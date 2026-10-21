# 现场服务器内部 Message 全链路与并发设计

日期：2026-10-14。配套 [PLC 迁移主方案](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/plc_to_cloud/04_LOCAL_SERVER_PLC_MIGRATION_PLAN.md)。状态：待实现设计，本次不写 Java/C++ 功能代码、不删除类、不运行服务器或 PLC。

目标只有现场服务器直连 PLC。最终移除 MQTT 及旧清洗路线，仍复用现有 Web、表示层、大厅、安全、CloudCleaning/Command/Statistics、DAO、线程与缓冲模块。下面“拟补／新增”均不是当前已完成的方法。

## 1. Message 是什么

`Message` 是系统内部跨阶段／线程处理的不可变消息，不是 MQTT 消息，不是直接发给 PLC 的字节报文，也不是页面 VO。HTTP 的 DTO/Param、业务 BO、PLC 协议 DTO 继续保持各自边界；需要排队处理时才放入统一信封。

建议新增 `domain/message/Message`、`MessageType` 与强类型 `MessagePayload`，使用 Java record 或等价不可变对象。载荷必须与类型匹配，不采用到处强转的 Object 或散落字段的 Map；信封泛型可承载现有 BO，不需要为每个功能再造接口类。

| 信封字段 | 说明 |
|---|---|
| `messageId` | 本条消息唯一身份；子写入、写结果及保存任务可分别关联 |
| `type` | 业务操作、设备采样、设备状态事件、PLC 写请求、写结果、历史输入、持久化请求／结果等 |
| `correlationId` | 同一次用户操作或同一组处理的关联身份，不是 Modbus transactionId |
| `causationId` | 直接产生本消息的父消息身份，便于追踪与去重 |
| `cleaningSystemId` | 系统归属及单写者路由键 |
| `deviceId` | 实际 PLC／业务设备归属；不拿用户输入直接当 IP |
| `createdAt`、`expiresAt` | 创建时间；命令有有效期，历史事实不能按普通命令 TTL 随意丢弃 |
| `priority` | 服务端按消息类型设置；用户不能通过字段把普通操作冒充急停 |
| `payload` | 不可变强类型载荷，包含相应 BO／协议请求或结果 |

requestId、业务 commandId、子写 messageId、Modbus transactionId 各自有用途，保留关联，不能全部复用一个编号。操作载荷包含 operatorId、expectedVersion、操作参数等；密码只用于认证，不进入持久化消息、PLC 报文或日志。

最低消息分类建议：

| 类型 | 载荷 | 消费方 |
|---|---|---|
| `USER_OPERATION` | 现有急停／设置 Param 转为业务操作 BO | 清洗单写者 |
| `DEVICE_SNAPSHOT` | 已解码 DeviceRuntimeSnapshotBo，带采集流和序号 | 清洗单写者 |
| `DEVICE_EVENT` | 模式、通信、限位等已观察事实，带事件序号 | 清洗单写者 |
| `PLC_WRITE_REQUEST` | 点位、编码后的值／寄存器布局、截止时间、业务关联 | PLC I/O 工作者 |
| `PLC_WRITE_RESULT` | 校验后的子写结果，成功／拒绝／不确定和时间 | 清洗单写者，再交命令聚合 |
| `CLEANING_INPUT` | 会话、轮次、快照、换向／结束等现有 BO | 历史整理工作者 |
| `PERSIST_REQUEST` | 稳定身份的历史批次或配置／命令审计任务 | 持久化工作者 |
| `PERSIST_RESULT` | 对应任务的提交／失败结果与版本 | 原拥有者：清洗或历史整理 |

消息分类表示一组语义，不为每个公共方法建立新框架。历史输入必须保持会话／轮次事件与测量的顺序；日志打印关联号及必要结果，不逐帧打印完整数据。

## 2. 用户操作到 PLC 的类和方法

### 2.1 HTTP 线程只受理，不等待设备

```text
用户 A／B 的 HTTP 请求
  → CleaningWebService.emergencyStop／updateSettings
  → CloudCleaningWebPresentation
  → CloudCleaningWebConverter（DTO → Param，当前用户上下文）
  → CloudLobbyService（安全检查 → 清洗服务）
  → CloudSafetyService.verifyEmergencyStop／verifySettingsChange
  → CloudCleaningService.emergencyStop／updateSettings
  → CloudCommandService.submit
  → CloudCommandRegistry.registerAndOffer（拟补，原子登记 USER_OPERATION）
  → 返回“已提交”及 requestId，线程结束该请求
```

