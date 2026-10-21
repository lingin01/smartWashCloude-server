# 云端清洗系统地基、盒子上报字段与报文大小

## 1. 结论先行

本次目标项目是 `SmartWashCloud-Server`。当前现场的业务拓扑固定为：

```text
一套清洗系统 → 一台盒子 → 四台动力设备/绞机
                           └→ 每台绞机两根牵引索、两个拉力传感器
总计：四台绞机、八根索、八个拉力传感器。
```

以后增加罐体时，新增清洗系统及其盒子档案，不把多个罐体的数据混在同一个 `boxId` 下。圆环自重约 3 吨属于设备档案，不是单索的实时安全阈值，也不能把八根索的平均值当作单索安全判断。

云端目前已建立 MQTT 接收、热缓冲、历史温缓冲、历史批次持久化接口和数据库表结构。本次把历史主题接到了 `MQTT → 表示层 → 大厅 → 统计服务 → 温缓冲`，并新增一个可选后台落库任务。**但当前盒子自动上传任务尚未实现，盒子历史报文也没有写入逐索拉力统计；因此不能说“历史拉力已能正常落库”。**

## 2. 本次落下的基础结构

### 2.1 设备档案

`CleaningSystemTopologyBo` 表达 `tankId / cleaningSystemId / boxId / ringId / winches / ropes / sensors`，新增当前现场形状校验：必须四台绞机，每台两根索，八个传感器编号唯一。

`CleaningDeviceDao` 和 `JdbcCleaningDeviceDao` 提供按盒子、按系统查询和首次登记。`docs/cleaning_foundation.sql` 建立系统、绞机、索/传感器映射表，数据库对 `box_id` 加唯一约束。首次登记使用事务；还没有对外提供“修改拓扑”的 HTTP 页面或接口，避免运行中无版本控制地改动测点身份。

### 2.2 云端遥测与冷数据

```text
盒子 MQTT 实时帧
 → PahoMqttTransport → MqttService → MQTT 入站缓冲
 → CleaningMqttDispatcher → Converter → CloudLobbyService
 → CloudStatisticsService.acceptSample() → 最新热快照

盒子 MQTT 历史批次
 → 同一 MQTT 接入、表示层和大厅
 → CloudStatisticsService.acceptHistoryBatch() → 历史温缓冲
 → TelemetryHistoryPersistenceTask → JdbcTelemetryHistoryDao
 → cleaning_telemetry_batch / segment / segment_measurement
```

历史落库任务默认关闭。需要先对实际使用的数据库执行 `docs/cleaning_foundation.sql`，完成盒子协议联调后再把 `cleaning.history.persistence-enabled` 改为 `true`。脚本没有自动执行，也没有改变旧 `init.sql`。

部署顺序必须是：建表并登记设备映射 → 启用云端落库任务 → 验证一批历史数据完整落库 → 最后启用盒子历史上传。当前 MQTT 表示层会把批次先放进内存温缓冲；若在落库任务关闭时提前启用盒子上传，缓冲满或云端重启都可能丢失批次。

持久化按 `boxId + batchId` 幂等，批次、分段、逐测点统计同事务提交。单个历史分段预留八路拉力、四台速度、四台转矩力的 `minimum / maximum / average / latest / sampleCount`。缺失的数组保持为空，不会补造拉力值。

### 2.3 旧文件归档

经源码引用检查，五个只服务旧 MQTT 链路、没有被新主流程引用的文件已经移到 `archive/legacy-mqtt-2026-09-22/`，保留原目录层级：

- `MqttClientManager.java`；
- `MqttIncomingBuffer.java`；
- `MqttIncomingMessage.java`；
- `MqttDispatchWorker.java`；
- `CleaningMqttPresentation.java`。

这只是归档，不是删除，原内容可恢复。`LobbyServiceImpl`、`InnerWallCleaningServiceImpl`、`CommandServiceImpl` 等旧业务类仍可能被 Controller 或其他类引用，未做整批移动。它们的替换需要先迁移调用链。

