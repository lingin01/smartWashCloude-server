# 盒子上传数据在云端的处理流程

日期：2026-10-14。项目：SmartWashCloud-Server；对端：iot_box_cleaning。

本文依据当前 Java 源码、MQTT 开发配置及既有双端协议设计整理。明确区分当前实现与目标流程；本次只修改文档，不修改代码、配置、数据库，也不进行编译和现场联调。

本系列统一放在 docs/database：

- [本目录说明及基础表](README.md)
- [历史数据存放流程](CLEANING_HISTORY_STORAGE_FLOW.md)
- [历史表、字段、类和方法实施清单](CLEANING_HISTORY_STORAGE_IMPLEMENTATION.md)
- 本文：包括实时、历史、告警和命令回执，以及尚未接入的三类上传消息。

共同协议参见 [盒子对齐要求入口](../CLEANING_BOX_ALIGNMENT_REQUIREMENTS.md)，数据访问参见 [MyBatis-Plus 开发约定](../CLEANING_SERVER_DEVELOPMENT_CONVENTIONS.md)。不将建议的 3.0 协议当成已上线协议。

## 1. 先说明整体处理原则

盒子上传测量和现场事实，云端按用途处理，不能把所有上传数据放入同一个列表，也不能所有消息一收到就直接写数据库。

```text
盒子命令服务生成上传消息
  → MQTT Broker
  → 云端内部 MQTT 客户端封装 PahoMqttTransport
  → 网络接入 MqttService：Topic 匹配、信封封装、入接收缓冲
  → 表示层 CleaningMqttDispatcher：消费、转换
  → CloudLobbyService：分发业务对象
       ├─ 实时 → 统计服务 → 当前热快照 / 有限实时温数据
       ├─ 历史 → 统计服务 → 历史温缓冲 → 后台任务 → DAO → MySQL
       ├─ 告警 → 告警服务 → 告警状态（当前内存，持久化待接入）
       └─ 命令回执 → 命令服务 → 原请求状态（当前内存，持久化待接入）
```

未来状态事件、作业汇总经表示层和大厅进入清洗服务，告警处置同步进入告警服务；不能为了接收方便绕过大厅直接访问数据库。

用户 HTTP 网络与现场 MQTT 网络是两类入口，复用大厅和业务服务，而不是互相调用对方 Controller。

## 2. 盒子的七类上传，云端目前接入了哪些

Topic 通用前缀是 cleaning/box/{boxId}/。订阅使用 + 匹配各盒子；发布、订阅定义应继续由配置和 Topic 注册表管理，不散落在业务代码。

| 上传类型 | Topic 后缀 | 当前云端 | 业务处理目标 |
|---|---|---|---|
| 系统实时快照 | telemetry/realtime | 已配置订阅、转换、大厅分发 | 最新快照及有限近期样本，不逐帧自动落历史库 |
| 清洗历史批次 | telemetry/history | 已配置订阅、转换、历史温缓冲及 DAO 路线 | 保存真实历史点、测量，再确认保存 |
| 告警发生/恢复 | alarm/event | 已配置订阅、转换、告警内存更新 | 保存现场告警事实与用户说明 |
| 急停/配置回执 | command/reply | 已配置订阅、转换、请求状态更新 | 区分受理、执行中、成功、失败、未确认 |
| 告警处置同步 | alarm/handling/up | 枚举有 ALARM_HANDLING，但未配置该订阅、未实现分发 | 独立处理人工处置版本和双端冲突 |
| 清洗状态事件 | state/event | 枚举有 STATE_EVENT，但未配置该订阅、未实现分发 | 更新作业、轮次、阶段和 PLC 恢复事实 |
| 自动作业汇总 | session/summary | 枚举有 SESSION_SUMMARY，但未配置该订阅、未实现分发 | 核对作业终态、轮数、完整性及实际汇总 |

当前核对的是 application-dev.yml，其他运行环境需要检查实际启用配置。枚举存在不等于订阅、转换和业务处理均已完成。

