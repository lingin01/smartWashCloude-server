# 云端消息对象、收发队列与对外调用流程

核对日期：2026-10-03。目标项目：SmartWashCloud-Server。

2026-10-07 补充：已增加显式白名单联调模式和配置字段对齐，实施步骤与现场验收以 [双向消息流程实施计划](CLEANING_BIDIRECTIONAL_FLOW_IMPLEMENTATION_PLAN.md) 为准。下文签名拦截说明适用于默认正式模式；联调模式发送无签名、标记为禁止执行的消息，盒子仍然拒绝设备操作。

本次按照盒子 `CLEANING_CLOUD_INTERACTION_DESIGN.md` 的分层要求连接云端基础流程。业务详细字段、正式签名协议、初始化/用户同步等新增协议以及 PLC 配置编码仍待双方确定。本文的“链路已连接”表示 Java 类和方法已经调用到指定边界，不表示现场设备已完成端到端联调。

## 1. 两类网络入口

用户入口为 `network_access_layer/web/CleaningWebService`，经过 `presentation/web/CloudCleaningWebPresentation` 和 `CloudCleaningWebConverter` 将 DTO 转成 Param，再调用 `CloudLobbyService`。

现场盒子入口仍为 `network_access_layer/mqtt/MqttService` 和内部 `PahoMqttTransport`。收到的数据入接收队列，由 `CleaningMqttDispatcher` 转换后调用同一个大厅。

```text
HTTP → 用户表示层 → 云端大厅 → 安全服务 → 清洗服务 → 命令服务
                                                      ↓
                                      协议转换/签名 → 协议消息 → 发送队列
                                                                      ↓
                                                             MQTT 网络服务
                                                                      ↓
                                                                     盒子

盒子回执 → MQTT 接入 → 接收队列 → MQTT 表示层 → 大厅 → 命令服务更新状态
```

业务服务不创建网络线程，不直接调用 Paho。发送、表示层消费和命令超时周期统一由既有 ThreadCenter 管理。

## 2. 消息与命令分开

`dto/mqtt/MqttProtocolMessage` 是收发双方共同使用的不可变协议消息：messageId、requestId、目标盒子、业务类型、MQTT 元数据、载荷、优先级、创建/过期时间、已尝试次数、最大尝试次数和下次发送时间。载荷使用防御性复制，不存签名密钥。

`MqttInboundEnvelope` 包装该消息，并附加匹配的主题键、主题变量和接收时间；原有实时、历史、告警转换入口继续使用它。

`CloudCommand` 保存申请操作。`CloudCommandStatusBo` 保存云端命令编号、请求编号、盒子返回的命令编号及执行阶段。云端命令编号不能冒充盒子的命令编号；盒子在拒绝请求时允许没有 commandId，使用 requestId 关联。

## 3. 消息缓冲中心

继续使用一个 `MqttMessageBufferCenter`，以收、发和网络事件窄接口向不同调用方提供能力。

| 部分 | 当前策略 |
|---|---|
| 普通可靠入站消息 | 4096 条，历史和告警等按队列消费 |
| 命令回执 | 独立 512 条容量，优先消费，避免历史洪峰占满其队列 |
| 实时入站消息 | 每个盒子保留最新待消费帧；旧帧重试不覆盖更新帧 |
| 出站消息 | 最多 1024 条，急停优先，发送中的消息仍占容量 |
| 出站失败 | 最多 3 次发送尝试，间隔 1 秒，重试保持消息编号和原载荷 |
| 断线 | 未过期出站消息留在队列，断线时继续清理过期命令 |
| 网络发送结果 | 放入内部事件交接队列，由表示层转成 CommandDeliveryParam 后送大厅 |

MQTT 服务每个发送周期最多处理 20 条，间隔 50ms；同步 publish 等待上限 5 秒。调用方只是入队，入队时没有直接发布。

**当前队列、网络事件和命令登记均在内存中。** 本次实现了同进程断线后的保留与重试，尚未实现进程重启恢复、命令与 Outbox 的数据库事务、持久化死信及最终回执恢复。既有 `CloudCommandDao` 契约未假装已经落库。现场发布前需继续完成这些能力。

## 4. 四项对外功能

### 4.1 接收

当前配置接收四种盒子消息：实时遥测、历史批次、告警事件、命令回执。

- 实时 → 大厅 → 统计服务 → 热快照。
- 历史 → 大厅 → 统计服务 → 温缓冲 → 可选落库任务；任务仍默认关闭。
- 告警 → 大厅 → 告警服务 → 当前内存告警。
- 命令回执 → 大厅 → CloudCommandService → 原命令状态。

连接报告、初始化回复、用户/设备同步回复、状态事件、作业汇总、告警处置及历史业务 ACK 的扩展分发仍按交互文档后续补齐；本次没有自行确定未定义的 Topic。

### 4.2 Web 急停

`POST /api/cloud/cleaning/boxes/{boxId}/emergency-stop`

HTTP DTO：requestId 必填，sessionId 和 reason 可选。客户端不能指定操作人和权限。

表示层依据登录用户查询实际启用状态和权限；大厅调用安全服务，再交清洗服务构建申请。命令服务登记稳定 requestId，委托协议转换器和签名适配器封装消息，然后入发送队列。

急停主题来自配置：`cleaning/box/{boxId}/command/emergency-stop`，QoS 1，retained=false。请求有效期当前 30 秒，急停优先级为 100。

同 requestId、同操作内容重提返回已有操作，不重新签名/入队；同 requestId 换目标、操作人、类型或内容会拒绝。该去重目前仅在同一进程内有效。