受理前校验账号、权限、设备归属和输入格式。改参数密码复核按既定用户登录认证能力实现；返回入队成功不是设备执行成功。查询操作结果使用现有命令查询入口，不阻塞 HTTP 等待 PLC，不无限循环轮询数据库。

### 2.2 清洗单写者生成下行消息

```text
ThreadCenter 管理的清洗单写者
  → CleaningRuntimeTask.runCycle
  → CloudCleaningService.onMessage（拟补，分派操作／设备事实／结果）
  → 复核命令期限、配置版本及本系统在途修改
  → 服务拥有的 InnerWallCleaningDeviceControl.prepareOperation（拟补）
  → PlcDeviceRuntime.buildWriteRequest（本地点位权限／类型／编码）
  → CloudCommandService.enqueueDeviceWrites（关联父命令与子消息）
  → CloudCommandRegistry 本地 PLC 写消息队列
```

控制对象由清洗服务创建和管理，没有独立线程；操作密码、HTTP 字段和 Socket 不进入控制对象。急停通常一个写请求，三参数修改按实际寄存器布局生成子消息；未配置点位返回明确未下发，不产生成功写消息。

### 2.3 PLC 网络发送与结果

```text
PLC I/O 工作者
  → PlcDeviceTasks.runDeviceCycle
  → 取 PLC_WRITE_REQUEST（急停优先，普通命令有限批次）
  → PlcDeviceService.dispatchWrite
  → ModbusTcpDevice.write（按功能码编码和串行交换）
  → PLC
  → 网络层校验响应／超时／异常码
  → PlcDevicePresentation.onWriteResult
  → 写结果队列中的 PLC_WRITE_RESULT
  → 清洗单写者消费
  → InnerWallCleaningDeviceControl.applyWriteResult 聚合子写
  → CloudCommandService.onLocalWriteResult 更新可查询结果
  → 配置确认／审计 PERSIST_REQUEST → 持久化任务 → DAO
  → PERSIST_RESULT 返回原操作，完成保存阶段
```

业务层不比较字节报文。写响应只证明协议写入确认，实际急停事实仍由读取状态确认。三个参数只确认两个时不能显示全部成功。数据库保存失败时保留“已确认写入但保存失败”，不能假装现场没有改变。

参数准备、全部写入确认、配置保存和实际运行生效是不同阶段；只有 PLC 生效规则明确时才宣称运行生效。审计字段应分别记录状态。未知结果不能通过伪造 MQTT CommandAck 补成成功。

## 3. PLC 读取到业务、历史和页面的类和方法

```text
PLC I/O 工作者：PlcDeviceTasks.runDeviceCycle
  → PlcDeviceService.pollDueDevices
  → ModbusTcpDevice.poll（读取 m/d 对应协议地址）
  → PlcReadResultDto
  → PlcDevicePresentation.onReadResult
  → DeviceRuntimeManager.updateDeviceData
  → PlcDeviceRuntime.updateDeviceData
  → snapshot + 已观察到的离散事件
  → 发布 DEVICE_SNAPSHOT／DEVICE_EVENT 到中心
  → 清洗单写者：CloudCleaningService.processDeviceSnapshots
  → CleaningDeviceConverter.toMeasurementFrame
  → InnerWallCleaningState.observe + 会话／轮次观察
  → CleaningRealtimeSnapshot／扩展 TelemetrySample
       ├─ CloudStatisticsService.acceptSample
       │    → CloudRuntimeDataCenter.updateLatest → 页面查询／推送
       └─ acceptCleaningInput → CLEANING_INPUT
            → 历史工作者：runTelemetryReductionCycle
            → CleaningTelemetryReducer（累计 0.5m 等）
            → 稳定历史批次 → CloudWarmBuffer
            → PERSIST_REQUEST／现有历史保存入口
            → CloudStatisticsService.runRealtimePersistenceCycle
            → TelemetryHistoryDao.insertBatch → MySQL 事务提交
```

设备采集先更新 Runtime，不调用大厅来解析寄存器。用户网络入口仍经大厅；这两种路径不能混成每个采样都构造一次用户请求。

Runtime 最新值可以覆盖，热业务最新值也可以覆盖；轮次／限位／异常起止事件及待保存历史不能静默覆盖。第一阶段为历史提供有界有序采样输入，不能只读最新热快照后声称还原了全部变化。满队列必须记录缺口、背压或进入明确降级，不用无限内存；同时不能因历史满队列把 PLC 急停和通信维护卡死。

事件在每次实际采集更新时锁存到队列，避免 Runtime 发布漏掉已经读到的 m8/m9。但从未读到的短脉冲仍会丢，需现场累计计数／事件序号／锁存机制，Message 本身不能解决物理采样遗漏。