仅增加这三条订阅也不能完成接入：Dispatcher 当前只有四个业务分支，其他类型会被判为尚未实现。

## 3. 公共接收路线：网络到表示层

### 3.1 网络接入层

主要类：

| 类/方法 | 实际职责 |
|---|---|
| IMqttTransport / PahoMqttTransport | 封装外部 MQTT 库，与网络服务之间传递原始消息 |
| MqttService.onMessage(MqttIncomingMessageDto) | 接收 Topic、原始 payload，匹配业务类型并投递队列 |
| CleaningMqttTopicRegistry.matchIncomingTopic() | 从配置匹配 Topic 及业务类型 |
| MqttInboundEnvelopeFactory.create() | 封装信封，携带 Topic 变量、消息身份、接收时间等 |
| MqttMessageBufferCenter.offerIncoming() | 根据类型放入相应接收区域 |
| MqttService.onConnectionChanged()/subscribeConfiguredTopics() | 连接成功及重连后恢复配置订阅 |

网络回调不执行统计、不生成页面坐标、不等待数据库保存。Topic 中盒子编号只是路由身份；Topic 与 payload 相符不等于来源认证完成，不能据此宣布 accessCode 或安全校验已实现。

### 3.2 三种接收区域

MqttMessageBufferCenter 是一个聚合体，分别暴露收、发等接口，队列和线程仍放在 info 下，不分散到各业务类中。

| 区域 | 当前方式 | 适用消息 |
|---|---|---|
| realtime | 按盒子保存一个待消费信封，同盒新接收消息覆盖旧信封 | 实时数据，可合并 |
| replies | 容量 512 的内存队列 | 命令回执，避免被历史洪峰占满 |
| reliable | 容量 4096 的内存队列 | 当前历史、告警等不可简单覆盖的消息 |

这里的 reliable 只是代码命名，不表示可靠持久化队列。实时 Map 未设置全局盒子数量上限，仍需配合已登记盒子校验及容量策略。

取消息时优先给回执和实时消费机会，再取普通队列。接收时间用于入站合并，不替代采样时间；后续热快照还有源序号判断。

### 3.3 表示层消费与转换

CleaningMqttDispatcher.start() 通过 BackgroundWorkerRegistrar 注册任务，当前每 50ms 调用 runDispatchCycle()，每轮最多取 100 条入站消息。该周期是云端消费频率，不是盒子采样频率或前端刷新频率。

转换路线：

```text
MqttIncomingMessageDto
  → MqttInboundEnvelope
  → JacksonCleaningMqttInboundConverter：UTF-8 JSON 解析
  → CleaningMqttConverter：字段读取、类型转换、协议校验
  → 内部业务对象
  → CloudLobbyService
```

主分发流程只选择业务分支，不零散读取每个 JSON 字段。

目标分层是外部 DTO → 表示层转换 → Param/BO → 大厅。当前四条上传转换直接返回 TelemetrySample、TelemetryBatchBo、CloudAlarm、CommandAck；专用业务上传 DTO/Param 并未全部建立。不能把命令传输事件已经使用 CommandDeliveryParam，误写成所有入站类型都完成了 DTO/Param 分层。

## 4. 实时数据：用于当前展示

### 4.1 当前调用链

```text
CleaningMqttConverter.realtime()
  → CloudLobbyService.receiveTelemetry(TelemetrySample)
  → CloudStatisticsService.acceptSample()
  → RealtimeSnapshotSink.updateLatest()
  → CloudRuntimeDataCenter.acceptTelemetry()
```

CloudRuntimeDataCenter 保存每盒最新值，并保留最多 10000 个近期实时样本。它与 CloudWarmBuffer 的“等待落库历史批次”是两种温数据，不能混为同一队列。

同一 session 中，重复或较小 sequence 不覆盖最新值；跨 session 按 sampleTime 判断。它不是所有历史记录的排序算法，也不能确保重启后序号空间天然可靠。

