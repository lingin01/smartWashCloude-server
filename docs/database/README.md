# 清洗云端基础表 V1

## 数据处理说明文档（统一在本目录）

| 文档 | 内容 |
|---|---|
| [PLC 点位分类与数据说明](CLEANING_PLC_POINT_CLASSIFICATION_20261015.md) | 2026-10-15：系统信息、设备信息、数据点、故障分类，第二版点位清单、来源与待确认项 |
| [原服务端 Modbus TCP 实施说明](CLEANING_MODBUS_TCP_IMPLEMENTATION_20261015.md) | 2026-10-15：配置驱动 PLC 采集、内部消息、急停／参数写入、线程锁、模拟测试与未完成业务 |
| [原服务端 MQTT 清理执行记录](CLEANING_MQTT_REMOVAL_EXECUTION_20261015.md) | 2026-10-15：原项目删除范围、保留项、PLC 未就绪边界及静态验证限制；副本未修改 |
| [盒子上传数据的云端处理流程](CLEANING_BOX_UPLINK_PROCESSING_FLOW.md) | 2026-10-14：七类上传的接入现状、MQTT队列、表示层、大厅、实时/历史/告警/回执处理及未实现项 |
| [历史数据存放流程](CLEANING_HISTORY_STORAGE_FLOW.md) | 盒子本地保存、0.5米留点、云端落库、保存确认与补传 |
| [历史存储实现对照](CLEANING_HISTORY_STORAGE_IMPLEMENTATION.md) | 表、字段、类、方法、MyBatis-Plus落点与验收清单 |

以上为源码核对及实施说明，不是现场联调通过证明。本目录继续保留基础表和验证脚本；旧协议/前端设计文档不搬动，通过链接引用。

## 基础表 V1 说明

2026-10-13 历史存储专项说明：[盒子本地保存→云端事务落库→保存确认](CLEANING_HISTORY_STORAGE_FLOW.md)、[表/字段/类/方法与待实现清单](CLEANING_HISTORY_STORAGE_IMPLEMENTATION.md)。仅新增文档，未启用任务、未执行SQL；当前历史可靠保存闭环仍未完成。

日期：2026-10-12。范围：SmartWashCloud-Server。状态：第一阶段表名、字段、关联和保存口径已整理为V1实施基线，未执行SQL、未修改运行代码或DAO。

用户最新技术约定：数据层主要使用MyBatis-Plus，接口和手写实现分目录；参见 [云端开发约定](../CLEANING_SERVER_DEVELOPMENT_CONVENTIONS.md)。本文提及Jdbc类仅描述现有可复用调用接口，不表示后续继续以JdbcTemplate为主要新增实现。

文件：[建表脚本](cleaning_schema_v1.sql)、[静态核对脚本](verify_schema_v1.ps1)。旧 cleaning_foundation.sql 与前端目录追加16表草案保留作历史参考；不要将几份脚本混合执行。V1延续现有六表名称和旧DAO所需列，不另建同义框架。

## 1. 第一阶段确定14张表

| 分类 | 表 | 一条记录代表什么 | 主键或业务去重键 |
|---|---|---|---|
| 已有基础 | cleaning_system | 一套清洗系统，关联罐体、盒子和圆环 | system_id；box_id唯一 |
| 已有基础 | cleaning_winch | 一台绞机，属于系统，位置1～4 | winch_id；system_id+slot_no唯一 |
| 已有基础 | cleaning_rope_sensor | 一根索及对应传感器，属于绞机，位置1/2 | rope_id；sensor_id唯一；winch_id+slot_no唯一 |
| 身份配置 | cleaning_box_access | 一台盒子的固定校验码配置及人工导出/报告版本 | box_id |
| 核心业务 | cleaning_settings_revision | 盒子确认已生效的一版三参数 | box_id+config_version |
| 核心业务 | cleaning_session | 一次连续自动作业，可跨日、多轮 | session_id |
| 核心业务 | cleaning_round | session内一次正常上下尝试及其结果 | session_id+round_no；round_id唯一 |
| 已有基础 | cleaning_telemetry_batch | 一个上传批次的事务登记及保存ACK状态 | box_id+batch_id |
| 已有基础 | cleaning_telemetry_segment | 一个历史真实留点或明确区间摘要 | box_id+batch_id+telemetry_sequence；新协议源身份另有唯一约束 |
| 已有基础 | cleaning_segment_measurement | 上述记录中的一路拉力、速度或预留指标 | 历史记录键+metric_type+channel_no |
| 恢复事实 | cleaning_recovery_event | 一次PLC自恢复过程，可能多次尝试 | box_id+event_id |
| 核心业务 | cleaning_alarm_event | 一个源端告警事件及其恢复/人工状态 | producer_id+event_id |
| 核心业务 | cleaning_alarm_handling_event | 一次用户处置及双端同步结果 | operation_id |
| 核心业务 | cleaning_command_record | 一次急停或配置申请、回执及审计 | box_id+request_id；request_id全局唯一兼容现有登记 |

