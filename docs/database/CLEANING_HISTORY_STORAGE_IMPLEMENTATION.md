# 清洗历史存储：表、类、方法与实施清单

日期：2026-10-13。适用：SmartWashCloud-Server 与 iot_box_cleaning。本文为现状核对及后续实施依据，不是实现完成证明。

先阅读 [完整保存流程](CLEANING_HISTORY_STORAGE_FLOW.md)。表以 [cleaning_schema_v1.sql](cleaning_schema_v1.sql) 为准，主要数据访问遵循 [MyBatis-Plus 开发约定](../CLEANING_SERVER_DEVELOPMENT_CONVENTIONS.md)。

## 1. 复用表，不增加另一套历史结构

| 表 | 归属及保存内容 |
|---|---|
| cleaning_system / cleaning_winch / cleaning_rope_sensor | 云端档案提供统一 ID、四机八路关系，人工导入盒子；不是每次上传重新生成 |
| cleaning_settings_revision | 盒子确认已应用的配置版本，不把云端待下发申请当已生效参数 |
| cleaning_session | 连续自动作业及起止、结果、身份/配置快照 |
| cleaning_round | session 内第几轮、起止、结果、完整性、统计资格 |
| cleaning_telemetry_batch | 上传批次登记、源流身份、内容指纹、真实记录数量、保存确认状态 |
| cleaning_telemetry_segment | 每个 POINT 或明确 AGGREGATE；保留现有表名，不再加 history_point |
| cleaning_segment_measurement | 记录中的各路测量、单位、有效性和稳定物理 ID |
| cleaning_recovery_event | PLC 恢复过程、尝试次数、最终结果，与原轮次关联 |
| cleaning_alarm_event | 独立告警事实；恢复中不提前创建最终失败告警 |

无需为每根索、每台绞机、每天或每个月分别建表。现有基础表 V1 是新库建表基线，未在真实 MySQL 执行；已有库必须核对结构后另做迁移，不能直接运行并认为完成升级。

## 2. 第一阶段历史点字段映射

以下是目标字段/语义。新协议字段以共同对齐文档为准；当前 Java/C++ 类和 JSON 尚未完整接入，不复制本表就认为协议已上线。

| 业务字段 | 数据库存放 | 说明 |
|---|---|---|
| boxId、batchId | batch/segment 关联键 | 与 MQTT Topic 盒子身份一致 |
| streamId | batch.stream_id、segment.stream_id | 明确源序号空间，跨重启不得复用源身份导致冲突 |
| firstSequence、lastSequence | batch.first_sequence/last_sequence | 范围不是连续性证明，不能推算数量 |
| records 实际数量 | batch.record_count | 来自实际校验后的数组 |
| 批次内容稳定指纹 | batch.payload_hash | 云端按约定规范内容计算，排除传输时间等易变项 |
| sessionId、roundNumber | segment.session_id/round_no | 自动记录必须有真实归属 |
| telemetrySequence | segment.telemetry_sequence | 整理后历史记录序号，不是每轮复位的 PLC 序号 |
| 原始采样序号（若提供） | segment.source_sample_sequence | 区分来源采样与历史序号 |
| direction、mode | segment.direction/mode | 实际方向和自动/检修模式 |
| POINT / AGGREGATE | segment.record_kind | 单点与真正区间摘要不能混算 |
| NORMAL_UP / EXCEPTION / RECOVERY | segment.record_scope | 正常曲线与异常过程分开 |
| startTimeMs、endTimeMs | segment.started_at/finished_at | POINT 相等，AGGREGATE 为真实区间 |
| startHeightMeters、endHeightMeters | segment.start_height_m/end_height_m | POINT 相等，禁止用平均四机高度假装圆环高度 |
| 起止高度有效性 | segment.start_height_valid/end_height_valid | 无效值不能补零 |
| 配置/身份版本 | segment.settings_version/binding_version | 保留当时语义，不能事后用新绑定改旧记录 |
| 恢复关联、事件代码 | segment.recovery_event_id/event_code | 不把恢复片段当新轮次 |
| 每路拉力、速度 | measurement 各行 | 通过 metric_type 和 channel_no 区分 |
| ropeId、sensorId、winchId | measurement 对应 ID 列 | 校验统一档案及上传时身份版本 |
| unit、valid、sampleCount | measurement.unit_code/valid/sample_count | 单位由盒子声明，无效测量为空 |
| min/max/average/latest | measurement 对应数值列 | POINT 有效时四者相同且 sample_count=1 |
| 真实累计值、计算方法 | measurement.sum_value/aggregate_method | 仅确有累计时写入，不由四舍五入均值反推 |