### 4.2 当前已读取与缺口

当前读取八路索对象、四台绞机对象、整体平均拉力/速度、平均高度、高度差、预警标志等。但仍需对齐：

- 当前转换要求非空 sessionId，无自动作业时的实时快照会受到限制。
- 当前读取根字段 state，盒子 2.0 实时协议使用 status 对象，两者尚未完整统一。
- 部分缺字段通过 asInt/asBoolean 默认成 0/false，不能作为正式“正常/有效”状态依据。
- schemaVersion、恰好四机八路、统一 ID 归属和有效性校验仍需补齐。
- 圆环高度不能拿四机 groupAverageHeight 替代；同步速度也不能拿平均单机速度替代。
- 单位、工作阶段、当前轮次、实际配置/绑定版本等按共同协议落实，不由云端猜值。

目前实时接收不自动调用历史 DAO，不保证每 500ms 样本进入数据库。

### 4.3 前端取得实时数据

已有查询路线：

CleaningWebService → CloudCleaningWebPresentation/Converter → 大厅 queryRealtime() → 安全读权限校验 → 清洗服务 latest() → RealtimeSnapshotQuery.latestSnapshot() → BO 转页面 VO。

前端不直接读取内存中心，不计算拉力平均、单位换算或图表坐标。完整页面 VO 和持续推送能力需要分别核对，本文不将盒子本地 WebSocket 方案当成云端已经实现。

## 5. 历史数据：温缓冲到数据库

### 5.1 当前路线

```text
CleaningMqttConverter.history()
  → 大厅 receiveHistoryBatch()
  → 统计服务 acceptHistoryBatch()
  → CloudWarmBuffer.offerHistory()
  → TelemetryHistoryPersistenceTask
  → 统计服务 runRealtimePersistenceCycle()
  → TelemetryHistoryDao.insertBatch()
```

当前 history() 检查 Topic/payload 盒子一致、batchId、records 为 1～100 条、序号范围及批内重复；可选拉力/速度数组存在时检查数量，缺失不生成假测量。

历史温缓冲容量 2048 个批次；持久化一轮最多取 20 个批次。开发配置任务默认关闭，默认调度间隔 2000ms。runRealtimePersistenceCycle() 名字虽然有 realtime，实际保存历史批次。

### 5.2 保存及确认

目标使用 MyBatis-Plus 保持既有 DAO 接口及业务链：批次、记录、测量同事务保存；事务提交后才发保存 ACK。

当前 JdbcTelemetryHistoryDao 仍是 JdbcTemplate，已有三表事务路线，但没有接入 V1 全部字段、稳定源身份冲突和内容指纹检查。

当前盒子历史 JSON 还缺拉力、速度和轮次等内容，云端不会凭空补齐，因此不能据此画真实拉力/速度曲线。

详细存储、0.5 米留点、正常 UP、异常 DOWN、跨批次去重和 ACK 见 [历史保存流程](CLEANING_HISTORY_STORAGE_FLOW.md)。保存 ACK、盒子确认释放、超时重传与重启恢复仍待实现。

## 6. 告警数据：发生、恢复与人工处置分开

### 6.1 当前路线

CleaningMqttConverter.alarm() → 大厅 receiveAlarm() → CloudAlarmService.receiveFromBox() → CloudRuntimeDataCenter.putAlarm()。

当前保存内存，不写 cleaning_alarm_event。转换保留 alarmId、alarmCode、严重度、affectedRopeId、notice.summaryZh、发生时间和 active 等；source 固定 BOX，人工状态初始化 ACTIVE，version 固定 0。

### 6.2 当前处理限制与目标

当前 putAlarm() 有版本/active 更新逻辑，但转换不能保留完整源版本，内存还仅以 eventId 作键。不能将它描述为跨盒子、乱序恢复和双端同步已完善。

后续需要：

