# PLC告警事件与阈值修改：业务、保存和接口

修订日期：2026-10-25。依据[本次红字说明](reference/plc-alarm-revision-20261025.md)及[全部页面新版报告](reference/frontend-pages-report-20261025.md)。本文是后端目标方案，当前Java尚未实现新告警查询、阈值命令或事件落库。接口字段见[页面接口](external-api-contract.md)，方法清单见[Controller说明](controller-api-specification.md)。

## 1. 谁判断、谁保存

PLC负责依据实际生效参数判断普通/严重拉力错误。后端接收明确的PLC错误信号，维护事件身份、过程、峰值和存储；前端展示。后端和前端都不因一条拉力数值超过配置而自行新增故障事件，也不因修改阈值重判或改写旧事件。

后端确认的PLC通信异常可以产生source=PLATFORM的连接事件；浏览器网络失败、401、HTTP超时、SSE断开不是PLC离线证据。状态/等级、是否已经结束和具体点位语义分别保留，不能根据拉力高低或颜色补出普通/严重信号。

保留大厅与清洗、命令、安全、统计四服务。安全模块负责账号/系统/操作权限；清洗模块唯一维护系统、运行与事件事实；命令模块维护下发队列和回执；统计模块可靠保存、查询与事件窗口统计。设备管理/DAO供上层读取配置与写库，不回调上层。

## 2. 同一过程怎样维护

| 已确认事实 | 处理 |
|---|---|
| 新的PLC过程开始 | 分配稳定alarmCode，revision从1开始，保存来源及触发快照 |
| 同过程持续、更新有效峰值 | 更新同一事件；有实际变更才推进revision，不新增告警条数 |
| 同过程普通升级严重 | 同alarmCode更新已确认等级/PLC内容和revision |
| PLC明确该过程结束 | eventState=ENDED并记录endedAt；不新增“结束告警” |
| 后续独立过程开始 | 新alarmCode；不能覆盖上一个过程 |
| 断联或缺少结束证据 | 原过程不自动结束，记录观测中断/结束未确认说明 |
| 持续过程跨日或跨RUN | 不因日期或runCode变化换编号；没有确定关联时runCode=null |

ACTIVE/ENDED描述现场过程，ENDED不表示人工确认、维修、恢复或消警。新页面没有上述写操作，不保留虚构的处理按钮。旧recovered_at/terminated_at等如果确有历史事实仍保留，但不能自动转换为新过程endedAt。

有PLC原生事件编号时，使用系统、控制器会话、来源通道/错误类型和原生编号组成唯一来源键；alarmCode是对外稳定编号。只有普通/严重位时，需已确认开始、变化、结束语义才能建立状态机。两个通道同时异常不能按同一winchCode合并，PLC重启复用编号不能覆盖旧事件。降级是否同过程、多个故障码是否同一原因未确认时不猜合并规则。

采集重放沿用稳定源标识，重复消息不重复新增或计数。revision为事件真实单调递增的安全整数，与更新持久化同事务；前端按alarmCode更新并用revision防回滚。updatedAt只是记录变化时刻，不能取代序号；不对采样时间做年龄、早晚、时钟偏差或过期判断。

## 3. 保存哪些事件数据

复用目标cleaning_exception_event，具体列及索引见[数据库设计](database/page-driven-database-design.md)。保存身份/控制器会话、内部通道、原生信号、当前等级、PLC原文、过程状态及版本、真实或首次观测时间、触发/峰值/结束事实、阈值与配置快照、可靠RUN关联。is_alarm明确区分告警与其他异常事实，不恢复旧t_alarm。

页面occurredAt为事件显示起点，并同时返回timeBasis=ACTUAL/FIRST_OBSERVED、actualOccurredAt及firstObservedAt。只有首次观测时actualOccurredAt=null；不把首次观测声称为PLC精确物理发生时间。持续更新不反复改起点；后补明确真实起点属于同号事实修订，推进revision并重建受影响日期统计，不新建第二条。列表和统计使用同一明确口径。

等级level只从已确认PLC映射取得NORMAL/SERIOUS；没有映射时返回null及levelConfirmed=false，不默认NORMAL。content保留PLC错误原文或可追溯的错误码解释，不按新配置改成另一条错误。thresholdKind不从严重等级推断，事件保存发生时已生效阈值快照。

triggerValue是触发时实测值，peakValue是该过程已确认/已观测的最大有效值，peakAt是真实对应时刻。PLC若直接给完整峰值，保留来源；后端也可在已确认的事件窗口内按同一来源有效采样增量累计，但不能拿触发值冒充完整过程峰值。peakCoverage=COMPLETE/PARTIAL/UNCONFIRMED、peakSource、hasDataGap说明范围；掉线后仍有最大观测值时保持PARTIAL，不造全程峰值。

峰值整理不重新判定故障。持续过程每个采样不是一条告警，也不要求每次查询都更新事件版本。开始、升级、结束等事实可靠入缓冲/落库；峰值等变化可以按已有可靠批次合并写入，版本对应实际发布的持久结果。

正常DOWN仍不保存连续历史。DOWN发生事件时可保存触发、峰值、结束等有限事件点快照，并在事件已开启期间用采集流增量维护最大观测值；不保存整段前后窗口、不据此声称有完整下降曲线。所有UP用途继续原始保存，生产报表只取PRODUCTION_UP。

## 4. 最新、历史与独立异常

新正式入口是`GET /api/command-center/alarms/latest`和`GET /api/command-center/alarms/history`。旧alarm-dashboard退出目标页面协议；旧统计卡、分类筛选和人工处理状态不再是新页面必返内容。