页面查询继续：CleaningWebService → 表示层 → 大厅 → 安全／清洗查询 → 不可变热快照 → Converter → VO。查询不排进 PLC I/O 队列，不写清洗状态，不计算前端均值。

## 4. 初始线程规划

本阶段一台 PLC、一个服务实例。建议 4 条应用后台工作通道，每通道初始一个专用工作线程，由现有 ThreadCenter 统一创建、命名、监督与停止。不是“全进程只有 4 个线程”；HTTP、WebSocket、JVM、数据库连接池等另有框架线程。

| 工作者 | 唯一写入／负责内容 | 不允许做的事情 |
|---|---|---|
| `plc-io` | PLC 会话、采集、写报文、网络重连、Runtime 更新及快照／事件发布 | 业务版本提交、长数据库事务、用户 HTTP 等待 |
| `cleaning-business` | 清洗状态、会话／轮次、在途配置、操作与子写结果聚合、超时检查 | 阻塞 Socket、等待历史消费者、持锁等待 DAO／Future |
| `telemetry-reduce` | 有序历史输入、0.5m 留点基准、批次整理及待保存状态 | 改 PLC 控制状态、发送设备命令 |
| `cleaning-persist` | 历史、配置版本、命令审计等有界保存任务，返回提交结果 | 调用 PLC、同步回调持锁的业务服务 |

快照发布放在 plc-io 内，业务超时放在 cleaning-business 内，原独立定时任务合并登记，避免重复运行。HTTP 工作线程可以同步进行有超时的认证／权限读取，但不持业务或队列锁，也不等待整条设备链路完成。

当前 ThreadCenter 只有 2 个共享定时线程，尚未提供上述四通道。后续需先说明并实施其配置化、专用通道、有限队列及停止等待能力，不由服务自行创建线程。简单把共享池大小从 2 改 4 不保证四通道隔离；同名任务禁止重复登记。这个调整只落在线程中心，不换业务架构。

PLC 读取目标每 500ms，一次请求有限等待。队列唤醒与周期采集由 I/O 通道协调，急停检查更频繁，且在连续读取块之间检查优先请求。不能一边等待很长网络响应，一边声称急停无延迟。多 PLC 的 I/O 并发和线程扩容另行验证，不按四台绞机创建四个 PLC 连接。

持久化请求和结果有相应所有者，不在一个结果队列里让错误的消费者取走别人的消息。历史生命周期输入按系统／轮次顺序消费；仅紧急用户操作有明确优先通道，不用一个无界“万能 Message 队列”处理全系统。

## 5. 锁具体放在哪里

原则是单写者管理业务状态、不可变快照供查询、队列边界短锁。下面不是要求每类都加锁。

| 资源 | 保护方式 | 持锁范围 |
|---|---|---|
| PLC Socket／transactionId | 单 I/O 工作者拥有；若客户端回调或未来多调用者，单设备会话锁 | 一次有明确超时的协议交换；不同时持中心锁，不回调业务 |
| Runtime 点位表 | I/O 唯一更新；发布不可变快照。确需其他读取时用短读写锁 | 复制／更新内存后立即释放，不跨 Socket 等待 |
| 用户操作、requestId 登记及待发送队列 | CloudCommandRegistry 原子 `registerAndOffer`；有界非阻塞队列 | 检查重复、登记和 offer 的短临界区，不在此做网络／数据库 |
| 清洗状态、轮次、在途配置 | cleaning-business 唯一修改 | 不需要全局服务锁；HTTP 只读已发布快照，不访问可变状态对象 |
| 热数据及设备快照中心 | 已有同步方法保持短小；可按系统原子替换不可变快照 | 内存检查和替换，不在 synchronized 内调用其他服务 |
| 历史输入／待保存任务 | 有界队列，明确在途容量；整理器单写者拥有 | take 出队后释放，再处理或写数据库 |
| 数据库版本／记录 | 唯一键、版本条件更新、短事务 | 事务不等待 PLC；只发持久化结果消息，不同步回调持锁对象 |
| 配置在途标记 | 业务状态字段，不是一直持有的 Java Lock | 等待写响应时保留 marker，线程仍可处理急停和查询 |

硬性约束：禁止同时持有业务中心锁与会话锁；禁止队列锁内调用服务；禁止持锁 `put()`、`Future.get()/join()` 或等待另一个工作者；禁止在数据库事务内等 PLC。通常通过先取本地副本、释放锁、执行操作、再发结果消息，避免跨对象嵌套锁。