- 保留统一告警代码与 PLC 原始故障码，单位、当前值、阈值、受影响设备/索、恢复时间和中文处置建议。
- 告警现场事实版本与人工处置版本分开，不用一个 version 同时表示两者。
- active 表示现场条件是否存在；人工完成不清除 PLC 故障，也不把 active 改成 false。
- 告警处置上报用 operationId 去重、expectedRevision 检查冲突，经大厅交告警服务。
- 以来源身份和事件 ID 持久化去重，避免不同盒子同编号冲突。
- 通过 AlarmDao 保存告警及处置，不直接在网络层写表。

当前人工待处理/完成方法只改内存；operator 尚未形成持久化审计和盒子同步。已恢复的事件也不能用固定 ACTIVE 人工状态代表完整处置语义。

普通拉力预警只提示，阈值属于单根索，不拿两索或八索平均替代保护指标。PLC 恢复中不提前当最终故障；五次未恢复且 PLC 最终确认失败的事实要由盒子上传，云端不能从一条拉力曲线自行推断。

## 7. 命令回执：关联原急停/参数申请

### 7.1 当前路线

CleaningMqttConverter.commandAck() → 大厅 receiveCommandAck() → CloudCommandService.receiveBusinessReply() → CloudCommandRegistry.applyReply()。

转换兼容数字/字符串阶段，校验 requestId、回执时间、非拒绝阶段的盒子 commandId；有 payload boxId 时与 Topic 对比。

登记中心校验原 requestId 和盒子、盒子命令编号，处理旧时间回执及终态不回退。当前状态在内存，服务器重启后原登记可能丢失，迟到回执无法关联。

### 7.2 不能混淆的结果

| 结果 | 表示什么 |
|---|---|
| BROKER_ACKNOWLEDGED | 传输已获确认，等待盒子业务结果 |
| ACCEPTED | 盒子受理，不代表设备已执行 |
| RUNNING | 操作处理中 |
| SUCCEEDED | 云端记录盒子报告成功；真实性需盒子以设备反馈判定 |
| FAILED / REJECTED | 失败或拒绝 |
| TIMED_OUT → UNCONFIRMED | 云端未确认结果，不能断言现场没执行 |

急停成功应依据设备反馈；配置成功应依据实际读回与保存结果。目前回执主要带 resourceVersion，不含完整实际参数快照，不能只凭版本号把云端申请值作为实际已生效配置。

命令记录持久化及恢复待接入 cleaning_command_record；这张审计表本身不等于可靠发送 Outbox。

## 8. 尚未接入的三类消息如何归属

本节是实施方向，不是新增方法已写好。

| 类型 | 经大厅后的归属 | 需要处理的业务事实 |
|---|---|---|
| STATE_EVENT | 清洗服务 | session/round、阶段变化、真实方向、PLC 恢复起止及终态 |
| SESSION_SUMMARY | 清洗服务组织作业归档，统计服务处理合格统计 | 作业起止、完成轮数、模式、实际版本、结果、完整性；不凭汇总消息补造缺失曲线 |
| ALARM_HANDLING | 告警服务 | 人工处置状态、操作者、操作 ID、双方版本及冲突结果 |

CloudStatisticsService.acceptCompletedRound() 和完成轮次缓冲已经有入口，但没有证明状态/汇总 MQTT 消息已实际触发它；轮次聚合与查询目前仍未接入。

历史可能先于轮次终态到达。保存事实后等待归属和完整性核对，不能凭单个有效点宣布正常完成，恢复往返也不新建普通轮次。

## 9. 消费确认、数据库确认与可靠性

### 9.1 三个不同确认

- 入站 source.acknowledge(envelope)：当前只是消费结束标记，消息已经取出；不是向 Broker 手动确认，也不向盒子回复。
- MQTT Broker PUBACK：传输确认，不表示云端业务或数据库完成。
- 云端历史保存 ACK：目标是事务提交后的应用协议确认，当前未实现。

