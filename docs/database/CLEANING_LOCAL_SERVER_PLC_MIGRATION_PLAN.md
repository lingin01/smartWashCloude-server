# 盒子 PLC 采集迁移到现场服务器实施方案

日期：2026-10-14，修订 2。对象：`iot_box_cleaning` C++ 盒子端与 `SmartWashCloud-Server` Java 服务端。状态：源码对照后的实施设计，不是已实现或联调通过报告。

最新决定：目标系统删除 MQTT 接入／签名／Topic／队列及无实际用途的旧清洗路线，不保留双模式运行。系统内部以不可变、强类型 `Message` 传递操作、采样、事件、写结果和保存结果。全链路与并发设计见 [Message、线程与锁方案](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/05_INTERNAL_MESSAGE_FLOW_AND_CONCURRENCY_PLAN.md)，具体清理范围见 [无用类及依赖删除说明](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/06_OBSOLETE_CLASS_REMOVAL_PLAN.md)。

2026-10-15 实施状态：原服务端已完成本轮旧通信清理，详见 [执行记录](CLEANING_MQTT_REMOVAL_EXECUTION_20261015.md)。以下内容仍是完整目标设计；Message 消费、PLC 下行、采集与轮次闭环未因删除而自动完成，当前操作明确返回通道未就绪。复制后的 SmartWashLocal-Server 不改动。

## 1. 本次确定的部署方向

服务器部署在现场电脑，与 PLC 在同一局域网。后续由 Java 服务端直接通过 Modbus TCP 读取和写入 PLC，替代盒子承担采集、业务状态判断、实时缓冲、历史整理和数据库保存。

PLC 继续负责自动运行、升降控制、保护及现场操作。现场人员设置时间等参数并启动后，作业来源向用户显示“系统自动执行”。服务器本阶段不新增启动、普通停止、暂停、恢复或解除急停功能，只下发急停和三项参数修改。

迁移的是盒子的分层处理方式及有效实现，不是复制 C++ 文件到 Java 项目，也不是在服务器内重新发布 MQTT 来模拟盒子。保留 Spring Boot、现有安全机制、Web 入口、业务服务、线程中心、缓冲模块和数据层调用边界。

```text
当前路径
PLC → C++ 盒子 Runtime／缓冲／业务整理 → MQTT → Java 服务端 → 数据库／页面

目标本地路径
PLC ↔ Java 网络设备层 → PLC 表示层 → 设备 Runtime
                                      ↓
                               设备快照发布任务
                                      ↓
                               设备快照／事件缓冲
                                      ↓
                              CloudCleaningService
                               ├─ 实时业务快照 → 热缓冲 → 页面
                               └─ 会话／轮次及快照 → 历史整理 → 温缓冲 → DAO → MySQL
```

外网断开不应中断现场采集。现场电脑、Java 进程或数据库故障仍会影响记录；没有 PLC 历史缓存或本地可靠暂存，就不能补回停机期间从未采集的数据。

## 2. 两个项目当前已有与缺失的能力

### 2.1 盒子端可以参考的实现

| 能力 | 已有类和方法 | 迁移原则 |
|---|---|---|
| 读取设备配置 | `NetworkDeviceDao::loadEnabledDevices()` | 配置驱动，不把寄存器地址散落在服务方法 |
| 周期读写调度 | `DeviceControlCoordinator::runPollingCycle()` | 有限消费下行消息，再执行到期采集；保留超时与退避 |
| 设备连接与写响应 | `NetworkDeviceManager::pollDueDevices()`、`dispatchDeviceMessage()` | 网络层关联请求、校验响应并返回结果 |
| Modbus 读取解码 | `ModbusTcpDevice::poll()`、`ModbusTcpCodec`、测点映射器 | 合并连续点、校验报文、按配置解析类型和倍率 |
| Runtime 保存 | `PlcNetworkDeviceRuntime::updateDeviceData()`、`getAllStatusSnapshots()` | 保存测点值、质量和真实采集时间，不判断清洗轮次 |
| 发布设备快照 | `DeviceScheduler::registerTasks()` | Runtime 是来源，缓冲是快照；不要改成大厅直接读 PLC |
| 业务转换 | `CleaningService::processDeviceSnapshots()`、`makeTensionFrame()`、`makeRealtimeSnapshot()` | 业务层只读已解码数据，计算均值和业务状态 |
| 历史整理及保存 | `TelemetryService::reduceCycle()`、`CleaningPersistenceWorker::persistCycle()` | 参考有序输入、有限批次、失败保留；不照搬文件上传链 |

盒子当前目标轮询配置是 500ms，但设备快照发布任务硬编码为 1000ms。历史整理仍用相邻快照高度差判断 0.5m，首帧没有输出，没有完整的正常上行过滤及异常起止处理。`CleaningService` 尚未接通完整会话／轮次生命周期。上述缺口必须在服务器实现时修正，不能原样迁移后宣布完成。