latest不接日期或页码，返回完整近期集合、asOf和后端hasAttention。目标近期策略为全部未确认结束的告警，加最近24小时结束的告警；recentWindowHours和窗口边界随响应返回，24小时是可调整的展示配置，不是历史删除或采样过期规则。跨日持续事件始终保留。所属设备筛选只缩小recent，hasAttention始终为全系统口径。

hasAttention目标规则：存在尚未明确结束的PLC告警，或已确认且尚未恢复的PLC连接事件则true；查询成功且确认无此类事件才false。普通/严重都按已确认事件处理，不用浏览器已读或点击清除。缺少事件事实源时返回依赖未就绪错误，不用false冒充正常；这些规则需在现场信号映射完成后启用。

latest不能悄悄截断并称完整。按目标每次最多1000项的序列化预算完整生成；超预算返回503 RECENT_SET_TOO_LARGE与说明，保留前端上次视图，再调整近期窗口配置或实施有快照版本的分批方案；不能丢弃跨日持续事件凑上限。此预算不限制数据库保留年限。侧栏与告警页共享一次可见时15秒查询，后端查询不改变事件。

history按上海业务发生日期、等级、设备在数据库筛选再分页。CUSTOM输入startDate/endDate包含首尾，内部转换`[开始日00:00,结束日次日00:00)`；TODAY/7D/30D分别是今日、含今日最近7/30个自然日。列表最大每页100、默认10；CUSTOM最多366天为单次查询边界，不删除更早历史。levels缺省为NORMAL,SERIOUS，显式空字符串返回空集合，不能变成全选。

历史日期按事件显示开始日，不因升级/结束重新计一次。latest包含跨日进行中的集合与history某日期的筛选集合可以不同。前端不能用recent给历史补条目；旧版本不能覆盖同alarmCode新版本。未知等级的事件保留在latest并说明，在历史可显式includeUnclassified=true查询，不归入普通或严重。

非告警DOWN等事实通过补充`GET /api/command-center/events/history`分页发现，再由`GET /api/command-center/events/{eventCode}`读详情，避免移除alarm-dashboard后丢失查询入口；不强造级别进入告警计数。事件详情只读，没有查看即恢复的行为。

## 5. 两路阈值怎样生效

新允许的目标是`PUT /api/command-center/winch/tension-thresholds`。业务输入为systemCode、winchCode及本机两路真实sensorCode的alarmThreshold/safetyValue，固定吨t；后端转kg存储，PLC编码由已确认单位决定。只做必要的身份、权限、两路完整、有限正数且预警小于保护检查；图表1.4t/3t不是设备安全上限。

命令沿用requestId幂等，expectedVersion取detail.thresholdSettings.savedVersion；这是目标技术字段，当前前端还需补发，不能声称附件已有。真实版本尚未初始化时返回409 CONFIGURATION_NOT_READY，不能猜0；缺少现场点位/写入映射时503 THRESHOLD_MAPPING_NOT_READY，无账号操作权限时403，不创建假APPLIED。

```text
登录操作人 → 安全服务 → 清洗操作编排
  → 两路申请及真实保存版本 → 命令服务有界队列
  → 已核实的PLC写入 → 现场生效确认
  → 清洗配置视图更新 → 统计保存器/DAO保存确认快照
```

| 回执 | 数据库/页面含义 |
|---|---|
| PENDING | 已受理/保存待应用；effectiveThresholds继续返回旧确认快照或null，不用申请值覆盖 |
| APPLIED | 两路均取得明确PLC生效依据；返回完整实际effectiveThresholds及配置版本；实际值可因已确认编码量化与申请值不同 |
| 写应答、超时或部分写入 | 不是APPLIED；命令记录WRITE_CONFIRMED/UNCONFIRMED等事实，说明未确认/部分完成，不自动回滚PLC |
| 明确失败/拒绝 | 非成功回执及具体原因；保留已保存申请和上次确认值，不能持续报等待成功 |

请求保存与实际生效版本分开保存，APPLIED时同事务更新两路确认快照及当前传感器配置。部分通道已确认、另一通道未确认时，保留逐通道证据和有效性说明，不能把旧快照声称为设备此刻已验证的完整配置。重新读取detail返回thresholdSettings与传感器已确认值，不根据提交草稿改NORMAL/WARNING/ALARM或告警事件。

本轮未提供阈值寄存器、数据类型、倍率、单位、两路写入/回读确认协议，均不得从D100～D108或图上量程猜。该新配置入口不开放远程启动、复位、解除急停、暂停/恢复、单轴升降；M50急停也保持原确认限制。

## 6. 实施与核对

先确认PLC事件和阈值协议，再做持久事件身份/版本、查询及有效配置快照，最后接Controller和页面。Controller只绑定输入、调用大厅/服务并转换响应；不能在Controller比较拉力、生成告警编号或直接写DAO。

需要核对的场景：持续→升级→结束始终同一编号；新过程新编号；两通道同时异常不合并；跨日/跨RUN不重复计数；断联不自动结束；重复源数据重放不增条目；峰值缺口保留；查询不写事件；PENDING不改确认值；APPLIED必须两路确认；错误参数只做所属模块一次检查；采样时间不作接收门槛。

用户附件报告的300项前端测试及9项浏览器模拟检查仅说明前端测试范围，本轮未重新运行，也不能证明PLC事件状态机、真实阈值写入或数据库闭环已完成。