### 4.3 修改配置

`PUT /api/cloud/cleaning/boxes/{boxId}/settings`

HTTP DTO：requestId、expectedVersion、settings。settings 保留 startTime、intervalSeconds、speedLevel、groupSpeedMetersPerMinute。当前盒子协议需要明确的整组速度，因此只有挡位而没有速度映射时拒绝；不自行猜测挡位对应速度。

与急停走同一安全/清洗/命令/队列链路。协议使用 expectedVersion 和 plan，plan 包含 startTime（HH:mm）、runIntervalMs（由 HTTP 秒数安全换算）及 groupSpeedMetersPerMinute；与当前盒子 Converter 对齐，不加入旧行程、轮数，也不发送未映射的挡位。正式模式仍有签名边界，现场 PLC 参数生效须另行联调验证。

本次不把申请值写成“当前生效配置”。盒子仍需检查 expectedVersion、执行设备下行、观察实际生效反馈、保存配置并返回实际版本。

### 4.4 查询

| HTTP GET | 返回对象与来源 |
|---|---|
| `/api/cloud/cleaning/boxes/{boxId}/realtime` | CloudRealtimeVo，读取热快照，直接展示盒子计算的平均值；未收到时 available=false |
| `/api/cloud/cleaning/boxes/{boxId}/commands/{requestId}` | CloudCommandVo，查询该盒子的关联操作及回执阶段 |
| `/api/cloud/cleaning/boxes/{boxId}/settings` | CloudSettingsVo；正式配置快照尚未同步时 NOT_SYNCHRONIZED，版本和配置为空 |

历史曲线与统计查询暂未补实现，不返回模拟曲线。2026-10-11 已撤销旧清洗页面的 9 个模拟查询入口和 2 个返回空成功的任务查询入口；原路径不再注册，调用返回 404。保留新云端真实查询，以及旧 start/stop/reset 的不支持或迁移说明，不把旧页面路由自动映射为不同语义的新接口。

旧 `/api/cleaning/stop` 缺少 boxId/requestId，现明确提示迁移到新入口。旧远程 start/reset 接口明确返回不支持，自动作业由现场 PLC 启动。

## 5. 回执与状态

盒子当前回执 stage 使用数字，云端转换器明确映射：0=REJECTED、1=ACCEPTED、2=RUNNING、3=SUCCEEDED、4=FAILED、5=TIMED_OUT。回执必须有 requestId 和有效时间；已受理及其后的回执需要盒子 commandId。错误盒子、不同盒子命令编号或未知请求不更新原操作。

```text
CREATED → QUEUED → BROKER_ACKNOWLEDGED → ACCEPTED → RUNNING → SUCCEEDED/FAILED
              └─────────────── 盒子可以直接返回 REJECTED

无法封装可信协议：BLOCKED（未发送）
过期、发送暂未确认、盒子超时：UNCONFIRMED（请核对现场）
```

网络事实和盒子回执可能先后交错。已受理/执行中的操作不被迟到的 Broker 确认回退；终态不被旧回执覆盖。UNCONFIRMED 可被迟到的最终盒子反馈更新为明确终态。

发送失败和超时不能证明设备没有执行；因此不给前端“已停止”或“未停止”的推断结论。只在盒子返回实际成功阶段后展示成功，当前回复源认证仍需随双方签名/Broker 身份策略继续确定。

## 6. 当前签名与 HTTP 认证边界

新的 `/api/cloud/cleaning/**` 启用既有 JWT 校验。refresh token 不作为设备接口登录凭据。表示层每次读取实际账号状态与角色权限，查看需要 device:read，急停和修改配置需要 device:write。旧接口的通用开发访问策略没有整批迁移，本次控制操作使用新命名空间。

`RemoteCommandSigner` 是待实现协议适配接口，默认实现 `PendingRemoteCommandSigner` 明确拒绝签名。没有选择 HMAC 算法，没有生成部署密钥，没有绕过盒子 SafetyService 的拒绝逻辑。

因此当前真实启动时，急停/修改配置可走到签名边界并返回 HTTP 503、BLOCKED、REMOTE_PROTOCOL_NOT_READY，随后可查询该操作；不会创建无签名的待发送设备控制消息。签名算法、规范化字段、keyId、预部署信任、时钟容差及密钥轮换共同确定后，只需补签名适配器及盒子验证，而不用再改上层调用关系。

## 7. 验证及后续边界

本次使用 Java 17 完成云端编译，并执行 `CleaningMessageFlowTest`、`CloudCleaningWebSecurityTest`、`ApplicationTests`：共 15 项测试，失败 0、错误 0、跳过 0。服务启动上下文检查通过；这不等同于真实盒子或 PLC 联调通过。

离线流程用例仅在测试中注入假签名和假 MQTT 传输，验证真实 Java 服务之间的调用，不向盒子或 PLC 发控制报文。覆盖 HTTP→大厅→安全→清洗→命令→协议消息→发送队列→网络服务→回执表示层→大厅→命令状态查询。

验证还覆盖：重复操作、内容冲突、拒绝回执无 commandId、数字 stage、错误目标、回执与发送事件乱序、查看用户禁止控制、禁用用户、断线保留、原载荷重试、急停优先、容量预留、过期清理、历史洪峰与签名未就绪拒绝。

后续需完成：正式签名与双端认证、初始归属及设备范围授权、命令/Outbox 持久化、配置快照同步、初始化及用户/设备同步、历史业务保存 ACK、盒子配置下行及真实设备反馈验证。当前未修改或编译盒子项目，也未执行建表脚本。