力按约定明确 KG/KGF 等单位；速度 m/min，高度 m。转矩留空时不补零，更不能把 N·m 当吨或公斤。时间传输 Unix 毫秒，数据库时间及 JDBC 读写要核对 UTC；页面周期用 Asia/Shanghai。

单机两索平均是同帧空间平均，与一轮时间平均不同。后端可根据同帧两路有效拉力计算页面平均，缺一路时不能擅自称两索平均。是否额外存四路已上传单机平均，以协议实施为准，不假定当前 DAO 已支持。

## 3. 一个批次的行数

举例：一个批次包含 20 个真实历史点，每点八路拉力、四路速度，包含无效通道但明确标记。

| 表 | 新增行数 |
|---|---:|
| cleaning_telemetry_batch | 1 |
| cleaning_telemetry_segment | 20 |
| cleaning_segment_measurement | 20 × (8 + 4) = 240 |

若未来明确额外保存四路本机平均，则为 320 条测量行；可选转矩另算，缺数据不伪造行值。重复传输不应重复新增这些行。

这是数据库行数，不是 MQTT 字节数。实际 JSON 字节量取决于字段、ID 长度和数值精度，应在确定序列化后按 UTF-8 实测，不用行数估算网络大小。

## 4. 盒子已有类和方法

路径相对 iot_box_cleaning：

| 类/方法 | 责任 | 当前情况 |
|---|---|---|
| TelemetryReductionProcessor.process() | 从快照整理历史点 | 有实现；当前比较相邻快照高度，需改为普通留点累计基准，并补 UP/恢复规则及首点 |
| TelemetryService.reduceCycle() | 顺序消费会话/轮次/实时/阶段输入，输出历史记录 | 已有 |
| BufferCenter.appendRoundRecord() | 温缓冲接收待保存历史 | 已有，容量有限 |
| CleaningPersistenceWorker.persistCycle() | 保存原始帧与整理历史 | 已有 |
| InnerWallCleaningDao.saveTelemetryRecord() | 本地文件保存历史记录 | 已有入口，需核对重启和断电语义 |
| BufferCenter.takePersistenceRecords()/completePersistenceRecords() | 标记正在写入及保存结果 | 已有 |
| BufferCenter.takeUploadPending()/returnUnsent() | 仅取已保存记录，入队失败可退回 | 已有 |
| TelemetryService.uploadCycle() | 组批并委托命令服务 | 已有；进入消息队列后保持 inflight |
| CommandService.enqueueHistory() | 组装 JSON、生成上行消息 | 已有；当前历史 JSON 缺拉力、速度、轮次等 |
| LobbyService.acknowledgeTelemetryUpload() | 处理云端保存 ACK | 当前明确拒绝，尚未实现 |
| BufferCenter.confirmUploaded() | 释放确认范围内的待上传记录 | 方法存在，不能直接当精确批次 ACK 使用 |

主要源码：

- src/business_layer/telemetry/telemetry_service.cpp
- src/business_layer/telemetry/telemetry_service_lifecycle.cpp
- src/infra/buffer/cleaning_buffer.cpp
- src/data_layer/cleaning/inner_wall_cleaning_dao.cpp
- src/business_layer/command/command_service.cpp
- src/business_layer/lobby/lobby_service.cpp

## 5. 云端已有类和方法

路径相对 SmartWashCloud-Server 的 src/main/java/com/company/project：