### 2.2 服务端实际已有的主链路

| 类 | 当前职责或现状 | 本次目标 |
|---|---|---|
| `CleaningWebService`、`CloudCleaningWebPresentation/Converter` | HTTP 输入、DTO→Param、鉴权上下文、BO→VO | 保留；查询不触发 PLC 同步读取 |
| `CloudLobbyServiceImpl` | 接收 MQTT 业务数据；急停／修改配置先安全检查再进入清洗服务 | 保留用户入口；本地设备采集不是伪造 MQTT 消息 |
| `CloudCleaningServiceImpl` | 当前主要组装远程命令，查询热数据；设置返回 `NOT_SYNCHRONIZED` | 增加设备快照消费、状态／轮次管理及配置确认 |
| `CloudCommandServiceImpl` | 当前固定转换 MQTT 消息并入 MQTT 队列 | 改为单一本地 Message→PLC 路径，删除 MQTT 依赖，保留命令幂等、关联和超时 |
| `CloudCommandRegistry` | 有界内存命令登记、状态、回执关联 | 复用并扩展本地下行及写结果管理；长期审计仍需 DAO |
| `CloudRuntimeDataCenter` | 实时热数据及每来源最多 10000 帧的近期采样 | 作为本链路唯一业务实时中心，增加设备快照／事件入口 |
| `CloudWarmBuffer` | 历史批次、完成轮次两个有界队列 | 复用；不将所有实时帧自动当作历史点 |
| `CloudStatisticsServiceImpl` | 实时入缓冲、历史入队、批次转 Entity 落库；统计查询等仍未实现 | 增加本地历史整理周期，复用持久化入口 |
| `TelemetryHistoryPersistenceTask` | 定期调用 `runRealtimePersistenceCycle()`，按配置启用 | 本地历史仍走同一任务与 DAO；开发配置当前关闭 |
| `ThreadCenter` | 统一任务注册，当前固定 2 个调度线程 | 复用生命周期；PLC 阻塞 I/O 的隔离需要单独落实 |
| `TelemetryHistoryDao`、`CleaningDeviceDao` | 已有历史和设备关系接口 | 复用；新增实现遵守现有 MyBatis-Plus 约定 |
| `CleaningSessionDao`、`CleaningRoundDao`、`CloudCommandDao` | 已有接口，但不能据此认定具体持久化全部接通 | 逐项核对实现、事务、Bean 装配及建表后接入 |

项目同时还有 `InnerWallCleaningServiceImpl`／`CommandServiceImpl`／`RuntimeDataCenter` 旧路线，许多方法是 TODO。本方案以已接入 `/api/cloud/cleaning/boxes/{boxId}` 的 `Cloud*` 路线为主，实施时移除旧路线及其专属依赖；旧类不是另一个可同时启动的业务系统。Web、安全、数据库等共用能力保留，不借清理做全项目改包。

## 3. 接入边界和目录安排

沿用服务端现有顶层包，不做全项目目录重组。以下标注“新增”的名字是拟建类，不是当前已存在。

```text
com/company/project/
  network_access_layer/plc/       新增 Modbus 网络读写与连接管理
  presentation/plc/               新增采集结果转换与 Runtime 更新出口
  dto/plc/                       新增协议层读写结果对象
  info/device/                   新增设备 Runtime 管理
  info/thread/plc/                新增 PLC 周期任务及启动协调
  info/thread/core/               复用线程中心、历史保存、命令超时
  info/buffer/cleaning/           扩展现有实时中心与温缓冲
  info/buffer/command/            扩展现有命令登记聚合
  service/                       扩展现有 CloudCleaning/Command/Statistics 接口
  service/impl/                  扩展对应实现
  repository/cleaning/            复用业务 DAO；本地配置 DAO 按已有约定放置
  domain/bo/                     状态、测量、轮次和写入业务结果
  domain/entity/cleaning/         数据库存储对象
```

用户操作仍然是“网络 Web → Web 表示层 → 大厅 → 安全／清洗／命令服务”。设备采集另走“网络设备层 → PLC 表示层 → Runtime → 设备缓冲 → 清洗业务周期”，不把每次 PLC 采样变成 HTTP 请求，也不绕大厅权限执行用户操作。

不新增一组 `ITensionSource/RealtimeSink/HistorySink` 等每功能接口。优先扩展现有聚合服务接口和具体中心；确需替换整个设备服务实现时，才考虑该服务的完整接口，不为单个方法创建接口类。

## 4. 从读 PLC 到页面的具体调用

以下为拟实现的调用顺序，新增方法需在实施阶段补代码。

