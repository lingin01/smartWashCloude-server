# 现场服务器无用类与依赖删除说明

日期：2026-10-14。范围：`D:/javaweb/all/SmartWashCloud-Server` 当前活动源码。配套 [迁移主方案](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/04_LOCAL_SERVER_PLC_MIGRATION_PLAN.md) 和 [Message／线程／锁设计](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/05_INTERNAL_MESSAGE_FLOW_AND_CONCURRENCY_PLAN.md)。

2026-10-15 执行进度：已按用户要求在原服务端清理 MQTT 和旧空服务，详细范围、备份及未完成能力见 [执行记录](CLEANING_MQTT_REMOVAL_EXECUTION_20261015.md)。SmartWashLocal-Server 副本不修改。下文保留 2026-10-14 的删除设计和清理前引用说明，不代表 PLC／Message 全链路已经实现。

## 1. 删除原则和清理前已有引用

当前 `CloudCommandServiceImpl` 仍注入 `CleaningMqttOutboundConverter` 和 `MqttOutgoingMessageSink`，两个 Message/Broker 测试也构建这条链。只删除 `MqttService` 会造成未处理依赖，不能作为一次完整清理。

旧 `LobbyServiceImpl/InnerWallCleaningServiceImpl/CommandServiceImpl/RuntimeDataCenter` 大多是 TODO，当前真实 Web 主入口使用 CloudLobby/CloudCleaning。它们仍有 Spring Bean 和相互引用，整体移除，不只删除一个实现再保留注入方。

做法是同一实施批次内替换主命令服务的消息链路、移除旧服务组及专属类、删除 MQTT 配置／依赖／测试引用，静态检查后再进行用户允许的构建与启动验证。本阶段仍不编译、不启动、不连接 PLC。

不凭类名包含 cleaning/cloud 就判无用；已有用户、安全、Web、数据层、实体和真实业务中心必须保留。未来不实现的功能无需保留空框架，但删除其共用 BO 前必须检查真实引用。

## 2. MQTT 活动源码删除范围

下列包路径均相对 `src/main/java/com/company/project/`。当前核对数量仅用于检查完整性，后续源码变化需重新枚举，不用通配符直接递归删项目根。

| 包／文件 | 当前内容 | 删除原因与替代 |
|---|---|---|
| `network_access_layer/mqtt/`，12 个 Java 文件 | MqttService；PahoMqttTransport、IMqttTransport、MqttTransportListener；Topic 注册／匹配／方向／类型类 | 本地 PLC 由 network_access_layer/plc 管理，不需要 Broker 和 Topic |
| `presentation/mqtt/`，7 个文件 | CleaningMqttDispatcher、CleaningMqttConverter、Inbound/OutboundConverter、JacksonInboundConverter、RemoteCommandSigner、PendingRemoteCommandSigner | 本地读写结果用 PLC 表示层与强类型 Message，不走 JSON MQTT 转换或远程签名 |
| `dto/mqtt/`，3 个文件 | MqttProtocolMessage、MqttIncomingMessageDto、RemoteCommandEnvelopeDto | 新内部 Message 不含 mqttMeta；不能仅给旧对象改名而保留 MQTT 约束 |
| `info/buffer/mqtt/`，10 个文件 | 消息中心、收发 Source/Sink、入站 Envelope/Factory、DeliveryEvent、BufferOfferResult | 删除专属队列组，复用现有业务中心／命令登记并接内部有界消息队列 |
| `config/MqttConfig.java` | Broker、凭据、Topic 配置 | 不再有 MQTT 连接配置 |
| `config/EmbeddedMqttBroker.java` | 本地 Broker 自动启动 | 现场服务器不是内部自转发 Broker；释放对应启动、端口和停止逻辑 |

其中 `BufferOfferResult` 名字虽通用，但目前位于 MQTT 专属包；如新队列确需相同语义，应将通用入队结果定义在现有 info/common 适当位置，而不是为了一个名字保留整个 MQTT 模块。

此表活动源码共 34 个 Java 文件；不是“随便删四个目录”即可完成，还须处理下一节的主业务依赖。

## 3. 旧清洗路线整体删除

以下 8 个文件相对同一 Java 根目录：

1. `service/LobbyService.java`
2. `service/impl/LobbyServiceImpl.java`
3. `service/InnerWallCleaningService.java`
4. `service/impl/InnerWallCleaningServiceImpl.java`
5. `service/CommandService.java`
6. `service/impl/CommandServiceImpl.java`
7. `service/ICommandEventListener.java`
8. `info/buffer/cleaning/RuntimeDataCenter.java`

替代是现有 CloudLobby/CloudCleaning/CloudCommand/CloudRuntimeDataCenter 主路线，补齐 PLC、状态、轮次和 Message 能力。不能同时启动两套清洗服务修改同一设备。

`param/StartCleaningParam/EmergencyStopParam/SystemResetParam` 等旧专属 DTO/Param/VO/BO 是否无引用，需要在上述组移除后重新检索决定。当前 `CleaningController` 的旧路由说明仍引用旧 DTO 和结果 VO，所以不能直接把所有旧 DTO 删除而留下 Controller。

旧 `/api/cleaning/start/stop/reset` 目前只返回不支持或迁移提示，不是真实控制能力。保留这些兼容提示入口或彻底下线，由 API 清理单独明确；若保留提示 Controller，则保留它必要的输入／输出类。不能因为删除旧业务服务就误删仍用于兼容提示的类型。