## 3. 盒子当前定义的上报消息

以下是依据盒子项目 `src/business_layer/command/command_service.cpp` 的 JSON 序列化函数和 `include/infra/config/topic_config.json` 核对的，不是凭页面字段推测。所有数值是盒子输出值，前端不承担计算。

| 类型 / 主题后缀 | 当前 JSON 内容 | 当前实际情况 |
|---|---|---|
| 实时 `/telemetry/realtime` | `boxId`、`sessionId`、序号、采样时间、状态、速度挡位；八个 `ropeId/sensorId/winchId/tension/valid/level`；四个 `winchId/heightMeters/speed/tensionAverage/online/running/faultCode`；整体平均拉力、平均速度、平均高度、高度差、告警标志 | 序列化函数存在，但自动上传任务没有实现/装配；盒子当前快照未填 `boxId`、`sensorId`、索所属 `winchId` 等关键身份 |
| 历史 `/telemetry/history` | `batchId`、`boxId`、首末序号；每段的 `sessionId`、序号、状态、方向、起止高度、起止时间、触发原因 | 内部 `CleaningTelemetryRecord` 有八路拉力及四台绞机统计数组，当前 JSON **没有**这些数组，也没写 `roundNumber`；自动组批任务未实现 |
| 告警 `/alarm/event` | 告警 ID、会话、等级、告警码、中文提示、影响设备/索、当前值、阈值、单位、产生/恢复时间 | 序列化函数存在；需确认告警服务何时实际调用 |
| 状态 `/state/event` | 会话、前后状态、时间、原因 | 序列化函数存在；需确认状态机调用 |
| 处置同步 `/alarm/handling/up` | 告警 ID、状态、修订号、操作 ID、处理人、来源、备注、更新时间 | 序列化函数存在；需确认触发与 ACK |
| 清洗汇总 `/session/summary` | 会话起止、实际行程、速度挡位、原始样本数、上报段数、告警数、结果 | 序列化函数存在；需确认会话结束时调用 |
| 命令回执 `/command/reply` | 请求 ID、命令 ID、受理/执行阶段、业务码、中文提示、时间等 | 序列化函数存在；与 Broker QoS ACK 不同 |

上述“函数存在”不等于周期任务已经运行。盒子 `TelemetryUploadWorker` 目前只有头文件声明，未发现 `.cpp` 实现及生产装配；云端也不能假设每秒一定能收到一包。配置中的 `realtimeIntervalMs=1000` 和 `maxBatchRecords=100` 只是策略默认值，不是现场实测发送频率。

### 必须先解决的协议差异

1. 盒子 `CleaningService::makeRealtimeSnapshot()` 没有填充 `snapshot.boxId`；MQTT 主题使用客户端 ID 替换 `{box_id}`。云端校验主题盒子 ID 与载荷 `boxId` 一致，空值会被判为非法消息。
2. 八路实时拉力虽然有 `ropeId`，但 `sensorId` 和所属 `winchId` 当前为空；不能把数组顺序悄悄当作永久设备身份。盒子应从设备映射配置填充。
3. 历史记录内部统计数组没有写入历史 JSON；云端只会得到高度、时间和状态，不可能还原传感器历史拉力曲线。
4. 历史 JSON 没有 `roundNumber`，云端暂只能记 `0`，不能准确归入第几次自动清洗轮次。
5. 盒子快照有 `torqueKg`，实时 JSON 暂未输出；云端当前 DTO 读的是 `torqueNewtonMeters`。转矩力和转矩单位不能混用，必须先确认 PLC 物理量和统一协议。
6. 盒子当前降采样器的 `minimum/maximum/average` 由最近一个快照填入，`sampleCount=1`，尚未真正聚合稳定区间；不能据此计算可靠的区间统计。

## 4. 每一次上传大概有多大

### 4.1 测量口径