1. `PlcDeviceTasks.runDeviceCycle()` 从有界队列取急停等到期命令，然后调用 `PlcDeviceService.pollDueDevices()`。
2. `ModbusTcpDevice.poll()` 按配置读 m/d 对应的协议地址，返回 `PlcReadResultDto`。网络层验证事务号、设备地址、功能码、异常响应、长度和超时。
3. `PlcDevicePresentation.onReadResult()` 将结果归一化为 `DeviceDataBatchBo`，交 `DeviceRuntimeManager.updateDeviceData()`。
4. `PlcDeviceRuntime` 保留每个点的值、质量、采集时间和来源，不凭通信成功产生“清洗已启动”。
5. `PlcDeviceTasks.publishRuntimeSnapshots()` 将不可变 `DeviceRuntimeSnapshotBo` 发布到 `CloudRuntimeDataCenter.updateDeviceSnapshot()`；离散事件进入独立有界事件队列。
6. `CleaningRuntimeTask` 调用 `CloudCleaningService.runDeviceCycle()`：取本系统绑定的快照／事件，消费写结果，再调用 `processDeviceSnapshots()`。
7. `CleaningDeviceConverter.toMeasurementFrame()` 按业务测点映射构建 `CleaningMeasurementFrameBo`。对象构造／校验负责有效性，服务补充状态、均值和会话归属。
8. `InnerWallCleaningState.observe()` 统一管理系统状态；`CloudCleaningService` 观察会话／轮次并得到业务实时快照。
9. 调用 `CloudStatisticsService.acceptSample()` 更新现有热缓冲，同时把需按顺序整理的轮次事件与快照交历史整理入口。
10. HTTP 或页面推送读取业务热数据，经现有 VO 转换返回，不读原始 Runtime，不让前端计算状态、单位或均值。

网络层不知道“平均拉力”“第三轮清洗”等业务含义；大厅不解析 m/d、字节序或 Modbus ADU；采样构造器不创建线程、不访问数据库。

## 5. 拟新增及扩展类的方法清单

### 5.1 技术采集与表示层

| 类及落点 | 主要方法 | 负责什么 |
|---|---|---|
| 新增 `PlcDeviceConfigDao`，数据层 | `loadEnabledDevices()`、`loadPointBindings()`、`loadCommandBindings()` | 聚合加载本地点位配置；不按一个点拆一个 DAO |
| 新增 `PlcDeviceService`，网络层 | `initialize()`、`pollDueDevices()`、`dispatchWrite()`、`shutdown()` | 管理连接、到期采集、超时退避、请求响应校验 |
| 新增 `ModbusTcpDevice`，网络层 | `buildReadBlocks()`、`poll()`、`write()`、`close()` | 单 PLC 会话，读写串行化；编码解码能力集中在该模块 |
| 新增 `PlcDevicePresentation`，表示层 | `onReadResult()`、`onConnectionChanged()`、`onWriteResult()` | 协议结果→已解码测点或写结果；不直接组页面 VO |
| 新增 `DeviceRuntimeManager`，`info/device` | `initialize()`、`updateDeviceData()`、`snapshotAll()`、`findRuntime()` | 管理一份本地设备实例关系及 Runtime 生命周期 |
| 新增 `PlcDeviceRuntime`，`info/device` | `updateDeviceData()`、`snapshot()`、`buildWriteRequest()` | 保存真实测点；按点位权限／类型构建下行，不直接连 Socket |
| 新增 `PlcDeviceTasks`，`info/thread/plc` | `start()`、`runDeviceCycle()`、`publishRuntimeSnapshots()`、`stop()` | 登记技术任务，不承载清洗业务 |

协议底层可采用合适的 Java Modbus 实现，但当前 `pom.xml` 尚无专门 Modbus 依赖。本方案不指定未经验证的库／版本，不新引入第二套业务框架。后续在该网络模块内确定实现，外层方法和业务对象不随库变化。

### 5.2 业务和已有中心

