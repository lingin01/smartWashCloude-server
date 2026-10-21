# 清洗盒子、云端与前端完整数据流程及字段合同

日期：2026-10-12。性质：按最新讨论整理的实施合同草案及当前源码核对，不表示协议已经全部实现。本次只编写文档，未修改 C++/Java/Vue、未编译、未执行 SQL、未部署或连接 PLC。

本文的“现有”表示当前源码已有字段/方法；“补充”表示后续需要实现。2026-10-11 简化稿中的 0.1 米分桶及另加 points[] 是旧建议，本阶段改为正常上升每 0.5 米保存真实测量点，并优先复用已有 records[] 与历史表。先不实现复杂多种对齐算法。

## 1. 最新业务口径

| 项目 | 确定的口径 |
|---|---|
| 设备关系 | 一个清洗系统对应一个盒子，四台绞机、八根牵引索、八个拉力传感器；每台绞机两根索、两个传感器 |
| PLC 采集 | 盒子每 500ms 获取一帧；实时缓冲更新和历史留点是不同流程 |
| 实时展示 | 覆盖上升、下降、等待及恢复过程；不只上传历史留点 |
| 正常历史 | 自动清洗上升中，相对上一次已保存的高度移动达到 0.5m，保留当前真实点；首尾和重要事件额外保存 |
| 正常下降 | 不保存普通下降测量曲线；仍有实时数据、阶段事件与轮次上下文 |
| 下降异常 | 额外保存异常测量及事件，direction 保留 DOWN，不改写成上升数据 |
| 自恢复 | PLC 正在恢复时系统正常、工作运行；恢复成功继续本轮，不自动判失败 |
| 最终故障 | 连续五次恢复失败且 PLC 明确报告最终故障，盒子才按该业务条件标记故障；不能仅看尝试次数就判故障 |
| 轮次 | 一轮仍为下降＋上升；一个持续自动作业 session 可含多轮，可跨日 |
| 检修 | 单机现场检修不计入自动清洗轮次和正常曲线统计 |

该恢复规则是对 PLC 自恢复这一场景的判定，不表示急停、离线或其他独立故障必须等五次。已应用现场安全保护保留，不因本稿延迟或关闭。

**待确认：下降异常从 PLC 开始自恢复就保存，还是仅最终故障确认时保存。**本文将“下降异常”保留为明确事件条件，不把其中任一选择写成已确认规则。建议恢复开始就留事件点/异常轨迹，但不是系统故障；须由用户确认后实施。

500ms 不是 MQTT 必须每 500ms 上传。当前盒子 TelemetryUploadPolicy 默认实时 1000ms、历史批次 2000ms、每批最多 100 条，均为独立策略。实时只发最新值；历史保留稳定身份并批量补传。

## 2. 数据的身份、单位和职责

| 字段 | 含义 |
|---|---|
| tankId / systemId / boxId | 罐体、清洗系统、盒子分别标识，不用 MQTT clientId 替代业务身份 |
| winchId / ropeId / sensorId | 绞机、索、传感器分别标识；不能按数组顺序或字符串编号猜关联 |
| sessionId + roundNumber | 实际自动会话及会话内轮次；“今日第3次”只是显示序号 |
| streamId + sequence | 建议补充采样流标识，防止盒子重启序号重置碰撞；不能由云端虚构 |
| batchId | 一次稳定上传批次；同批次重试保持相同身份、范围和内容 |
| requestId | 一次操作的关联与幂等编号；HTTP、MQTT、回执保持一致 |
| alarmId / operationId / revision | 告警事件、一次处置操作和处置版本 |

建议双端明确力 kg、速度 m/min、高度 m、时间 Unix 毫秒，页面时间按 Asia/Shanghai 显示。当前盒子 tension 等值须先确认实际单位并补上传声明，不能只凭 Java 注释自动转换。原始转矩 N·m 与盒子换算后的力 kg 是两个指标；本阶段未确认的转矩保持预留/null，不伪装成拉力。

设备实时平均值由盒子按同帧有效数据计算；云端负责历史筛选、周期形态曲线、单位展示、轴和分页。前端只选择条件和渲染，不自行判断故障、计算均值、换算单位或补曲线。

