# 清洗云端类与调用链（第一阶段）

## 已接通的上行链路

盒子当前主题以 `iot_box_cleaning/include/infra/config/topic_config.json` 为准。已接通 `cleaning/box/+/telemetry/realtime`、`cleaning/box/+/alarm/event`、`cleaning/box/+/command/reply`：

```text
network_access_layer/mqtt/MqttClientManager
    → info/buffer/mqtt/MqttIncomingBuffer
    → info/thread/mqtt/MqttDispatchWorker
    → presentation/mqtt/CleaningMqttPresentation
    → CloudLobbyService → CloudStatisticsService / CloudAlarmService
    → info/buffer/cleaning/CloudRuntimeDataCenter
```

目录按职责拆分：网络接入层只负责 MQTT 连接和收发；表示层只负责协议转换和调用大厅；缓冲队列、业务热/温数据中心及消费线程统一放在 `info` 下的对应子目录。原 `DeviceMessageHandler` 转发类已移除。

MQTT 回调只入队；表示层专属线程解析后调用大厅。实时遥测按盒子实际报文保留 `boxId`、`sessionId`、`sequence`、`sampleTimeMs`、`speedLevel`、独立 `ropes`/`winches` 列表和整组平均值。每根索仍区分 `ropeId`、`sensorId`、`winchId`。拉力单位由盒子换算为 KG，云端不重新决定单索安全停机。当前盒子实时上行报文还没有转矩字段，云端不伪造。

`CloudRuntimeDataCenter` 保存各盒最新热数据和每盒最多 10000 个实时采样的温数据；它不是 3～4 天持久历史，也无法在重启后恢复。后续要接 DAO、历史批次 inbox、去重表和故障恢复。

## 业务类与历史

| 类 | 主要方法 | 当前状态 |
| --- | --- | --- |
| `CloudLobbyService` | `receiveTelemetry`、`receiveAlarm`、`receiveCommandAck` | 已连通 |
| `CloudAlarmService` | `receiveFromBox`、`markPending`、`complete` | 内存版本；未落库/回传盒子 |
| `CloudStatisticsService` | `acceptSample`、`closeRun`、`rebuildDay/Month/Year` | 接受采样已连通，汇总待 DAO |
| `CloudSafetyService` | `verifyEmergencyStop`、`verifySettingsChange` | 接口预留 |
| `CloudHistoryService` | `listRuns`、`getRun`、`getRunCurve` | 类型化接口预留 |

`CleaningRun.mode` 应区分 `AUTOMATIC` 和 `MAINTENANCE`。现场设置参数后持续自动清洗，应以一次连续作业为主记录，每趟上下循环为子记录；单台卷扬机手动运行属于检修，不计入自动清洗轮次。历史曲线暂只有 `CurveQuery`/`CurveData` 契约；横纵轴、后端抽稀和自动吨/公斤单位由后端实现，前端只展示。

## 尚不能宣称完成的下行

计划链路是 `HTTP Controller → CloudLobbyService → CloudSafetyService → InnerWallCleaningService → CommandService → MQTT → 盒子业务 ACK`。仓库现有 `SecurityConfig` 对所有请求放行，`CommandServiceImpl.sendCommand` 只生成 ID，尚未真正发布 MQTT。因此本次**没有开放远程急停和参数设置 HTTP 接口**，也没有把假命令结果返回为成功。必须先恢复鉴权及控制权校验，再实现命令 outbox、发布、超时/重试及业务 ACK 关联。MQTT Broker QoS 1 ACK 不表示盒子已执行。

盒子另外还发布 `telemetry/history`、`alarm/handling/up`、`state/event`、`session/summary`。后续应逐一核对真实报文并接 DAO、幂等与告警双向同步，不能只收到 MQTT 消息就标记业务完成。

## 旧类和可靠性边界

### 按《云端服务器清洗后端与统计体系分析》补齐的类

- `domain/bo/statistics`：独立定义 `CleaningRoundSummaryBo`、`RopeRoundStatisticBo`、`WinchRoundStatisticBo`、`TelemetryBatchBo`/`TelemetrySegmentBo`、`MetricAccumulatorBo` 和统一统计查询/结果。聚合平均值按 `sum/count` 计算，不对平均值再平均。
- `domain/bo/CleaningSystemTopologyBo`：稳定保存罐体、清洗系统、盒子、圆环、卷扬机、索和传感器身份；转矩是卷扬机维度。当前实时 MQTT 报文没有转矩时保留空值，不在云端补算。
- `domain/entity/cleaning`：分别建立会话、轮次、历史分段、聚合桶、检修操作及安全规则版本实体。它们是数据库模型契约，尚未创建表或 Mapper 实现。
- `repository/cleaning`：按会话、轮次、历史、统计、检修、告警、命令、安全策略及设备档案建立窄 DAO；未接真实数据库前不能宣称已持久化。
- `info/buffer/cleaning`：热快照读写窄接口和历史/完成轮次温缓冲接口；内存缓冲不替代持久化 inbox。
- `info/thread/core`：`BackgroundWorkerRegistrar` 与 `ThreadCenter` 统一管理周期任务；MQTT 消费任务通过线程中心注册，每次最多处理 100 条，不再自己 `new Thread`。
- `StatisticsPeriodResolver`：按上海业务时区生成日、自然月、季度、半年、年的左闭右开范围，以及裁剪到自然月内的周桶。
- `TensionDisplayUnitResolver` 与 `StatisticsComparisonCalculator`：后端统一处理 AUTO/KG/TON 展示换算和环比/同比算术；安全阈值和数据库原值仍使用 KG。
- `CleaningMqttConverter`：集中完成盒子实时、告警、回执及历史批次 JSON 到 BO 的转换；历史批次转换已定义，但在持久化 inbox/DAO 接通前未订阅历史主题。

`CloudStatisticsService` 已增加接收历史批次、完成轮次、统一统计查询和四类业务周期方法。当前实时热数据接收可用，历史批次/轮次可进入内存温缓冲；查询、持久化、聚合和重算明确报未实现，不返回模拟结果，也没有注册会重复失败的统计周期。

后续必须补表、Mapper、事务性批次幂等、告警版本同步与 outbox，才能开放真实历史/统计查询和远程命令。旧页面 `buildMockData()` 仍不属于新链路。

旧 `CleaningController` 的首页、设备、槽体、计划和历史接口仍调用 `buildMockData()`，相关旧 VO 被 Controller 引用，不是死类。本次保留，以免页面立即失效；前端迁移后再逐个移除模拟数据和无引用类，不得将其响应视作真实盒子数据。

当前入站队列仅是内存有界队列。队列满会报错，但没有持久化 inbox、死信和重放能力。重要告警、历史批次与命令回执必须补齐可靠消费；告警处置应以 `alarmId + revision/operationId` 做双向版本同步。遥测原始载荷不写 INFO 日志。