原有用户表t_user继续复用，不新建清洗用户表。旧t_device/t_alarm不是新清洗身份与告警的权威表；本次不删除它们，也不自动搬迁旧数据。

完整绑定历史、独立转矩标定、维护操作、精确轮次统计量、周期聚合和持久化发送Outbox按后续功能实施。这里没有为今日/月/季度/半年/年分别建表，也不为每个传感器、每条曲线单独建表。

新增两表不是扩充无关功能：access表用于云端生成并人工预置同一校验码；recovery表用于保留用户明确区分的自恢复过程，不能塞入最终故障告警或伪装新轮次。

## 2. 主要关系

```text
cleaning_system（系统/罐体/盒子/圆环）
 ├─ cleaning_winch ×4
 │   └─ cleaning_rope_sensor ×2
 ├─ cleaning_box_access（当前凭据）
 ├─ cleaning_settings_revision（实际参数各版本）
 └─ cleaning_session
     └─ cleaning_round（多轮）

cleaning_telemetry_batch
 └─ cleaning_telemetry_segment（按session/round关联）
     └─ cleaning_segment_measurement（8路拉力、4路速度等）

cleaning_recovery_event ──原session/round──历史异常片段
cleaning_alarm_event ── cleaning_alarm_handling_event
cleaning_command_record ──盒子回执──实际settings版本
```

SQL位置唯一约束只保证不重复、值不越界，不能保证刚好四机八索。CloudCleaningService在创建/导出时必须复用 CleaningSystemTopologyBo.requireCurrentSiteShape()，以同一事务保存完整拓扑。

## 3. 身份生成与人工导入

云端生成systemId/boxId/winchId/ropeId/sensorId等统一ID，事务保存前三表；读取后导出带bindingVersion的配置，你手动导入盒子。本地PLC点位、倍率和Runtime绑定仍由盒子配置，不能用云端业务编号猜地址。

exported_at只证明云端导出过，不证明盒子安装成功。reported_binding_version只在盒子明确报告或经核对后记录，不能导出时自动填写。设备信息不走MQTT下发，旧设备初始化/同步方案不是本阶段依赖。

V1拓扑一旦有历史，禁止直接原地改变旧索/传感器ID及其归属，使旧历史含义漂移。改绑定时需新版本、保存旧作业identity_snapshot_json并核对人工导入，或后续增加正式绑定历史表；盒子切换应在业务允许的空闲阶段进行。

## 4. 关键字段与单位