## 3. 完整上传与接收流程

### 3.1 实时链路

```text
PLC → 既有设备采集/Runtime（500ms）
 → 盒子 CleaningService 组装同帧快照、状态及有效性
 → 热缓冲 → TelemetryService 定时读取最新值
 → CommandService::enqueueRealtime 封装 ProtocolMessage
 → 上行队列 → MqttService/Paho 发布
 → 云端 MqttService/Paho → MqttMessageBufferCenter 最新值槽
 → CleaningMqttDispatcher → Converter → CloudLobbyService
 → CloudStatisticsService.acceptSample → CloudRuntimeDataCenter 热缓冲
 → Web 表示层读取 → 页面 VO → 前端
```

WebSocket 后续复用同一页面 VO/同帧快照推送，网络发送仍属于技术层。当前云端 /ws 只有通用连接发送能力，未接新大厅快照推送，不能当作已实现实时页面订阅。

### 3.2 历史链路

```text
同帧快照及轮次/事件事实
 → TelemetryReductionProcessor：正常 UP 0.5m 留点，DOWN 异常按确认规则留点
 → 盒子温缓冲 → 本地持久化 → 待上传批次
 → CommandService::enqueueHistory → 上行队列/断网存储 → MQTT
 → 云端接收队列 → 表示层解析 DTO/业务对象 → 大厅
 → CloudStatisticsService.acceptHistoryBatch → CloudWarmBuffer
 → 线程中心历史保存任务 → DAO → 数据库事务提交
 → 云端保存结果消息 → 盒子确认批次 → 保存本地确认状态/释放上传副本
```

最后的历史业务保存 ACK 尚未完成。现有云端历史落库任务默认关闭；不能把收到包、进入温缓冲或 MQTT PUBACK 当作数据库提交，也不能据此释放盒子历史。

### 3.3 云端接收业务分派

| Topic 后缀 | 对象/业务去向 | 当前接收状态 |
|---|---|---|
| telemetry/realtime | TelemetrySample → 统计服务 → 热缓冲 | 已有；需要适配盒子 status 2.0 和无作业空 sessionId |
| telemetry/history | TelemetryBatchBo → 统计服务 → 温缓冲/DAO | 已有；盒子尚未序列化测量数组和轮次 |
| alarm/event | CloudAlarm → 告警服务 | 已有；完整来源/恢复/阈值字段尚未全部映射 |
| command/reply | CommandAck → 命令服务 | 已有关联及状态更新 |
| alarm/handling/up | 告警处置结果 → 告警服务 | 云端尚未配置/接入该完整路由 |
| state/event | 阶段/轮次事件 → 清洗服务 | 盒子有发送方法，云端分发与业务触发待补 |
| session/summary | 作业/轮次汇总 → 清洗及统计服务 | 盒子有发送方法，云端分发与业务触发待补 |

Topic 前缀均为 cleaning/box/{boxId}/。云端主题必须来自配置表/注册类，不能在业务流程散落拼接。现有消息使用 QoS1、retained=false；重复投递由业务去重。

## 4. 盒子上传字段

### 4.1 实时数据

当前 schemaVersion 为 2.0，以下字段已进入实际 JSON：

| 字段 | 类型 | 含义 |
|---|---|---|
| schemaVersion、boxId | string | 协议版本、盒子身份 |
| sessionId | string | 自动会话；无作业须允许空值并明确语义 |
| sequence、statusVersion、sampleTimeMs | integer | 采样序号、状态版本、采样时间 |
| status | object | 系统状态与清洗阶段 |
| speedLevel | integer | 旧挡位字段，不能等同于当前整体设定速度 |
| ropes | array[8] | 八根索的独立测量 |
| winches | array[4] | 四台绞机实测 |
| groupAverageTension、groupAverageSpeed | number | 盒子计算的组平均，不代替单索安全判定 |
| groupAverageHeight、heightSpread | number | 四机平均高度、高度差，不是圆环整体高度 |
| hasWarning、hasCriticalAlarm | boolean | 告警标识，不单凭此字段推导系统故障 |

status 当前包含 systemStatus、systemStatusTextZh、cleaningPhase、cleaningPhaseTextZh、cleaningPhaseKnown、cleaningActive、emergencyActive、online、faultCode。