| 类 | 保留／拟补方法 | 实施要求 |
|---|---|---|
| `CloudCleaningService` 及实现 | 保留 `emergencyStop/updateSettings/latest/settings/operation`；拟补 `initialize/runDeviceCycle/processDeviceSnapshots` | 拥有状态、轮次、业务转换及控制聚合；不手写 Socket 或线程 |
| 新增 `InnerWallCleaningState`，业务对象 | `observe()`、`observeOffline()`、`snapshot()` | 用统一入口观察事实，避免控制方法和观察方法重复成两套状态机 |
| 新增 `CleaningDeviceConverter`，业务内部转换 | `toMeasurementFrame()` | Runtime 点位→已归一化测量／事实，不接 HTTP/MQTT |
| `CloudCommandService` 及实现 | 保留 `submit/find/runTimeoutCycle`；拟补 `enqueueDeviceWrites/onLocalWriteResult` | `submit` 原子登记业务 Message；清洗单写者处理后生成 PLC 子消息，没有 MQTT 分支 |
| `CloudCommandRegistry` | 复用登记；拟补 `offerDeviceWrite/pollDeviceWrite/offerWriteResult/takeWriteResults` | 聚合管理有界优先队列、写结果、命令关联；网络等待不能持有整个中心锁 |
| `CloudRuntimeDataCenter` | 保留热数据查询；拟补 `updateDeviceSnapshot/deviceSnapshots/offerDeviceEvent/takeDeviceEvents` | 原始设备快照与业务热数据分区保存，事件不能被最新值覆盖 |
| `CloudStatisticsService` 及实现 | 保留 `acceptSample/acceptHistoryBatch/runRealtimePersistenceCycle`；拟补 `acceptCleaningInput/runTelemetryReductionCycle` | 热数据与历史输入分别处理；不另建同职责上传服务 |
| 新增 `CleaningTelemetryReducer`，统计业务内部 | `beginSession/beginRound/process/finishRound/abortRound/endSession` | 聚合管理历史留点基准、行程段和轮次输入 |
| 新增 `CleaningRuntimeTask/TelemetryReductionTask`，`info/thread/core` | `start/stop` | 分别登记清洗观察和历史整理周期，调用业务方法 |

这些是类级职责，不要求每个方法成为新的公开接口。控制中多个参数写入结果的聚合由清洗服务拥有的控制对象或私有处理完成；网络层只提供每条请求的写结果。

## 6. 数据对象和配置怎么对应

### 6.1 两层映射

底层配置回答“读哪里、怎么解码”：连接 IP/端口、unitId、符号名、实际协议地址、功能码、类型、寄存器长度、字节序、原始倍率、无效码规则、读写权限。

业务映射回答“这个值是谁的、代表什么”：`cleaningSystemId`、`winchId/ropeId/sensorId`、槽位、业务字段、单位换算和状态解释。由 `CleaningDeviceDao` 查询现有设备关系；业务设置与配置版本从相应业务 DAO 加载。

`m7/d40` 可以作为可读的符号标签，大小写统一保存，但不能假定它们的 Modbus 地址就是 7/40。必须由 PLC 厂家映射确认地址和功能码。当前盒子读取地址解析不支持直接将 m/d 字符串作为协议地址，不能照搬该限制并宣称已支持。

本地连接与寄存器配置不由页面用户随意修改，不要求经 MQTT 下发，也不混入清洗三参数设置。未确认点位保持禁用／未绑定，不能猜地址。

### 6.2 业务字段口径

| 字段组 | 源数据与目标 |
|---|---|
| 8 路索拉力 | 第二版文档 `d40～d47` 标注 N；业务原模型 kg 口径必须明确为负载换算值／kgf，确认换算后只执行一次 |
| 4 路本机高度 | `d80～d83` 标注 cm，换为 m；核对四个零点、方向和范围 |
| 圆环整体高度 | 单独保留来源和有效标志；文档未给明确整体点位，不能默认为任一台高度或四台平均 |
| 速度 | `d61～d64` 是单独运行显示，`d65` 是同步速度；按第二版 cm/s 口径转换，不能照第一版单位；不能把同步值冒充四台实测 |
| 4 路转矩 | 文档未给正式点位；缺失为无效／空值，不阻断已有实时数据，不能把 N·m 字段改名 kg 就当完成换算 |
| 状态／方向 | `m10/m7/m2/m3/d3/m20～m37` 等确认后独立映射，不按测量速度猜状态 |
| 运行异常 | `m40` 仅明确运行异常，不等于已确认拉力偏离 20%；异常细分及告警内容后续再确定 |
| 时间 | 每点采集时间、帧序号、状态版本、进程采集代号；没有 PLC 时钟点位时不得冒称为 PLC 自带时间 |

`TelemetrySample` 当前仍有 `int state`，`WinchSample` 使用 `torqueNewtonMeters`，没有完整承接盒子五状态／有效性模型。优先扩展现有 BO/VO 和转换器，旧 MQTT DTO 单独兼容，不再创建第二份实时权威模型。

实时对象允许没有自动作业 `sessionId`。采集流身份与清洗会话身份分开：待机／检修也有实时数据，但不伪造自动作业。`CloudRuntimeDataCenter` 现有按 session+sequence 去重逻辑需增加进程流代号和状态版本，防止重启序号归零、同序号状态更新被丢弃。

保留数据库现有 `boxId` 作为兼容来源关联键，明确不代表现场仍有实体盒子；实际设备归属以 `cleaningSystemId` 与 PLC 本地绑定确认。不得凭空生成新业务 ID、复用冲突 ID 或清空既有外键。本地模式长期重命名／移除 boxId 另做数据库与 API 迁移，不在本次实施中顺带全局替换。

## 7. 系统状态和一次清洗如何判断