| 表 | 核心字段及来源 |
|---|---|
| system/winch/rope_sensor | ID和slot来自云端档案；名称可空；圆环自重来自已确认档案，不等于安全值 |
| box_access | access_code_ciphertext、encryption_key_ref、credential_version、enabled及导出/报告时间 |
| settings_revision | start_time、interval_seconds、group_speed_m_per_min、config_version、applied_at，必须来自盒子确认 |
| session | started_at/finished_at、status、stop_reason、settings/binding版本、身份快照、revision；未知计数为空 |
| round | 起止、result、恢复/告警数、统计资格、data_complete及revision；开始时RUNNING，结束依据PLC完成事实 |
| batch | 首末序号、实际record_count、stream_id、payload_hash、ack_state，不能由首末差推断所有记录都存在 |
| segment | 时间、高度、方向、mode、record_kind、record_scope、round及实际版本/事件关联 |
| measurement | 稳定物理ID、metric_type、unit_code、valid、样本数以及真实min/max/average/latest/sum |
| recovery | 原轮次、起止、attempt_count、恢复结果和final_fault_confirmed；次数未知为空 |
| alarm | source、alarm_code、source_fault_code、notice、active、发生/恢复时间和两类独立revision |
| handling | operation_id、expected_revision、target_state、实际操作者、同步结果及已应用版本 |
| command | request_id、两端command_id、目标/操作者/业务payload指纹、状态/中文结果、有效期及回执时间 |

力字段沿现有kg命名约定，实际单位须声明KG/KGF；PLC原始N须由盒子正确换算，不由列名猜单位。速度m/min，高度m，业务时间UTC DATETIME(3)，页面/周期Asia/Shanghai。现有JDBC配置serverTimezone为Asia/Shanghai，DAO实施时须同步核对UTC连接、会话及Timestamp读写，不能仅靠DDL宣称时间已统一。

测量无效时valid=false/sample_count=0，数值为空，真实有效0保留0。统计sum只能来自真实累计，不能由四舍五入average倒推。取稀疏历史点的最大值也不能填成全过程真实最大值。

单索warning/stop/breaking字段仅存盒子已应用快照，不开放普通三参数表单修改。必须预警值<安全停机值<已确认物理失效边界；不写任何演示阈值。单索失效边界不是平均拉力阈值，未知时为空。

## 5. 固定校验码的保存

accessCode由云端为每盒生成后人工预置，数据库保存可读取密文，而不是公开盒子ID或业务VO字段；云端需要解密原码才能封装下发，只存哈希无法完成这个用途。加密密钥由受控运行配置提供，不与密文同表保存。

enabled默认false，没有材料不启用远程认证。凭据DAO与普通档案/查询DTO隔离，不能在日志、审计payload_json或前端响应中暴露。校验码生成、加密、导出和双端验证本次均未实现，也没有生成真实校验码。

此存储设计不改变线上消息仍是一个accessCode字段的简化方案，也不把固定码等同于消息签名。云端用户权限、目标、时效、幂等和现场参数/保护检查继续保留。

## 6. 0.5米历史与恢复如何入表

### 6.1 第一阶段单点口径

- 正常自动UP移动约0.5m后保存当前真实帧，不把相邻500ms帧差当累计基准。
- segment.record_kind=POINT，record_scope=NORMAL_UP，真实time及height的start=end。
- measurement每路有效sample_count=1，min=max=average=latest=该点实测值；无效不补0。
- 正常DOWN不保存普通测量曲线，但作业/轮次与阶段事实不丢。
- 异常/恢复片段保持实际DOWN/UP，用EXCEPTION/RECOVERY范围并关联recovery_event_id；事件留点不等满0.5m。
- 真正区间摘要使用AGGREGATE并带真实累计及算法，不能冒充POINT。第一阶段不重复建立独立history_point表。

盒子最新设计的points[]和records[]是传输对象，两者将来并存时必须有分别稳定的来源键和明确kind；V1先接单点路径，不把相同序号的两个对象硬写为同一条记录。双轨摘要实施时应核对联合唯一键和实体映射，不伪造来源序号。

stream_id为空的旧行只保留旧批次去重能力，不能保证跨重启/跨批次去重。新协议必须提供稳定stream_id+telemetry_sequence并检查同键内容；数据库唯一约束不能代替相同batchId的payload_hash冲突校验。现有INSERT IGNORE仅忽略重复批次的行为需要改造，不能把不同内容当成功。

### 6.2 恢复与最终结果

恢复中系统正常、工作运行；recovery_event保存RECOVERING和真实尝试次数，不生成额外清洗轮次。五次未恢复且PLC确认最终失败才写对应FAILED/final_fault_confirmed，盒子不自行猜次数，其他急停/独立故障不受此等待条件限制。