当前系统枚举字符串为 Normal/Maintenance/Fault/Offline，阶段为 Idle/Prechecking/Ready/Descending/BottomWaiting/Ascending/TopWaiting/Completed/Unknown。当前 Normal 中文为“运行”，与用户要求的系统“正常”＋工作“运行”需在业务中文展示层明确分开，不能前端混用。

ropes[] 当前字段：ropeId:string、sensorId:string、winchId:string、tension:number、valid:boolean、level:integer（0正常/1预警/2严重/3无效）。

winches[] 当前字段：winchId:string、heightMeters:number、speed:number、tensionAverage:number、online:boolean、running:boolean、faultCode:string。

**需要补充的实时字段：**

| 字段 | 类型/语义 | 当前状态 |
|---|---|---|
| ringHeightMeters、heightValid | number/null、boolean；圆环真实高度 | 盒子对象有，未写入实时 JSON |
| currentRound、mode、direction | integer/null、string、UP/DOWN/STOP/UNKNOWN | 盒子对象有，未写入实时 JSON |
| tensionUnit、speedUnit、heightUnit | string，建议 kg、m/min、m | 对象有，未上传 |
| winches[].speedValid | boolean | 对象有，未上传 |
| winches[].tensionSum、tensionAverageValid | number/null、boolean | 合计对象有；平均有效性需明确，不能缺一路补0 |
| workStatus、workStatusTextZh | string，建议 RUNNING/IDLE/ENDED/UNKNOWN | 新增草案，来自盒子/PLC作业事实，不根据四台速度猜测 |
| plcRecovery | object，见下表 | 新增草案；仅有PLC反馈时填写 |
| appliedSettingsVersion | integer/null | 实际配置版本，需要来源 |
| streamId | string | 新增采样流身份，双端落实稳定生成规则 |

plcRecovery 草案包含 active:boolean、attempt:integer/null、result:string（NONE/RECOVERING/RECOVERED/FAILED/UNKNOWN）、finalFaultConfirmed:boolean/null、sourceFaultCode:string/null。没有PLC点位时保持未知，不由盒子虚构“已试五次”。恢复 active=true 不把系统改成 Fault，第五次成功仍是恢复成功；只有明确最终失败才按该恢复业务判故障。

原始 sourceFaultCode 与系统 status.faultCode 必须分开。当前盒子状态刷新会把非空业务 faultCode 判为 Fault，因此恢复中仅观察到的 PLC 原始编号不能直接复制到 status.faultCode；应经已确认的恢复/最终故障规则映射，其他独立故障及急停仍按原规则处理。

### 4.2 历史批次与每个真实留点

批次当前字段为 schemaVersion:string（当前1.0）、batchId:string、boxId:string、firstSequence/lastSequence:integer、records:array（当前云端限制1～100条）。建议补 recordKind=POINT/SEGMENT、单位声明与采样身份/配置版本，并正式升级双方协议版本；版本号待实施时确定，不能以新增字段假冒已支持版本。

records[] 当前实际上传：sessionId、telemetrySequence、state、direction、startHeightMeters、endHeightMeters、startTimeMs、endTimeMs、triggerReason。

对于 0.5m 留点，采用 POINT 口径：startTimeMs=endTimeMs=真实采样时刻，startHeightMeters=endHeightMeters=真实圆环高度。不要把时刻点说成一个区间的均值，不插值伪造严格的0.5、1.0等高度。事件点可能不满0.5m。

| records[] 补充字段 | 类型 | 用途 |
|---|---|---|
| roundNumber | integer>0 | 实际会话轮次，已有对象未上传 |
| mode | string | 自动与检修区分，需补协议 |
| startHeightValid、endHeightValid | boolean | 已有对象未上传 |
| ropeTensions | array[8] | 已有对象未上传，每路见下表 |
| winchSpeeds | array[4] | 已有对象未上传 |
| winchAverageTensions | array[4] | 建议补同帧本机两索均值及有效性，避免从不同时刻摘要拼均值 |
| eventCode、plcTensionDeviationActive | string、boolean | 已有对象未上传；偏离不等于最终故障 |
| recordKind、recordReason | string | 建议 POINT；NORMAL_UP/RECOVERY_EVENT/DOWN_EXCEPTION/FAULT/ROUND_END 等，枚举待确认 |
| sourceSequence、streamId | integer、string | 原始采样关联，当前遥测记录序号不等同PLC原始采样序号 |
| configurationVersion、bindingVersion | integer/null | 实际配置及测点绑定，未知不得虚构 |