不可变不仅是 record 外壳：List/Map/byte[] 等载荷也要防御复制，不能把 Runtime 内部可变集合直接交给其他线程。

## 6. 两个用户同时操作会发生什么

### 6.1 两人同时修改配置

假设确认版本都是 5：

1. A、B 各自通过 HTTP 鉴权，产生不同 requestId 的操作消息；这一步不持设备锁。
2. 清洗单写者先处理 A，复核版本 5，设置 pendingConfig；不靠两个 HTTP 线程同时检查来决定能否修改。
3. 处理 B 时发现本系统已有在途修改，明确返回忙／冲突，不让 B 三个参数穿插进 A 的三个参数。
4. A 确认写入并完成配置保存，版本变为 6；B 后续用旧版本 5 重试会得到版本冲突，需重新查询。
5. DAO 的版本条件更新和 requestId 唯一约束作为第二道保护。数据库版本保留／提交需要时交持久化通道并以结果消息继续执行，不在业务线程锁内等结果。

同 requestId、同用户同内容重试返回同一操作，不重复写 PLC；同 requestId 不同内容拒绝。不能因为 ConcurrentHashMap 是线程安全的，就认为“检查版本＋发送＋保存”整体原子。

写超时可能已经改变 PLC 参数，必须标记不确定。此时不能简单释放 pendingConfig 让另一用户用旧版本覆盖；根据现场约定进行读回／人工确认／配置对账后才能解除阻塞。这里保留的是业务标记，没有占住 Java 锁，急停仍可继续。

### 6.2 一人改配置，另一人急停

急停单独高优先通道，不因 pendingConfig 拒绝，也不要求先获得普通修改控制权；仍需要鉴权。已经发送或正在等待响应的写请求不能假装撤销，记录可能部分写入。尚未发出的普通子写暂停／终止策略按安全约定执行，不再自动续发。优先检查点必须同时存在于业务消费和 I/O 队列，不能只在一个队列设优先级。

急停不能强行抢走正在交换中的同一 Socket；用有限读写超时、分块优先检查和现场安全装置限制影响，不保证零延迟。

### 6.3 两人同时急停或查询

急停都是朝同一安全方向，但 requestId 分别审计；能否合并在途急停由 PLC 写点是否幂等确认，不能未经确认随意合并或无限重发。查询各自读取快照，不参与设备下行互斥。

## 7. 为什么不能直接承诺不会死锁

两个用户不是死锁的充分条件；死锁来自循环等待。例如一条线程持“业务中心锁”再等“PLC 会话锁”，另一条线程持“会话锁”回调业务再等“中心锁”，就可能互相等待。

另一个风险是不持 Java 锁也会卡住：唯一业务工作者提交一个必须由自己消费的结果任务，然后 `Future.get()` 等结果；或同一个小线程池的全部线程都在等待队列里还没运行的任务。这属于执行器饥饿／自等待，不能只检查 synchronized。

当前热中心、命令登记和线程中心采用内存短同步方法；尚无完整本地 PLC 链路，不能据此证明未来不会死锁，也没有执行压力测试。目标设计通过单写者、禁止嵌套跨模块锁和消息结果回流减少风险，必须按下一节验证。

同一 PLC 只允许一个活动控制实例。以上 JVM 内锁不能阻止第二个 Java 进程或另一软件写 PLC；第一阶段部署限制单实例，后续如多实例需明确设备所有权租约／主备，不靠数据库版本字段替代 PLC 控制互斥。

## 8. 并发和停止验收

- 两个用户同版本同时提交设置：只有一组参数进入 PLC，另一请求明确冲突／忙。
- 同 requestId 并发重试：原子登记一次，没有重复子写；不同内容明确拒绝。
- 配置在途时急停：业务和 I/O 两级优先均生效，不因配置标记无限等待。
- 延迟／错误／丢失 PLC 写响应：没有持锁卡住查询或其他工作者，不确定结果不冒成功。
- DAO 变慢或失败：不占用 I/O 线程；队列满有明确结果和在途容量，不静默丢批次。
- 状态变化和设备断线：即使没有新测量，也能发布状态版本，不伪造采样序号。
- 使用线程 dump／超时测试检查锁循环和工作者自等待；单次完成不代替并发验证。
- 停机先拒新操作、停生产、有限等待 I/O、整理末点、有限保存、关闭线程；不可在持中心锁时 join 消费该中心的线程。

本次交付只有设计。代码实现后必须分别验证死锁、线程饥饿、数据竞争、重复下发和数据库版本竞争；“没有死锁”不等于“不会发生错误覆盖或漏记录”。