目标系统和单台动力设备均展示“待机、运行、检修、故障、离线”。手动模式统一显示检修，不论是否运动；方向／正在运动另有字段，不把手动上升改成自动运行。

自动模式无正在执行流程为待机；自动流程执行中为运行，途中等待不因速度为零自动变待机。系统故障、急停与本机电机／编码器故障分别保留原因，不将某台故障复制成四台同一原因。状态无效与历史故障并存时需明确“当前离线、最后已知故障仍保留”，不能清除故障事实，也不能用通信成功替代有效状态。

使用 `m10` 模式、`m7` 自动运行、`m2/m3/d3` 急停／故障及单机相关状态；`m13` 是自动已启动／使能事实，不等于每次清洗启动。`m6` 文档仍表示中断，不能因此推导为急停或新增服务器暂停按钮。`m0` 控制电源状态与无法通信不是同一事实，映射到五状态前需现场确认。

清洗会话表示连续自动作业，轮次表示从原起始端到另一端再返回：顶→底→顶或底→顶→底。`m8` 正常返回原位可作为重要完成事实，`m9` 是下降到位；`d10/d11` 是单向完成次数，不能简单相加或除二就算完整轮次。

自恢复往返仍属于原轮次；恢复期间不因 `m40` 或换向就直接判最终故障。最终故障以 PLC 确认事实为准，不由服务器擅自计五次运动推断。切换手动后记录自动轮次中断，检修运行另外存，不混入正常自动曲线。

`m8/m9` 保持 1 秒：Runtime 和快照刷新会覆盖旧值。需要在每次采集更新时锁存已观察到的边沿并保留事件队列；即便如此，采集本身漏过脉冲仍无法补回。现场必须进一步确认累计计数、事件序号或锁存确认机制，断线后不能只靠最后一个布尔值重建全部轮次。

## 8. 热数据和 0.5 米历史的本地闭环

```text
清洗业务实时快照
  ├─ CloudStatisticsService.acceptSample → CloudRuntimeDataCenter 热数据
  └─ acceptCleaningInput → 有序历史输入 → CleaningTelemetryReducer
       → TelemetryBatchBo → CloudWarmBuffer
       → TelemetryHistoryPersistenceTask
       → CloudStatisticsService.runRealtimePersistenceCycle
       → TelemetryHistoryDao.insertBatch → MySQL 提交
```

本地链路不需要等待 MQTT Broker ACK 或伪造盒子业务 ACK。数据库事务提交才说明该历史批次已保存；本阶段不预建远程同步链路。

留点规则：

- 正常自动上行：以本行程段上一次普通历史留点的真实高度为基准，累计移动至少约 0.5m 保存当前真实采样。
- 首点、末点、流程节点额外保存；不能沿用盒子当前“首帧不输出”。
- 正常下行不生成连续普通测量曲线，但保留流程开始／到位／换向事件。
- 下降异常保存事件及位置点；持续异常是否按 0.5m 追加由后续告警需求确认，不先编造完整下降异常曲线。
- 异常开始／结束立即留事件和位置点，不等待 0.5m；没有恢复事实不能填“已恢复”。
- 异常插入点不随意重置普通留点基准，换向／新行程段重新建立基准。
- 恢复期间经过相同高度不直接去重，按时间、方向、行程段及事件关联保存。
- 未采到整 0.5m 网格时保存真实位置，不插值伪装成实测；无有效整体高度不生成普通位置触发点。

历史一条轮次包含概要、测量点、流程事件；显示“系统自动执行”、今日第几次、开始／结束时间。异常数据结构预留起止和关联，详细告警文案及处理流程后续实施，不在这里重新定义全部告警码。

每点记录 8 路拉力、4 路速度、4 路转矩换算值及其有效性，整体方向／高度、时间和留点原因。速度随点保存，不另造速度留点系统。整体平均要求 8 路有效、本机平均要求两索有效，部分可用值需标明覆盖数量，不能让缺失零值参与平均。

稀疏点平均仅是留点平均。如果需要全程最大值或时间平均，要在原始采样过程中累积并保留统计口径；不能将上行稀疏点最大值宣称为完整清洗全程最大值。月／年曲线、同比环比仍按现有统计接口后续实施。

当前历史 BO/Entity 还缺一些事件、状态、质量和身份字段；实施时对照现有 V1 表和测量表逐项扩展，不为每页、每台、每传感器单独建新表。点记录可沿用 segment 表，单点的起止时间／高度相同，不宣称为真实区间统计。

不能依赖现有“数据库失败退回内存队列”承诺不丢数据：`CloudWarmBuffer` 回队时仍可能满，进程退出也会丢内存。必须先明确在途容量预留；若要求数据库故障／重启后补写，再补持久化待保存日志及重放机制。普通 3～4 天保留策略与未确认待写数据清理分开。第一阶段不能承诺停机零丢失，也不将全部 500ms 帧无差别长期入库。