每路摘要沿用 minimum、maximum、average、latest:number/null，sampleCount:integer。额外带稳定 targetId（索或绞机）、ropeId/sensorId/winchId、channel 与有效性，数组顺序不能成为唯一来源身份。

POINT有效测量：min=max=average=latest=该次实测值，sampleCount=1；无效值为null，sampleCount=0。SEGMENT真正有多次采样时才使用区间统计。转矩数组 winchTorqueForcesKg 当前含义待确认，第一阶段可以缺省/无有效样本，不画伪转矩曲线。

completed/statisticsEligible 不能只靠每个点的旧默认值认定轮次已完成：所有点先归属在途轮次，待收到最终轮次结果后由云端统一关联统计资格。当前对象 statisticsEligible 默认true及仅尾部记录更新的行为，需要后续修正，不能让失败轮次的前半段混入正常周期平均。

复用 cleaning_telemetry_batch、cleaning_telemetry_segment、cleaning_segment_measurement。POINT 是测量事实，单次样本不等于区间精确统计量；没有独立实际点时，不能用大区间 min/max/average 重建波形。

### 4.3 告警产生与恢复

当前字段：schemaVersion、alarmId、sessionId、severity、alarmCode、notice、affectedDeviceId、affectedRopeId、currentValue、thresholdValue、unit、active、occurredAtMs、recoveredAtMs。

建议补 source（PLC/BOX_SYSTEM/CLOUD等明确业务来源）、sourceFaultCode（原始D3故障编号）、boxId、roundNumber、winchId/sensorId、事件revision和发生时规则版本。alarmCode保持统一业务码，sourceFaultCode保留原始码，不混用。

恢复过程是可观察事件，不必直接升为故障级告警；具体告警分类以盒子事实为准。设备告警由盒子产生，云端只产生自己观察到的保存失败、上传中断等平台事件，不能伪造传感器故障。

notice 当前完整字段：publicCode、severity、titleZh、summaryZh、currentImpactZh、resolutionSteps[]、retryable、contactMaintenance；处理步骤包含order、titleZh、descriptionZh、requiresProfessional。用户说明不带内部堆栈、密码、密钥或原始控制报文。

### 4.4 告警处置同步

当前字段：schemaVersion、alarmId、state、revision、operationId、handledBy、source、resolutionNote、updatedAtMs。处置回复额外有 accepted、duplicate、conflict、code、notice。

state、source 当前发送数字枚举，双方需建立明确版本映射，前端只接收中文/字符串语义VO。active是设备事实，处置state是人工工作流；人工“完成”不能把设备active改false、不能填写设备recoveredAt，也不解除急停。

目标闭环：云端提交 operationId+expectedRevision → 盒子确认/冲突 → 云端保存权威revision及实际状态。两个端同时处理时旧版本不覆盖新版本。当前完整云端链路待补。

### 4.5 操作回执

| 字段 | 类型 | 含义 |
|---|---|---|
| schemaVersion、requestId、commandId | string | 版本、原请求、盒子命令；拒绝时commandId可以为空 |
| stage | integer | 当前0拒绝/1受理/2执行中/3成功/4失败/5超时，云端转成字符串 |
| code、notice | integer、object | 内部结果及中文公共说明；页面优先notice.publicCode |
| timestampMs | integer | 盒子结果时间 |
| resourceVersion | integer | 实际配置版本，不代表实际值已同步 |

急停 SUCCEEDED 需要观察到停止反馈；配置 SUCCEEDED 需要写入/读回和保存确认。ACCEPTED/PUBACK/HTTP202都不是最终生效。

### 4.6 状态事件和作业/轮次摘要

状态事件当前为 schemaVersion、sessionId、previous/current（旧枚举）、timestampMs、reason。建议补 boxId、roundNumber、eventId/revision、eventType、系统状态/工作状态/真实阶段、方向及恢复对象。