恢复成功可继续原轮次。是否整轮排除正常统计或只排除恢复片段仍需业务确认；表能保存资格和范围，但不在DDL中把该待定策略固化。下降异常保存起点以盒子最新设计为讨论依据，尚需确认的现场点位/反馈不能被表默认值代替。

statistics_eligible初始false，由最终round结果与完整性决定；不是每个有效测量点自带true。正常参考曲线只取合格轮次的NORMAL_UP，DOWN异常另查。

## 7. 告警和命令状态

告警active/recovered_at属于现场事实；handling_state属于人工处理。事实版本source_revision与处置版本handling_revision独立，人工“完成”不清PLC故障、不改active。

建议数据库处置语义UNHANDLED/PENDING/COMPLETED/CLOSED，经Converter映射盒子Raised/Pending/Resolved/Closed及旧云端ACTIVE/PENDING/COMPLETED；不能原样混存数字与字符串。operation_id幂等，expected_revision用于冲突，不按谁的时间戳晚就覆盖。

命令state复用CloudCommandStatusBo.State。requestId保持现有全局唯一约定，同ID不同目标/操作者/类型/内容拒绝；重试不延长expires_at。payload_json只存业务参数，不含认证码/登录令牌。SUCCEEDED需盒子实际反馈，PUBACK不是成功。

命令表是审计/幂等，不等同可靠发送Outbox；当前发送队列和登记仍内存。完整重启恢复后续需要同事务Outbox或等效持久化队列，不把本次建表误报为已实现。

## 8. DAO与业务类落点

| 表组 | 数据访问与业务归属 |
|---|---|
| 设备前三表 | 复用CleaningDeviceDao/JdbcCleaningDeviceDao、CleaningSystemTopologyBo、清洗服务 |
| box_access | 后续补独立凭据DAO，由安全/命令转换使用，不塞入普通设备VO |
| session/round/settings | 复用已有CleaningSessionDao/CleaningRoundDao及清洗服务，实际配置保存需补实现 |
| 历史三表 | 复用TelemetryHistoryDao/JdbcTelemetryHistoryDao和CloudStatisticsService，扩展新增字段 |
| recovery | 按清洗服务聚合恢复记录数据能力，不为每个恢复字段增加接口 |
| alarm/handling | 复用AlarmDao/CloudAlarmService，补持久化及双端协调 |
| command | 复用CloudCommandDao/CloudCommandService，接入当前内存登记的持久化 |

查询路径仍是网络→表示层DTO/Param→大厅→业务→DAO/缓冲→BO→表示层VO。平均/单位/坐标轴和周期范围由后端提供，表不保存格式化“吨”文本、前端颜色或每张页面的大JSON。

## 9. 创建、迁移与验证边界

- 本SQL是全新库基线。不得在已有六表或已执行16表草案的库中直接运行并认为完成迁移。
- 旧六表原列及类型静态核对保留；新增字段不表示现有Java实体和DAO已读写。
- 已有库需先只读核对SHOW CREATE TABLE、字符集/排序规则、已有数据及唯一键，再单独生成增量迁移。当前未做这一步，没有ALTER/DROP脚本。
- 新表统一utf8mb4_bin以明确ID比较；已有库不能未经核对混用外键列排序规则。旧历史暂不设置强制round外键，以允许事件/历史乱序到达；业务必须完成归属校验，不凭字段存在纳入正常统计。
- 所有FK不级联删除历史。应停用档案而非删除有历史的设备。
- 新协议批次、记录及测量在同一事务保存后才发保存ACK；ACK的可靠发送/重复回复任务仍待实现。ack_state=PENDING不是已经发出。
- MySQL从8.0.16起实施CHECK约束；更早版本会忽略部分CHECK，不能当作数据保护已生效。[MySQL官方CHECK说明](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)

verify_schema_v1.ps1已通过：14表、10外键；表名、列重复、索引列、外键字段类型及目标唯一键、约束名称、旧基础列保留核对通过。该脚本是结构静态检查，不是完整MySQL语法解析器或引擎执行证明。本次未连接数据库、未执行DDL、未编译应用，也没有修改原运行框架。