## 9. 急停和配置修改下行

### 9.1 共用用户调用链

```text
CleaningWebService
  → CloudCleaningWebPresentation／Converter
  → CloudLobbyService
  → CloudSafetyService
  → CloudCleaningService
  → CloudCommandService.submit（登记操作 Message，HTTP 不等待 PLC）
  → 清洗单写者消费操作 Message、复核版本及在途修改
  → CloudCleaningService 控制对象／PlcDeviceRuntime 构建写入
  → CloudCommandService.enqueueDeviceWrites
  → CloudCommandRegistry 有界本地下行队列
  → PlcDeviceTasks／PlcDeviceService
  → ModbusTcpDevice → PLC
  → 网络层验证写响应
  → PlcDevicePresentation.onWriteResult
  → 写结果缓冲 → CloudCleaningService 消费并聚合
  → CloudCommandService.onLocalWriteResult → 审计／查询结果
```

清洗服务决定操作内容并拥有其上下文；`PlcDeviceRuntime.buildWriteRequest()` 完成点位权限、类型、范围和编码映射；命令服务关联请求和消息、选择路由。网络层不查用户密码、不保存生效参数；业务层不比较报文。

每次操作继续校验当前账号、权限、设备资源归属、requestId、版本和参数，不因部署本地取消安全检查。当前安全服务只包含部分账号／编号／权限检查；设备归属及修改参数密码复核需按现有用户服务认证能力补齐，不能声称已完成，也不把密码存入命令 payload 或日志。

### 9.2 写结果不是设备运行事实

- 入队：只说明请求已提交。
- 网络写响应有效：说明对应协议写操作被确认，返回 `messageId/requestId/commandId`、时间和结果。
- 急停实际状态：继续由后续采集的急停事实确定；不得拿写响应生成“设备已经停止”。
- 参数写入：三个写请求全部确认后才登记写入确认版本和审计；仅部分确认显示部分写入，不保存完整成功，也不擅自回滚已写点。
- 参数“已运行生效”：需要 PLC 约定的生效规则或反馈；只有写响应时不要冒称执行效果已确认。申请值、确认写入值、采集实际值分别保留。

本地结果不使用 `BROKER_ACKNOWLEDGED`，也不伪造 `boxCommandId`。复用已有命令状态并明确 `ACCEPTED/RUNNING/SUCCEEDED/UNCONFIRMED` 的本地语义，增加本地结果入口，而不是构造假的 MQTT `CommandAck`。