作业汇总当前为 schemaVersion、sessionId、startedAtMs、finishedAtMs、actualTravelMeters、speedLevel、rawSampleCount、uploadedRecordCount、alarmCount、result。

为不新增大量Topic，建议在该汇总通道补 summaryType=ROUND/SESSION，分别表达每轮结果和整个持续作业的结果；这是新增协议草案，不是当前已能识别的类型。ROUND至少补 roundNumber、completed、result、endReason、statisticsEligible、configurationVersion、恢复结果及实际终态时间；SESSION再补完成/中止轮数和最终时间。稳定事件ID/revision防止重传重复计数。

摘要若需要精确全过程均值，应由盒子另保留原始sum/count或时间权重。0.5m留点均值只代表保留点/空间形态，不等同500ms全过程均值。

## 5. 云端发往盒子的对象与流程

```text
用户HTTP请求 → CleaningWebService → Web表示层DTO→Param
 → CloudLobbyService → CloudSafetyService → CloudCleaningService
 → CloudCommandService登记 → OutboundConverter封装协议对象
 → MqttProtocolMessage → 发送队列 → MQTT服务发布
 → 盒子接收队列 → MQTTCommandDispatcher/Converter → LobbyService
 → 校验/清洗业务/已有设备控制链路 → 反馈
 → CommandService回复 → 云端接收队列/表示层/大厅 → 原操作状态更新
```

DTO与ProtocolMessage不同：DTO是业务消息内容；ProtocolMessage还带Topic、QoS、优先级、有效期、重试等内部传输信息，不能把整个内部队列对象暴露给前端。

### 5.1 公共命令字段

当前 RemoteCommandEnvelopeDto 包含 schemaVersion:string、requestId:string、targetBoxId:string、operatorId:string、commandType:string、issuedAtMs/expiresAtMs:long、nonce:string、payload:object。转换器另加 flowCheckOnly:boolean、signature:string、可选keyId。

操作人由云端真实登录取得；消息目标与Topic一致；重试不换requestId、不延长原有效期。当前控制TTL为30秒、急停优先级100、配置10，QoS1且不保留消息。

accessCode是用户讨论的固定校验码方案，尚未实现：应由云端受控配置加入报文并由盒子对照本地配置核对，不接收前端自填、不放入VO或日志。它不是HMAC签名；本稿不假定已替换现有拦截。

当前正式模式仍被 PendingRemoteCommandSigner 阻止；显式flow-check白名单模式能发验证消息，但盒子在安全入口回REJECTED/FLOW_CHECK_RECEIVED，不执行PLC动作。

### 5.2 急停

Topic：command/emergency-stop，commandType=emergency_stop。

HTTP CloudEmergencyStopDto：requestId必填，sessionId/reason可选。MQTT payload对应sessionId及reason，未提供reason默认“远程急停”。不给前端开放远程启动、单机检修、解除急停等新动作。

### 5.3 三项配置

HTTP CloudSettingsUpdateDto：requestId、expectedVersion、settings。

| HTTP settings字段 | 类型 | MQTT payload.plan字段 |
|---|---|---|
| startTime | string HH:mm | startTime，同值 |
| intervalSeconds | integer>0 | runIntervalMs，后端安全乘1000 |
| groupSpeedMetersPerMinute | decimal>0 | groupSpeedMetersPerMinute |
| speedLevel | integer，可选预留 | 当前没有映射，不下发；不能只传挡位猜速度 |

expectedVersion为已应用版本，不能前端虚构0。申请值先入命令记录；成功确认后才保存实际配置版本和快照。现有回执没有独立完整配置快照，需要补 appliedSettings 或通过确认的快照消息同步三项实际值，不能让查询永远只有版本。

### 5.4 其他云端下行

告警处置payload：operationId、alarmId、targetState、expectedRevision、resolutionNote、operatedAtMs，操作者从可信身份取得。Topic alarm/handling/down，云端路由待接。