下表是按**当前盒子 JSON 字段集合**构造典型样本，使用紧凑 UTF-8 JSON 计算的示例大小。假定会话 ID 为 `session-20260922-001`、序号六位、毫秒时间戳十三位、八路拉力与四台速度均有数值。字符串长度、浮点小数位数、中文提示和批量记录数变化时，大小也会变化。

| 报文 | 典型 MQTT 载荷大小 | 说明 |
|---|---:|---|
| 实时快照，按当前未填身份字段的模板 | 约 1,464 B | 这是当前字段形状的示例；因 `boxId` 为空，在云端会被拒绝 |
| 实时快照，补齐盒子/传感器/绞机 ID 后 | 约 1,606 B | 仍未包含转矩力等未来字段 |
| 历史批次，1 段 | 约 355 B | 当前历史段没有拉力数组 |
| 历史批次，10 段 | 约 2,380 B | 当前协议 |
| 历史批次，50 段 | 约 11,380 B | 当前协议 |
| 历史批次，100 段 | 约 22,630 B | 当前策略允许的记录数量上限 |
| 告警事件 | 约 553 B | 含示例中文提示；真实处理步骤越多越大 |
| 状态变化事件 | 约 134 B | 原因文字可变 |
| 告警处置同步 | 约 200 B | 备注文字可变 |
| 清洗会话汇总 | 约 238 B | ID 长度可变 |

以上只计 JSON 载荷。MQTT QoS 1 PUBLISH 还要加固定报头、主题长度、主题字节和报文标识；以 `cleaning/box/box-001/telemetry/realtime` 为例，约再加 46 B。TCP/IP、TLS、Broker 内部开销、重发和订阅端流量未计入。建议现场统计**真实序列化后的 UTF-8 字节数**，不要用 C++ 结构体 `sizeof` 估算网络流量。

云端 `MqttService` 现已在 Debug 级别记录 `payloadBytes`，现场可用它核对真实单包大小；正常日志级别不逐包输出。

如果盒子以后把每段八路拉力、四台速度、四台转矩力的五项统计全部放进历史 JSON，同样的示例数据大约变为：10 段 19.8 KB、20 段 39.5 KB、100 段 196.7 KB。这是**未来协议容量估算，不是当前实际发送量**。因此不能只按 `maxBatchRecords=100` 限制，后续还应在盒子端增加 `maxBatchPayloadBytes`（建议初始按约 32 KiB 设计，再依据 Broker 配置和现场网络确定），达到字节上限就提前分批。

若未来真的每秒发送一份约 1,464 B 的实时载荷，理论值约 1.46 KB/s、126.5 MB/天（只计载荷）。但当前自动上传任务未接通，此数值仅用于容量规划，不能当成现有运行流量。

## 5. 数据保存边界与剩余工作

1. **热数据**：每个盒子保留最新实时快照，供云端页面当前状态使用；这是展示数据，不能当作完整历史。
2. **温数据**：历史批次进入有界队列，等待落库；当前内存队列在进程重启时可能丢失。要做可靠交付，还需持久化 Inbox 或在数据库提交后才给盒子业务确认。
3. **冷数据**：本次建表脚本和 JDBC DAO 可保存盒子已经发送的分段及未来逐索统计；尚未执行脚本，落库任务默认关闭。实时帧没有被作为权威历史逐点写入数据库。
4. **身份映射**：云端可以登记一系统/一盒子/四绞机/八传感器，但需要实际设备编号和映射数据；目前没有自动种子数据，也没有改动盒子采集配置。
5. **统计**：日、月、季度、半年、年及同比/环比计算仍未实现，现有统计服务查询方法仍抛 `UnsupportedOperationException`。
6. **安全**：单根索的预警和安全停机由盒子判定；云端保存盒子上报结果，不根据平均拉力推断某根索是否安全。

下一步应先让盒子真实补齐 `boxId/sensorId/winchId`、实现 `TelemetryUploadWorker`、把八路拉力统计和 `roundNumber` 写入历史报文；随后执行云端建表脚本、登记拓扑、开启持久化任务，再做实际 MQTT 联调与字节数采样。最后才进入轮次聚合和页面曲线统计。