FC05/FC06 的正常写响应可按请求回显核对；如实际参数是 32 位或多寄存器并需 FC16，则响应返回起始地址和数量，不是完整请求回显，必须按功能码验证。不能把盒子当前单写的“响应等于请求”作为所有操作的通用规则。[Modbus 官方应用协议](https://modbus.org/docs/Modbus_Application_Protocol_V1_1b3.pdf)

同 PLC 连接的读写必须串行或由底层正确关联事务，不允许两个业务线程直接争抢 Socket。超时写入可能已经到 PLC，不能自动当作未执行或无限重发；急停、参数写的重试规则分别现场确认，重启后过期命令不自动发送。

急停进入高优先队列，但普通优先队列不能中断已经等待的 Socket 读。需要有限读等待预算、逐块检查优先队列、过期清理和现场急停兜底，不承诺零延迟，也不以网络急停替代现场安全装置。

### 9.3 三项参数与 PLC 点位

用户设置仍只有启动时间、运行间隔、整组速度（最高 10m/min）。第二版给出 `d100/d101/d102`，但没有明确小时分钟编码、间隔单位、速度倍率、类型／宽度和生效时机，确认后才能实际写入。`d103` 中转时间不是当前用户设置项，不自动加入页面或命令。

服务端目前尚未完整校验 10m/min 上限，设置查询还是未同步；需要接实际配置 DAO／版本。挡位只有在现场提供挡位与速度映射后才换算，不猜速度，不用 `speedLevel` 填充来绕过参数校验。

## 10. 后台任务与线程

本节列逻辑任务；修订 2 的具体初始物理线程方案为单 PLC I/O、清洗单写者、历史整理、持久化四条专用通道，统一由 ThreadCenter 管理。快照发布并入 I/O，超时检查并入清洗观察，不额外为每个任务、设备或用户建线程。详见 Message／并发专题的线程和锁表。

任务数不等于线程数。沿用 `BackgroundWorkerRegistrar`，业务服务只暴露周期方法，不自行 `new Thread`。

| 逻辑任务 | 调用 | 建议初始节奏及说明 |
|---|---|---|
| PLC 读写 | `PlcDeviceTasks.runDeviceCycle` | 目标采集 500ms；命令入队应唤醒或采用更短队列检查节奏，不为每个 PLC 独立创建失控线程 |
| Runtime 发布 | `publishRuntimeSnapshots` | 与新采集序号关联，可每轮有效更新后发布；不得固定 1000ms 再丢掉中间事件 |
| 清洗观察 | `CleaningRuntimeTask → runDeviceCycle` | 可从 100～500ms 起，消费序号及事件；实际周期不是现场实时保证 |
| 历史整理 | `TelemetryReductionTask → runTelemetryReductionCycle` | 初始 50～100ms，有限批次，保持生命周期与测量输入顺序 |
| 历史保存 | 现有 `TelemetryHistoryPersistenceTask` | 默认配置 2000ms，可调；事务成功确认，失败保留 |
| 命令超时 | 现有 `CloudCommandTimeoutTask` | 当前 1 秒；本地模式改为本地写结果语义，不等盒子回执 |
| 页面查询／推送 | 现有网络能力，后续接线 | 读热缓冲，不挤占 PLC 会话；没有 MQTT 上传任务 |

现有 `ThreadCenter` 是固定两线程 `scheduleWithFixedDelay`。一个阻塞 PLC 请求加一个数据库请求就可能拖慢其他任务；“每 500ms 注册”也不等于实际 500ms 采到一次。

实施前必须单独落实最小的线程中心能力调整：技术 PLC I/O 与业务／数据库任务隔离、有限队列、停止等待及配置化容量，仍由现有线程中心统一管理。可优先采用网络模块异步 I/O；若需专用有界执行通道，应先说明影响再修改框架能力。不能在业务服务偷偷创建线程，也不能仅增加几个调度线程就声称隔离完成。多 PLC 的超时隔离与扩容放下一批验证。

## 11. 初始化和停止顺序

不能依赖各类独立 `@PostConstruct` 的偶然顺序启动所有任务。新增 `LocalPlcLifecycle` 放 `info/thread/plc`，统一使用 Spring 生命周期控制本地采集模块，保留应用 `Application.main()`。

启动顺序：

1. 加载本地接入模式、PLC 定义、读写点位和任务策略，检查未知点位禁用、重复映射、只读／可写范围。
2. 从既有 DAO 加载系统与四台绞机／八传感器关系、用户、三参数确认版本及业务策略。
3. 初始化现有中心、队列、命令关联与设备 Runtime；清洗服务创建并拥有状态／轮次／控制对象。
4. 初始化统计整理器和历史保存入口；验证 DAO Bean、数据库与配置，不自动执行整份建表脚本。
5. 启动历史消费者和清洗观察，再启用 PLC 采集／快照发布；对每个运行方法保留就绪门禁。
6. 设备暂时不可达时可启动 Web 诊断与重连，显示离线；配置无效应禁止该设备实际读写，不能把两者都判为服务正常。
7. 不因服务启动发送 PLC 自动启动、解除急停或重放过期命令；中途接入自动作业标记观察不完整，不伪造其开始时间。

停止顺序：停止接受新写命令 → 停 PLC 生产并等待在途请求有限退出 → 排空已读事件／清洗输入 → 刷新历史末点并有限保存 → 关闭 PLC 连接和任务。共享 ThreadCenter 的全局 shutdown 最后执行。取消周期任务不代表在途线程已经退出，必须有明确等待／超时处理。

数据库不可用、可靠暂存未接通时应明确降级和容量限制，不能继续无限采集并宣称所有历史已经保存。

## 12. 删除 MQTT 与旧路线

目标只有现场服务器直连 PLC，不设计 `MQTT_BOX` 回退分支。实施时删除 `network_access_layer/mqtt`、`presentation/mqtt`、`dto/mqtt`、`info/buffer/mqtt`、MQTT 配置与 Broker Bean、相应 Maven 依赖及专属测试；旧清洗／命令／大厅服务和旧 RuntimeDataCenter 按删除清单同步移除。

当前新主命令服务仍依赖 MQTT 转换器／队列，直接只删网络类会导致 Java 引用与 Bean 装配失效。因此“删除”必须与主服务改为内部 Message、清除旧回执入口、补 PLC 接入同时作为一个完整实施批次，不是只隐藏类，也不是随便返回成功占位。

保留 `Cloud*` 主业务类、用户认证、Web、数据库和真实热／温中心。`boxId` 暂存为现有数据库／API 兼容键，不代表保留实体盒子或 MQTT。旧资料只描述原部署，不作为最新功能设计；本次不删除文档。完整文件范围、保留项和删除检查见删除说明专题。

## 13. 分批实施与验收

| 批次 | 范围 | 验收标准 |
|---|---|---|
| A 依赖与模式 | 现有类、配置 DAO、设备身份、启动门禁、线程能力评审 | 能说明每个 Bean 谁拥有，未配置 PLC 不产生假数据；不修改既有 API 主入口 |
| B 只读 PLC | 网络设备／表示层／Runtime／设备快照／事件 | 用测试会话验证读响应、超时、断线重连、质量、m/d 地址映射；真实点位未确认不启用写 |
| C 实时业务 | 五状态、方向、单位、有效性、热缓冲、现有 VO | 待机／运行／检修不同；缺失不补零；运行会话空也可展示；重启和状态变化不被去重丢掉 |
| D 本地下行 | HTTP→大厅→安全→清洗→命令→PLC→写结果 | 先用模拟写响应证明关联、拒绝、超时、急停优先及三参数部分成功；现场安全确认后才真实写 |
| E 清洗历史 | 会话／轮次、有序输入、0.5m、温缓冲、DAO | 缓慢上行能累计留点；正常下行不保存普通曲线；恢复不重复计轮；首末点和异常边界可关联 |
| F 可靠性与清理验收 | 数据库故障、满队列、停止重启、采集独占、MQTT／旧路线删除 | 无 MQTT 引用／Bean／任务，不依赖盒子 ACK，不重复写 PLC；不能静默丢未保存数据；未满足可靠暂存目标明确标记限制 |

具体测试场景：

- 高度连续 10.00/10.05/10.10…10.50m，应在累计达到阈值时留点，不要求相邻两帧超过 0.5m。
- 第一次有效上行采样保留首点；结束不到下一个 0.5m 也保留末点。
- 相同高度在恢复下行／上行再次出现，行程段与方向保留，不折叠成同一点。
- 手动单台上升显示检修及实际方向，不创建自动轮次。
- `m40` 开始／结束保留运行异常事实，不擅自标成特定拉力告警或最终故障。
- 普通读取无响应时急停队列不能无限等待；网络写确认与实际急停状态不混淆。
- 三参数有一个超时，不能返回全部成功、不能把申请版本写成实际运行生效版本。
- 数据库断开、缓冲满、关闭程序及 PLC 重连，分别检查未提交记录、在途结果、过期命令和脉冲丢失风险。

第一批建议先从只读采集到热缓冲，第二批接急停与三参数写入，第三批接轮次／历史。不要先实现月年统计，也不要同时迁移所有旧类或创建新目录体系。

## 14. 仍需现场确认和本次交付边界

现场确认清单：PLC IP/端口、unitId、m/d 实际 Modbus 映射、类型与宽度、字节序／倍率／无效码、四台高度零点及整体高度来源、同步／单机速度口径、转矩点位、最终故障码表、运行异常复位、脉冲／计数可靠读取、参数编码和生效规则、可写急停点及安全联调安排。

本方案不假定“读取 m/d 已经可用”，不启用任何真实写点，不执行数据库脚本，不选择未核验依赖，不编译两个项目。后续代码按上述批次实现；文档中的新增类／方法是实施清单，不是当前功能完成证明。

### 源码和既有设计依据

- 盒子 [采集与业务说明](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/01_PLC_DATA_ACQUISITION.md)、[测点添加说明](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/02_ADD_MEASUREMENT_GUIDE.md)、[原 MQTT 路径](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/03_MQTT_UPLOAD_AND_CLOUD_BUFFER.md)。这些描述原部署，并未因本方案自动失效。
- [盒子设备协调器](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/src/network_access_layer/presentation/device_control_coordinator/device_control_coordinator.cpp)、[Runtime](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/src/data_layer/device_manager/device_runtime.cpp)、[设备快照发布](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/src/business_layer/timer_scheduler/device_scheduler.cpp)、[历史整理现状](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/src/business_layer/telemetry/telemetry_service.cpp)。
- [服务端大厅](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/service/impl/CloudLobbyServiceImpl.java)、[清洗服务](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/service/impl/CloudCleaningServiceImpl.java)、[命令服务](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/service/impl/CloudCommandServiceImpl.java)、[统计服务](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/service/impl/CloudStatisticsServiceImpl.java)。
- [实时中心](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/info/buffer/cleaning/CloudRuntimeDataCenter.java)、[历史温缓冲](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/info/buffer/cleaning/CloudWarmBuffer.java)、[线程中心](D:/javaweb/all/SmartWashCloud-Server/src/main/java/com/company/project/info/thread/core/ThreadCenter.java)。
- [服务端分层与数据层约定](D:/javaweb/all/SmartWashCloud-Server/docs/CLEANING_SERVER_DEVELOPMENT_CONVENTIONS.md)、[历史存储说明](D:/javaweb/all/SmartWashCloud-Server/docs/database/CLEANING_HISTORY_STORAGE_FLOW.md)、[数据库 V1 基线](D:/javaweb/all/SmartWashCloud-Server/docs/database/README.md)。本地直连的最新作业来源采用“系统自动执行”，下降异常连续留点仍待确定，不机械继承旧文档更宽的假设。