历史业务保存确认DTO已有盒子预留：access公共上下文、batchId、channel、saved。正式Topic尚未确定，不杜撰已上线路径。建议补 firstSequence/lastSequence 或内容指纹以核对原批次；校验来源、目标、通道及真实登记批次，重复ACK幂等，saved=false不释放数据。

用户、设备初始化/同步仍按既有 CloudInitializationDto、CloudUserSyncDto、CloudDeviceMetadataSyncDto 预留；首次初始化不自动启动设备、不覆盖本地寄存器配置。本阶段不依赖这些完整功能才画单轮曲线。

## 6. 云端业务类及基础表

| 类 | 核心业务 |
|---|---|
| CloudLobbyService | 同一业务入口，派发上传、命令、查询 |
| CloudCleaningService | 自动session/round、实际配置和两种控制操作的组织；当前session/round应用方法需补 |
| CloudCommandService | 创建/去重/入发送队列/回执/超时/查询；当前登记主要在内存 |
| CloudStatisticsService | 最新快照、历史批次、数据库保存和曲线/周期查询；后半部分待实现 |
| CloudAlarmService | 告警事实及人工处置，不能从平台代判PLC状态；当前主要内存 |
| CloudSafetyService | 用户、权限、目标和请求检查；不把告警当作计算出来的现场停机事实 |

线程与消息缓冲继续放info，DAO负责简单持久化查询；事务、状态机和业务分派在服务中。网络层不直接查表生成页面、业务层不创建Paho客户端。

基础六表：cleaning_system、cleaning_winch、cleaning_rope_sensor、cleaning_telemetry_batch、cleaning_telemetry_segment、cleaning_segment_measurement。

第一阶段业务六表：cleaning_session、cleaning_round、cleaning_settings_revision、cleaning_command_record、cleaning_alarm_event、cleaning_alarm_handling_event；用户复用t_user。字段仍以现有DDL草案和迁移核对为基础，表存在不代表DAO/业务完成。

0.5m POINT复用现有三张历史表，补原始采样身份、有效性、mode、recordKind/reason、版本等必要列。不同batch重传同一点需要稳定sourceSequence/streamId唯一关联，而不仅依赖batchId去重。DAO事务同时保存批次、测量点及各通道，保存ACK在提交后发；具体可靠ACK任务和重启恢复尚需实现。

暂不逐日/逐月/逐年建表；月年曲线先查询已有留点，后续再启用统一聚合表。正常下降没有测量历史，不补0，不纳入“正常上升参考曲线”；DOWN异常保留给故障分析和事件详情。

## 7. 给前端的响应对象

### 7.1 公共外壳和接口

继续使用 Result<T>：code:number、message:string、data:object/null。200是查询成功；202是命令已提交，前端必须支持，不马上显示“已停机/已生效”。旧模拟页面接口已撤销，不回滚引入演示数据。

当前真实接口前缀 /api/cloud/cleaning/boxes/{boxId}：GET /realtime、GET /settings、GET /commands/{requestId}、POST /emergency-stop、PUT /settings。首页/单机/历史/告警完整页面读入口为后续在同一受保护前缀下增加的合同，不能说现有已经全部开放。

### 7.2 首页与单机详情VO

当前 CloudRealtimeVo：boxId、available、message、data。data有sessionId、sequence、sampledAt、state（旧字段）、speedLevel、groupAverageTension/groupAverageSpeed/groupAverageHeight/heightSpread、hasWarning/hasCriticalAlarm、ropes、winches。

目标按已有 CommandCenterHomeVo / CommandCenterWinchDetailVo 页面模型复用与适配，最小核心字段如下：

| VO字段/组合 | 页面意义 |
|---|---|
| systemId、boxId、tankId及名称 | 已确认档案/拓扑，不写死编号 |
| sampledAt、sequence、statusVersion | 同帧来源，不逐路拼不同版本 |
| systemStatus/textZh、workStatus/textZh、cleaningPhase/textZh | 系统正常与工作运行分开，恢复不误显示故障 |
| sessionId、currentRound、displayRoundLabel | 当前作业与实际轮次；当日显示第几次由后端给 |
| mode、direction/textZh、plcRecovery | 模式、真实运动及恢复过程 |
| ringHeightMeters、heightValid、heightUnit | 圆环主坐标；平均高度另有独立字段 |
| ropes[] | 独立拉力、有效性、单位、状态及已应用单索预警/安全值 |
| winches[] | 单机速度/有效性、两索平均及合计/有效性、单机高度、在线/运行及故障说明 |
| groupAverageTension、groupAverageSpeed | 后端事实；同步速度PLC D65建议另设synchronizedSpeedMetersPerMinute及有效性，不与均速合并 |
| appliedSettingsVersion | 实际生效版本，不是申请版本 |

