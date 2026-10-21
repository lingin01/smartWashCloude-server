# 原服务端 Modbus TCP 接入实施说明

日期：2026-10-15。实施项目：`D:\javaweb\all\SmartWashCloud-Server`。同级 `SmartWashLocal-Server` 副本不修改。

## 1. 本轮完成范围与限制

删除 MQTT 后，原服务端改用 Modbus TCP 客户端，现场 PLC 是服务端。实现配置驱动读点、设备 Runtime、业务实时转换、热／温缓冲、急停及三参数写入消息、协议响应校验、结果回流和线程生命周期。复用现有 Spring Boot、JDK、ThreadCenter、CloudLobbyService、CloudCleaningService、CloudCommandService；不增加外部通信库。

默认 `cleaning.plc.enabled=false`、设备关闭、写入关闭，未知点位地址为空且禁用。启动默认配置不会连接 PLC。所有模拟地址只出现在测试中，不能复制到现场。

这不表示全部清洗业务完成：

- 自动会话／轮次识别、顶部／底部闭环、PLC 自恢复五次最终故障处理尚未接入。
- 正常上行每累计 0.5m 留历史点、下降异常片段、异常开始／结束尚未接入本采集链路。
- 配置版本 DAO、配置审计、命令审计／重启恢复尚未接入。配置写入成功后保留“已写入但未持久化”的状态，不假报整个操作成功。
- 告警事件归档、统计曲线、WebSocket 推送没有在本轮新增。
- 现场地址、类型、倍率、无效值、极性、下行编码需要确认；未访问 PLC 或数据库，未启动正式 Spring 服务。

## 2. 用户操作到 PLC

```text
HTTP 请求线程
→ CleaningWebService
→ CloudCleaningWebPresentation / CloudCleaningWebConverter（DTO→Param）
→ CloudLobbyService（用户鉴权与权限）
→ CloudSafetyService（操作检查）
→ CloudCleaningService（急停／三个业务参数）
→ CloudCommandService（登记、幂等、有效期）
→ PlcCommandConverter.prepare（业务对象→点位写入对象）
→ PlcMessageBuffer.offer（有界 Message 队列）
→ PlcDeviceService.runCycle（专用 I/O 线程消费）
→ ModbusTcpDevice.write
→ ModbusTcpClient.exchange
→ PLC
```

HTTP 线程只登记和非阻塞入队，不等待 PLC 响应，不为每个用户创建 PLC 会话或工作线程。现有 API 返回 QUEUED 表示已进入队列，不表示发送成功。

仅提供急停及修改参数，不提供启动、解除急停、暂停／恢复。自动启动仍由 PLC 负责。三参数是开始时间、运行间隔、整组运行速度；速度必须为明确的 m/min 数值且大于 0、不超过 10，挡位映射未知时不能猜。

## 3. 写结果如何回来

```text
PLC 返回报文
→ ModbusTcpClient（帧长度、事务号、单元号、协议号／功能码关联）
→ ModbusTcpCodec.checkWrite（写地址／写值／数量校验）
→ PlcOperationResultBo
→ PlcMessageBuffer.offerResult
→ PlcDeviceTasks.dispatchCycle（专用分发线程）
→ PlcDevicePresentation.onWriteResult
→ CloudCommandService.acceptDeviceResult → CloudCommandRegistry
→ CloudCleaningService.acceptDeviceWriteResult
→ 用户查询 operation 获得最新结果
```