| 类/方法 | 责任 | 当前情况 |
|---|---|---|
| network_access_layer/mqtt/MqttService | 外部 MQTT 客户端入口、消息队列 | 已有接收路线 |
| presentation/mqtt/CleaningMqttDispatcher.runDispatchCycle() | 队列消费并调用大厅 | 已有 HISTORY_TELEMETRY 分支 |
| JacksonCleaningMqttInboundConverter.toHistory() | 入站消息转换 | 已有，委托 CleaningMqttConverter.history() |
| CleaningMqttConverter.history() | Topic/盒子、批次和测量数组转换 | 已有；records 限 1～100；可选测量缺失返回空，不生成假数据 |
| CloudLobbyService.receiveHistoryBatch() | 大厅分发 | 已有 |
| CloudStatisticsService.acceptHistoryBatch() | 验证批次基本身份、写温缓冲 | 已有 |
| info/buffer/cleaning/CloudWarmBuffer | 历史及完成轮次缓冲聚合体 | 已有；历史队列容量 2048 个批次 |
| CloudWarmBuffer.takeHistoryBatch()/returnHistoryUncommitted() | 取出/失败回队 | 已有；回队容量竞争仍需处理 |
| info/thread/core/TelemetryHistoryPersistenceTask | 注册周期持久化任务 | 已有，复用线程中心；开发配置默认关闭 |
| CloudStatisticsServiceImpl.runRealtimePersistenceCycle() | BO→Entity、批量调用历史 DAO | 已有；名字含 realtime，但实际处理历史批次 |
| TelemetryHistoryDao.insertBatch()/findByRange() | 历史聚合保存及查询 | 接口已有 |
| JdbcTelemetryHistoryDao | 当前三表事务实现 | 仍是 JdbcTemplate，尚未迁移 MyBatis-Plus |
| CloudStatisticsServiceImpl.query()及聚合/重建方法 | 曲线和周期统计 | 当前抛出未接入异常 |

现有持久化每轮取最多 20 个批次，任务默认间隔 2000ms。它是后台处理参数，不是每次上传固定 20 条记录，也不是 PLC 采样参数。

配置文件 src/main/resources/application-dev.yml 当前 cleaning.history.persistence-enabled=false。启用前需先核对真实数据库结构、协议字段和 DAO，不在本次文档任务中直接开启。

## 6. 后续 MyBatis-Plus 落点

继续复用业务 DAO 与 service，不让网络直接调用 Mapper：

- 业务接口目标放 service/interfaces，实现在 service/impl。
- TelemetryHistoryDao 接口目标放 repository/cleaning/interfaces。
- 手写 DAO 实现放 repository/cleaning/impl，内部注入 Mapper。
- Mapper 放 repository/cleaning/mapper；标注 @Mapper，由框架创建代理，不手写 MapperImpl。
- Entity 放现有 domain/entity/cleaning；补 V1 字段映射。
- 复合键、批量和冲突查询使用参数化自定义 Mapper SQL；不要为了 BaseMapper 将业务主键改成自增。
- 使用 Spring 事务组织批次、记录、测量及待确认状态，提交后才组织 ACK。

上述是目标目录，当前接口并未整体搬迁，不能把文档当作改包完成。

现有 insertBatch() 返回 void，不能表达“新保存、相同重复、内容冲突及实际保存集合”。实现保存 ACK 时需在既有 DAO 聚合职责内补充可复用结果对象/查询能力，不为每个字段建一个接口。

## 7. 实施顺序与验收

| 顺序 | 实施内容 | 必须验证 |
|---|---|---|
| 1 | 对齐身份、单位、POINT 字段及版本 | 恰好四机八路，ID 归属正确；未知版本/单位不得默认正常 |
| 2 | 盒子留点规则和历史 JSON | 低速每帧移动不足 0.5m 仍累计留点；缺数据不补零；异常 DOWN 保留 |
| 3 | MyBatis-Plus Entity/Mapper/DAO及事务 | 任一测量失败全批回滚；不留半批数据 |
| 4 | 幂等、源记录冲突与保存 ACK | 同内容重传不重复；同 ID 不同内容拒绝；提交前不 ACK |
| 5 | 盒子确认状态及超时补传 | ACK 乱序/丢失/重复不误删；有序号空洞也不按最大值清空 |
| 6 | 本地恢复与云端重启 | 重启继续原未确认批次；云端未落库温数据丢失可由盒子补传 |
| 7 | 作业/轮次/恢复事实及完整性 | 恢复属于原轮次；终态乱序不回退；不完整轮次不假装可统计 |
| 8 | 历史查询，再做曲线和周期统计 | 只查询真实点；留点统计与全过程统计明确区分 |

还需验证：缓冲满与失败回队竞争、磁盘满、长期断网、配置/绑定切换、真实有效零值、跨日作业、部分通道无效，以及同源记录跨批次去重。

session/round 事件可能晚于历史到达：不要自行创建“已完成轮次”替代缺失事实；V1 暂不强制 segment→round 外键，但业务必须补关联并记录未完整状态。

## 8. 本次验证记录

本次只读核对了上述 C++/Java 源码、开发配置和 V1 建表草案，新增说明文档及入口链接。

未修改运行代码；未执行 SQL、未连接 MySQL、未启用持久化任务、未编译盒子或 Java、未做真实 PLC/MQTT 联调。当前链路骨架存在，但不能据此宣布历史可靠保存闭环完成。