没有来源的转矩、累计时长、阈值、实际配置返回null及说明。原始N·m不按吨展示；单索安全阈值也不是本机平均值的安全阈值。前端的自动/吨/公斤是显示选择，不修改盒子保护。

### 7.3 配置和命令VO

CloudSettingsVo当前包含boxId、state、version、settings；settings为startTime、intervalSeconds、speedLevel、groupSpeedMetersPerMinute。当前未同步时state=NOT_SYNCHRONIZED且实际配置/版本为空；目标只有盒子确认后才显示APPLIED。

CloudCommandVo现有字段：requestId、cloudCommandId、boxCommandId、boxId、type、state、code、message、createdAt、expiresAt、updatedAt、resourceVersion。可查询QUEUED/BROKER_ACKNOWLEDGED/ACCEPTED/RUNNING/SUCCEEDED/REJECTED/FAILED/UNCONFIRMED/BLOCKED等；UNCONFIRMED提示现场核对，不能猜动作是否执行。

### 7.4 历史列表与详情VO（待实现）

复用 SystemHistoryListVo / SystemHistoryEntryVo / SystemHistoryDetailVo，不新建一套同义历史服务。

最小列表响应：entries、total、page/pageSize、range、dataThrough。每条包含内部recordId、sessionId、roundNumber、displayRoundLabel、startedAt/finishedAt、durationSeconds、result/resultTextZh、completed、statisticsEligible、recovered（有PLC明确结果时）、alarmCount、异常下降记录是否存在、实际配置版本、dataComplete。

页面显示“今日第3次清洗，开始09:20，结束09:45”，不把内部requestId或数据库键当轮次次数。当前轮次未结束时结束时间为空，持续作业不要被午夜拆成新session。

详情包含entry、正常UP测量曲线、下降异常事件/测量、告警列表、恢复过程、实际配置快照。正常DOWN无数据时显示“正常下降不保存历史曲线”，不显示0或假曲线；全轮经过时间仍可包含下行，历史数据覆盖并非完整上下波形。

### 7.5 曲线筛选DTO及曲线VO（待实现）

查询条件：系统/绞机路径、scope=TODAY/MONTH/YEAR、anchor、metric=TENSION/SPEED、source=WINCH_AVERAGE/SENSOR1/SENSOR2、direction、grouping、unitIds（最多3个所选项）、displayUnit=AUTO/KG/TON。

正常趋势direction默认UP；DOWN只提供明确的异常分析记录，不提供不存在的正常下降月年曲线。速度为选中绞机实测速度，整组实测均速、同步速度、设定速度互不替代。

| 返回组合 | 字段 |
|---|---|
| range | startAt、endAt、timezone、dataThrough |
| units[] | id、label（今日第N次/某周等）、startedAt、finishedAt、empty、roundCount |
| xAxis | type=HEIGHT、name=圆环高度、unit=m、min/max/ticks、decimalPlaces |
| yAxis | name=拉力或速度、unit=kg/t/m/min、min/max/ticks、decimalPlaces |
| series[] | id、label、source、direction、unitId、kind=ACTUAL/REFERENCE、points[] |
| points[] | x（真实高度或明确聚合桶坐标）、y/null、sampledAt（实际点时）、roundNumber、valid/quality、recordReason、参考形态的contributingRounds |
| capabilityNote/dataComplete | 缺测、缺协议、估计形态或实际覆盖说明 |

沿用 WinchTrendUnitSetVo 等参考模型，坐标轴/明确来源等需要补字段，并经Converter映射前端旧字段。前端直接画 x/y 和ticks，不再value*1000、不固定0～25m、不自行按高度把上下方向混排序。