FC05／FC06 的成功响应是写请求回显；FC16 只返回起始地址和数量，不能用完整报文相等判断。校验规则依据 [Modbus 官方协议规范](https://www.modbus.org/modbus-specifications)。

急停写响应已确认时，命令可标 SUCCEEDED，代码 `PLC_EMERGENCY_WRITE_CONFIRMED`，其严格含义是“急停请求已写入”，不能据此修改采集到的实际设备状态或宣称设备已停止。实时是否急停必须从配置好的上行状态点确认。

参数三项可能分三次写入，不保证 PLC 原子生效。全部回显确认后，命令保持 UNCONFIRMED／`PLC_SETTINGS_WRITTEN_STORAGE_PENDING`，配置查询为 WRITTEN_NOT_PERSISTED，版本为空。同设备新参数请求继续被阻止，直到后续配置 DAO 与核对闭环完成。若现场需要整组原子更新，应另确认 PLC 缓冲寄存器、提交点及握手，不自行虚构。

PLC 明确异常响应且没有已确认子写入可标 FAILED；超时、断线、部分写入标 UNCONFIRMED。不能把超时当“肯定未执行”，不自动重发写命令或回滚现场参数。急停可以打断参数组后续未发送子项，但不能撤销已经写入的子项。

## 4. PLC 数据到系统实时缓冲

```text
PlcDeviceTasks 的 I/O 工作者
→ PlcDeviceService.runCycle
→ ModbusTcpDevice.poll（默认 500ms 轮询，连续同功能码地址合并读取）
→ ModbusTcpClient / ModbusTcpCodec（协议解码、倍率、无效码）
→ PlcReadSnapshotDto → PlcMessageBuffer.offerSnapshot
→ PlcDeviceTasks.dispatchCycle
→ PlcDevicePresentation.onSnapshot
→ PlcDeviceRuntime.update（不可变设备事实）
→ CloudCleaningService.acceptDeviceSnapshot
→ CleaningDeviceConverter.toRealtime（设备事实→业务状态、均值）
→ CloudRuntimeDataCenter.updateLatest（热最新帧＋既有有界温缓冲）
→ 现有大厅／HTTP 查询返回业务 BO，经表示层转换成 VO
```

设备采集 DTO 的物理协议字段不暴露给用户。设备表示层是内部采集入口，不把 PLC JSON 交给大厅解析；用户入口仍经过大厅。Runtime 不创建清洗任务、不发送控制。前端不计算均值或做原始单位换算。

消息中心的最新帧按设备合并，因此它只服务实时监测；不能把该通道当作无损历史事件队列。既有温缓冲最多每设备 10,000 帧并移除旧帧，不是完整单轮记录，也不是数据库可靠历史。本轮没有把温缓冲自动送入历史 DAO，不能宣称历史落库闭环已完成。

采集时间 `sampledAt` 是服务端本次采集完成时间，不假装是 PLC 自带时间戳。序号用于本进程快照排序，不是可跨重启重放的历史 streamId。

## 5. 业务字段及状态

| 采集配置 field | 槽位 | 业务用途／单位 |
|---|---|---|
| manualMode | 1 | 手动模式显示检修；不论是否移动 |
| running | 1 | 自动模式下运行／待机区分 |
| emergency / fault / faultCode | 1 | 急停、最终设备故障事实／原故障码 |
| up / down | 1 | UP／DOWN／STOP／UNKNOWN；相互矛盾不猜方向 |
| ropeTension | 1～8 | 8 路索拉力，转换后 kg；绑定真实 ropeId／sensorId／winchId |
| winchHeight | 1～4 | 四台高度，m |
| winchSpeed | 1～4 | 四台实测速度，m/min |
| winchTorque | 1～4 | 现场已换算的转矩力，kg；不是 Nm |
| winchRunning / winchFault / encoderFault | 1～4 | 可选独立本机事实，不能把一台故障复制成四台 |
| ringHeight / groupSpeed | 1 | 可选整体圆环高度及整体速度 |

每个绞机位置固定对应两个索槽：1 对 1/2，2 对 3/4，3 对 5/6，4 对 7/8。统一编号必须来自已有档案；同槽位归属一致，不同槽位不能重复编号，不生成演示编号代替真实身份。

系统／动力设备状态为 STANDBY（待机）、RUNNING（运行）、MAINTENANCE（检修）、FAULT（故障）、OFFLINE（离线），中文由后端 VO 转换提供。急停呈现故障，不新增暂停状态。自恢复不是最终故障；未映射自恢复字段时不根据拉力异常猜故障，最终故障只认已配置的设备事实。

缺失／无效值为 null 且 valid=false，不补零。两索平均必须两路均有效；系统总平均仅对有效通道计算，并给出 validTensionCount／validSpeedCount。圆环高度只认 ringHeight，不把任一绞机高度或四机均高伪装成圆环高度。整体 groupSpeed 与四机平均速度分别保留。Nm 兼容字段不填 kg 数值。

尚未识别轮次时 sessionId 为空、旧 state 为 -1，不把通信 RUNNING 或 m7 当作某个清洗阶段。预警／严重告警标志没有实现告警事件转换，不是已完成告警判断的依据。

## 6. 配置和新增测点

主开关在 `src/main/resources/application.yml`。完整禁用模板在 `src/main/resources/plc-points.example.yml`，它不会自动加载。确认后将 cleaning.plc 内容合并到正式 YAML，或由明确的 Spring 配置导入加载，不通过用户 HTTP 指定任意寄存器地址。

每个点提供：id、symbol、enabled、address、functionCode、dataType、byteOrder、scale、offset、invalidValue、field、index、unit／rawUnit、实际业务 ID；状态点有 activeValue；下行有 value／encoding。

- symbol 是小 m／小 d 的现场标签，不是协议地址。不能未经 PLC 的 Modbus 映射确认就使用 m10→10 或 d40→40。
- 功能码读 FC01／02 位、FC03／04 寄存器；写 FC05、06、16。不同区域须按 PLC 实际映射配置。
- dataType 支持 BOOLEAN、INT16、UINT16、INT32、UINT32、FLOAT32、FLOAT64；byteOrder 支持 ABCD、BADC、CDAB、DCBA（多字数据）。
- 解码为 raw×scale＋offset；下行反向换算。单位变换只做一次，倍率未知留空并保持禁用，禁止只改单位名称。
- BOOLEAN 位读取为 0／1，业务 activeValue 确认有效极性。急停下行点与上行急停状态点需分别核对。
- 开始时间 encoding 为 HHMM 或 MINUTES_OF_DAY；间隔 SECONDS 或 MILLISECONDS；整组速度 IDENTITY 后按倍率反向编码。
- configurationVersion 为人工核对的当前版本，不能自动按系统时间生成。当前版本未持久化闭环完成前，只能安全阻止后续配置覆盖。
- 模板中的 m／d 名称和原始单位只作既有文档参考；类型、地址、倍率、故障码含义仍以现场确认表为准。

新增采集点先在 points 中配置。不认识的 field 可以进入 Runtime 原始事实，但不会自动出现在页面；需要在 CleaningDeviceConverter 中映射，扩展业务对象与 VO。不得在主采集循环里到处添加寄存器特殊判断。

## 7. 线程、锁和生命周期

保留 ThreadCenter 现有共享调度池（2 线程）供历史持久化／命令超时等既有任务使用。本轮在 ThreadCenter 增加受托管的专用周期任务注册，不新增独立线程中心：

| 工作者 | 线程数 | 职责 |
|---|---:|---|
| plc-device-io | 1 | 串行 PLC 网络读写、响应校验、重连 |
| plc-device-dispatch | 1 | 结果消费、Runtime 更新、清洗转换、实时缓冲 |
| 既有共享调度池 | 2 | 既有后台任务，不参与阻塞 PLC I/O |

HTTP 线程数由现有 Web 容器管理，不计入上述 4 个后台线程。PLC 禁用不创建两条专用线程；启用时串行处理所有配置 PLC。每轮先最多消费 8 条下行消息，再轮询一个到期设备。增加许多 PLC 时需要重新评估吞吐，不保证所有设备都能精确 500ms。

一个 PLC 同一时间只有一个请求／响应交换。网络会话锁可以跨 I/O，但缓冲锁和命令登记锁只执行短内存操作，绝不持它们访问 PLC 或数据库。登记→非阻塞 offer 是唯一嵌套锁顺序；消费者取出消息先释放队列锁，再更新登记，避免反向等待。

同 requestId 同内容幂等；不同内容拒绝。同设备配置最多一个在途请求，版本不匹配拒绝。急停优先于普通参数消息。队列满时明确拒绝，不无界堆积；本阶段没有持久 Outbox，进程异常退出不能保证未完成消息恢复。

正在进行的单个协议交换不能由新急停瞬间抢占，等待上限受 timeoutMs 约束；系统端软件急停不替代现场硬件急停。断线读取退避 1/2/4/8 秒，单次连接／发送／收取共享截止时间，写命令不自动补发。

依赖构造：配置→消息中心／线程中心→设备服务、Runtime、转换器→命令／清洗服务→设备表示层→任务注册。对象构造不连接 PLC；设备初始化校验配置建立会话对象；任务启动后才采集。停机先停止收新消息、关闭连接，再取消专用工作者，未发送排队项返回 SERVICE_STOPPED。停机等待有上限，不宣称完成持久恢复。

## 8. 类与关键方法

| 类 | 关键方法 |
|---|---|
| PlcAccessProperties | validate、activeDeviceForBox、validatePoint |
| Message<T> | create；标识 PLC_OPERATION／PLC_WRITE_RESULT／DEVICE_SNAPSHOT |
| PlcCommandConverter | prepare |
| PlcMessageBuffer | offer、pollOperation、emergencyWaiting、offerResult、peekResult、completeResult、offerSnapshot、takeSnapshots |
| PlcDeviceService | initialize、runCycle、shutdown、cancelQueuedOperations |
| ModbusTcpDevice | poll、write、close |
| ModbusTcpClient | exchange、close |
| ModbusTcpCodec | read、write、readData、checkWrite、decode、encode |
| PlcDevicePresentation | onSnapshot、onWriteResult |
| PlcDeviceRuntime | update、snapshot |
| CleaningDeviceConverter | toRealtime |
| CloudCleaningServiceImpl | acceptDeviceSnapshot、acceptDeviceWriteResult、latest、settings |
| CloudCommandRegistry | registerAndOffer、applyDeviceResult |
| PlcDeviceTasks | start、dispatchCycle、stop |
| ThreadCenter | registerDedicatedPeriodic、unregister、shutdown |

## 9. 验证与启动注意

选定测试不加载 Spring／数据库，只绑定 127.0.0.1 的临时端口：ModbusTcpProtocolTest、PlcMessageFlowTest、CleaningLocalCommandReadinessTest。包括响应关联、FC05／06 回显、FC16 范围、异常帧、倍率／字节序、分包、超时、幂等、并发配置、急停优先、消息容量、缺失数据、三参数编码及大厅→模拟 PLC→命令结果／热缓冲整链路。

编译使用本地 JDK21、Maven，目标 Java17；原项目仍可能存在旧 target MQTT 二进制。实际发布必须 clean 后重新构建，不能运行残留 target/classes。此次不启动服务器、不执行 SQL、不调整数据库连接。测试结果仅证明模拟条件下代码路径成立，不代替现场联调和硬件安全验收。

验证命令：`mvn -o -B -ntp -Dtest=ModbusTcpProtocolTest,PlcMessageFlowTest,CleaningLocalCommandReadinessTest,LegacyCleaningRoutesTest clean test`。旧路由测试使用 MockMvc 内存模拟，不启动 HTTP 服务器。各测试报告随本次部署清单保留在 archive 备份目录中。

本地验证结果：2026-10-15 08:28（Asia/Shanghai）BUILD SUCCESS，20 项测试，0 失败、0 错误、0 跳过；其中协议 5、消息／线程／完整链路 8、原命令就绪 5、旧路由 2。未运行会访问数据库的 ApplicationTests。

修改原项目文件前按哈希核对，备份放原项目 archive/modbus-tcp-*；只部署本轮清单，保留用户无关改动及旧设计文档。旧 MQTT／盒子协议文档仅作历史资料，本轮链路以本文为准。