历史投递到 CloudWarmBuffer 后，Dispatcher 即执行消费确认。此时 MySQL 可能尚未处理；若云端重启，内存批次可能丢失。因此可靠闭环必须依赖盒子保留未确认批次及业务补传，不能靠当前队列命名保证。

### 9.2 当前异常行为

| 情况 | 当前实现 | 待完善 |
|---|---|---|
| Topic/JSON/参数错误 | 忽略或 deadLetter() | 分类记录、维护入口，避免敏感 payload 泄漏 |
| 临时业务异常 | requeue()，超过 3 次或队列满转死信 | 分级重试、容量控制和可恢复待处理 |
| 死信 | 当前仅输出日志 | 不是实际死信存储，无法保证重放 |
| 入站队列满 | 日志报错，未入队 | 不能认为 Broker 会因未入队自动业务重发 |
| 历史写库失败 | 当前及未处理批次返回温队列 | 回队存在容量竞争；重启依靠盒子补传 |
| 服务器重启 | 各内存队列/状态可能丢失 | 持久化与业务确认恢复路线 |

实时可以覆盖旧待消费数据，历史、告警及操作事实不能套用同样覆盖策略。每盒最新快照合并应与来源序号、注册身份和版本校验协同，避免迟到包及未登记盒子占用资源。

日志正常单包大小使用 debug；故障状态变化、恢复和周期汇总再记录。当前部分异常逐次输出，不能声称已全面实现日志降频。对用户输出错误码、中文说明及处置建议，不返回堆栈、数据库异常和固定校验码。

## 10. 云端业务服务各自负责什么

| 服务 | 上传处理责任 | 当前边界 |
|---|---|---|
| 大厅 | 统一业务入口、分发；不进行 JSON 字段搬运 | 四分支已有 |
| 统计服务 | 实时更新、历史批次组织保存；后续曲线/周期计算 | 实时和历史骨架已有，统计 query/聚合/重建仍抛未接入异常 |
| 清洗服务 | 作业/轮次/配置事实与一致性 | 当前实时查询和远程控制组织已有，状态/汇总事件归档待接入 |
| 告警服务 | 告警事实及人工处置 | 当前内存更新，持久化及同步待接入 |
| 命令服务 | 原请求登记与回执，后续保存 ACK 消息走既有消息机制 | 当前回执内存登记，保存 ACK 尚未组织 |
| 安全服务 | 用户权限及来源/目标校验等 | 不能用 Topic 匹配代替凭据认证；固定 accessCode 双端实施仍需完成 |

后台线程由 info/thread 下的任务及线程中心管理；业务方法只组织处理，不直接使用外部 MQTT 库或另开裸线程。缓存类仍在 info/buffer 下，数据访问主要按 MyBatis-Plus 逐步迁移，不替换整体框架。

## 11. 后续实施与验收顺序

1. 对齐四种已有上传的版本、身份、字段和有效性，避免缺字段默认正常。
2. 补历史 JSON 和单点规则，落实三表事务及精确业务保存 ACK。
3. 补未确认批次补传、服务器/盒子重启恢复和队列满处理。
4. 接状态事件及作业汇总，补作业/轮次/恢复事实；实际配置快照需单独明确。
5. 持久化告警及命令登记，补告警处置版本同步和审计。
6. 基于真实历史做后端曲线坐标、单位和周期筛选，再对齐页面展示。

应测试重复/乱序、跨盒子 ID 冲突、无作业实时包、恢复往返、部分无效通道、任务关闭、MySQL 失败、ACK 丢失、队列满和重启。月/年统计不能在缺拉力历史的情况下输出模拟结果。

## 12. 本次核对范围

只读核对了网络服务、Topic 开发配置、接收缓冲、表示层转换与分发、大厅、实时中心、历史服务、告警服务和命令登记源码。

本次未编译、未启用历史任务、未连接 MySQL/MQTT/PLC、未执行 SQL。结论是四类上传已有处理骨架，三类尚未接入；持久化、字段一致性和可靠保存确认仍未全面完成，不能据此宣称现场整条链路已跑通。