今日展示当前/所选轮次UP曲线，可加今日已完成合格轮次参考线。本月/本年通过选择周、月、季度、半年得到同高度的多点上升形态曲线，不是一月一个点。自然周与月取交集，首尾不满7天照实显示；空周期不补数据。

0.5m是盒子留点间距，不保证实际高度严格落在固定网格。若做参考曲线，后端用明确0.5m展示桶归并同指标UP点，每轮每桶先生成代表值再按轮次等权；标注“0.5m保留点平均形态”，不是500ms全过程精确均值。不插值跨大缺口，也不跨绑定/配置变化混成无说明的同一曲线。精确统计在取得原始权重后单独实现。

全部返回曲线/参考线统一单位及纵轴；AUTO由后端确定。单位变化本身不会放大波动，轴范围由后端决定。Tooltip最多显示3条所选曲线＋1条参考线；20次清洗在选择列表中选择，不默认全部叠加。

### 7.6 告警页面VO（待实现完整查询）

字段：alarmId、source/sourceTextZh、alarmCode、sourceFaultCode、severity/severityTextZh、system/box/winch/rope/sensor身份、notice、active、occurredAt/recoveredAt、handlingState/handlingStateTextZh、revision、handledBy、resolutionNote、updatedAt。计数由后端按唯一事件统计；采样次数、自恢复尝试次数、告警数、失败轮次数不能混用。

## 8. 示例：留点到前端（仅格式说明，不是实测数据）

盒子历史 records 的一条示意：

```json
{
  "recordKind":"POINT",
  "sessionId":"session-example",
  "roundNumber":3,
  "telemetrySequence":101,
  "mode":"automatic",
  "direction":"UP",
  "startHeightMeters":10.52,
  "endHeightMeters":10.52,
  "startHeightValid":true,
  "endHeightValid":true,
  "triggerReason":"HEIGHT_STEP",
  "ropeTensions":[
    {"targetId":"rope-example","sensorId":"sensor-example","winchId":"winch-example",
     "minimum":380,"maximum":380,"average":380,"latest":380,"sampleCount":1}
  ]
}
```

为缩短仅展示1路，正式消息应包含全部8路，时刻、单位、稳定采样关联和4路速度也须提供；不能把这个片段直接当作当前云端可接受的完整批次。

给前端单个实际点：x=10.52，y=380，单位kg，方向UP，轮次由后端显示“今日第3次”。不能把x强改为10.5并宣称PLC在那里采到了同值；参考曲线分桶坐标可以10.5，但必须标明聚合形态。

## 9. 当前实现与待办总结

已有：云端统一消息队列、两类控制入口、四类MQTT接收分派、热/温缓冲入口、历史批次DAO、命令回执/查询及联调模式。之前的本机Broker测试不是现场PLC或数据库验收。

待实施：

1. 修复盒子0.5m基准：不能拿相邻500ms帧比较，应保存lastSavedHeight；按正常UP/下降异常策略留点，事件点与普通高度基准的关系需明确，起止/恢复/失败去重避免每500ms重复记同一告警。
2. 序列化实时新字段及历史8路拉力、4路速度、轮次、身份/有效性，统一单位；修正云端旧state读取及空sessionId拒绝。
3. 实现轮次开始/终态应用及统计资格，以最终结果为准，不把恢复过程判最终故障。
4. 完成数据库迁移/事务、命令与告警持久化、历史保存ACK、配置快照；目前任务默认关闭、固定accessCode未实现。
5. 接通剩余3类上传消息，完成真实页面VO、曲线读取和WebSocket同帧推送。
6. 在盒子环境编译，现场验证PLC恢复标志/最终故障、真实高度和四机八索；本稿未执行这些操作。

## 10. 待用户确认的最少事项

- 下降异常记录：恢复开始即留，还是最终故障确认才留。
- PLC是否提供恢复中标志、尝试次数、最终失败与原始故障编号，缺字段时显示未知而非猜测。
- 0.5m历史采用本稿单点口径，还是以后需要区间摘要；第一阶段建议单点，避免复杂聚合和双轨点表。

本稿是完整目标流程，不是所有功能当前都已完成；尤其固定校验码、实时状态协议对齐、历史测量字段、业务保存ACK和前端曲线尚需落实。