## 4. 主路线保留，但移除 MQTT 专属方法与字段

| 保留类 | 要删／重写的内容 | 新路径 |
|---|---|---|
| `CloudCommandService/Impl` | MQTT converter/outgoing 注入、Broker 送达分支、等待盒子回执文字、MQTT Delivery 入口 | submit 登记 USER_OPERATION；enqueueDeviceWrites；onLocalWriteResult |
| `CloudLobbyService/Impl` | MQTT 专属 receiveTelemetry/history/alarm/commandAck/delivery 接入，按调用迁移后清除 | 用户 Web 入口保留；设备事实走 Runtime→清洗业务周期 |
| `CloudCommandRegistry` | applyReply 的盒子回执关联和 Broker 状态推进 | 原子消息登记、子写关联、本地写结果、版本审计 |
| `CloudCommandStatusBo` | BROKER_ACKNOWLEDGED 等专属状态；boxCommandId 无本地用途时移除或兼容过渡 | 状态基于本地受理、写入确认、保存结果；调整现有 VO 映射，不能靠 ordinal 比较新状态顺序 |
| `CloudRuntimeDataCenter` | 仅用于盒子回执的 ackMap/putAck/ack；已有 BOX 特判按新事实模型改 | 保留实时及设备快照／事件，不删除真实热缓冲 |
| `CloudCleaningServiceImpl` | 将操作转换为 MQTT Map payload、等待盒子设置同步 | 业务 Message、单写者配置仲裁、PLC 点位构建与版本查询 |
| `CloudStatisticsServiceImpl` | “只有盒子整理上传才能处理”的假设 | 保留实时／历史 DAO 入口，增加本地有序整理；本地 batchId 是保存批次，不是上传证明 |
| `CloudCommandTimeoutTask` | BOX_REPLY_TIMEOUT 语义和重复超时调度 | 合并到清洗单写者，按本地在途结果超时；不确定不能伪失败 |

`CommandDeliveryParam`、`CommandAck`、远程 source 枚举或字段不一定都能立即删除：先更新服务接口、CloudCommandDao 和 VO／表映射等引用；不再产生虚假的盒子回执。既有数据库 boxId／历史归属键暂保留兼容，不能和 Java 字段清理一起无迁移地删表／删历史。

不在本阶段为 Cloud* 类全局改名。名字可以后续调整，现阶段优先让唯一主路线通、移除无用实现，不用改名制造额外 import／API 风险。

## 5. Maven、配置、任务和测试

- `pom.xml` 中 MQTT 专属 Eclipse Paho 和 Moquette 依赖在所有生产／测试引用移除后删除；不删除 Spring WebSocket、Security、MyBatis-Plus、MySQL 等共用依赖。
- 所有 application 配置中清理 mqtt broker、Topic、用户名／密码、客户端参数；不只改 application-dev，需核对 application-flow-check 与默认配置。
- 清理 Broker 自动启动、cleaning-mqtt-send/dispatch 任务、签名 Bean 注入和遗留日志提示；内部 Message 没有 MQTT QoS 或 Broker ACK。
- `CleaningMessageFlowTest`、`CleaningBrokerFlowTest` 是旧 MQTT 专属测试，替换为本地消息链路／PLC 网络模拟测试或删除专属测试。新测试必须覆盖实际新路径，不能删测试后声称质量通过。
- `CloudCleaningWebSecurityTest`、用户／路由等仍有价值，保留并调整主服务签名；`LegacyCleaningRoutesTest` 跟随旧兼容路由最终决定，不随意删。
- 不自动删除数据库、历史记录、用户账户、日志目录或 archive；`archive/legacy-mqtt-*` 不在活动源码下，本次说明不授权清空档案。
- 旧 MQTT 文档保留原部署事实，并由文档入口说明不是现行设计；本轮用户要的是删除说明文件，不是删除既有 Markdown。

## 6. 删除实施顺序与检查

1. 明确新 Message、唯一清洗单写者及 PLC 网络类的编译依赖与 Bean 装配。
2. 将 CloudCommandService 主调用改为本地 Message，并移除大厅／DTO／DAO 的旧回执专属引用；未配置点位时返回未就绪，禁止假成功。
3. 一起移除旧 8 文件组，按实际剩余引用清理专属模型；共用用户、VO、设备关系不删。
4. 移除活动 MQTT 34 文件组以及 Maven／配置／任务；这一步不保留备用 MQTT 运行分支。
5. 调整并补消息、权限、PLC 模拟、历史和并发测试。
6. 用 rg 检查活动源码／配置／测试的 Mqtt、Paho、Moquette、Signer、Broker、旧接口注入残留；文档里的历史描述不属于代码残留。
7. 核对唯一 Cloud* Bean、HTTP 入口、ThreadCenter 生命周期、数据库接口、队列及未配置情况下的明确错误。
8. 在用户允许后执行 Java 编译／测试／启动验证，再按安全安排联调；没有运行就不得填写“删除后编译通过”。

本轮实际删除结果以 2026-10-15 执行记录为准，移除和覆盖的原文件另存为可恢复备份；未执行数据库、构建或真实设备操作。不可递归清空工作区或项目根。
