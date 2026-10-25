# 从页面字段倒推的数据库设计

修订日期：2026-10-25。依据本轮两份新附件的全部页面修订及PLC告警事件规则更新；同名20261024文件已有新版正文，不能沿用旧右侧统计双表。本文是方案，不是已执行的建表或迁移；当前数据库仍为7表基线。

新版点位、M50急停和历史保存规则见[新版统一方案](../unified-plc-business-and-data-plan.md)；新页面职责、公共编号、单位和接口修订见[前后端对接修订](../frontend-backend-contract-alignment.md)。本文提供目标十表、字段、索引及页面查询设计，不把当前旧接口行为当成新的页面契约。

本次图表范围见[详情拉力对比处理方案](../detail-force-comparison-processing.md)，告警及阈值闭环见[PLC告警与阈值处理](../plc-alarm-and-threshold-processing.md)；依据保留为[全部页面新报告](../reference/frontend-pages-report-20261025.md)和[PLC红字修订原文](../reference/plc-alarm-revision-20261025.md)。

依据：上述两份新报告、当前后端代码及[实时、上行历史与详情口径](../realtime-upward-history-and-detail-plan.md)。前端报告已改成逐点表，动力页仍有本地SUM，历史页仅三类来源；本仓库未独立核验前端测试或PLC联调，不把模拟上下行、51/601点及测试数量当生产保存要求。

边界：单PLC、四机八索；上行保存核心过程采样，正常下行不保存连续测量，下行异常保存事件点及必要快照；没有数据过期判断。现阶段RUN页面建议采用上行单程，不称为完整清洗轮次。

## 1. 从页面倒推，先分清哪些数据要存

| 页面需求 | 数据性质 | 处理方式 |
|---|---|---|
| 系统编号、对象名称、分组、阈值、维护日期 | 稳定档案/配置 | 存设备配置表，有来源才填写；额定载荷/额定速度已退出本次页面需求 |
| 当前状态、方向、拉力、高度、速度 | 当前事实 | 清洗服务最新快照供首页每秒推送；单机详情主动查询快照，不为每个页面建立实时表 |
| 已保存参数、已确认生效参数、当次设定速度 | 版本化配置及历史快照 | 区分平台保存和设备生效；详情不拿实际速度或额定速度填设定速度 |
| 某次起止、结果、参数、曲线、异常 | 运行事实 | 主记录、上行采样、异常事件分别保存 |
| 本机本次平均、极值、累计运动 | 运行摘要 | 保存可合并的设备分日摘要，避免反复扫原始帧 |
| 今日次数、月/年结果、平均曲线 | 周期统计 | 后台预计算并保存，查询直接读取 |
| 状态/结果中文、实际名称、总拉力及统计 | 后端业务结果 | 根据真实配置、事实和规则生成；历史保留必要来源快照，前端不推导 |
| 字体颜色、显示小数位、图例与分页交互 | 前端展示 | 不建业务表；前端可做明确单位格式化，不重算业务结论 |

首页、单台详情和历史详情读取同一份事实，不各存一套重复运行数据。正常下行仍可实时查看，但不能提供未保存的完整历史曲线、原始导出或全程峰值。

最新两种图均以所选曲线的逐点表替代旧统计双表。历史图只选择ROPE1/ROPE2/SERVO，动力页保留两索合计需求；sampleKey作为后端稳定关联目标保留。statistics、rawStatistics、ropeExtrema、defaultUnitId不再为这些页面强制返回，但运行摘要、周期统计和高度桶继续服务列表概况、归档与导出。正式统计仍只取PRODUCTION_UP，其他UP用途留原始、正常DOWN不补存；逐点表通过有界续读最终取得选中曲线全部已存点，不用抽样结果冒充完整表。

正式页面由`/api/command-center/...`按`systemCode`查询，具体契约见[对外接口说明](../external-api-contract.md)。当前`/api/cleaning/devices/{deviceId}`仍是已实现代码的设备接口，不继续作为新页面历史设计的另一套入口。

## 2. 建议的目标结构：10张业务表

复用现有账号与三张拓扑表，增加六张历史/统计表。重算标记放在周期表，无需另建任务表。迁移期原三张批次历史表另外保留，因此实际表数会暂时多于目标结构，本文不删除旧表。

这里“无需另建任务表”指统计重算。导出记录列表另需`cleaning_export_task`技术表，目标为10业务表＋1导出任务表；字段及流程见[导出方案](../data-export-plan.md)。文件本体使用持久文件存储，不放数据库BLOB，当前7表不因设计而变更。

| 表 | 一行表示什么 | 对应页面 |
|---|---|---|
| `t_user`，沿用 | 一个登录账号 | 可验证后台操作的身份 |
| `cleaning_system`，扩展 | 一套系统与PLC绑定、版本化参数 | 首页档案、系统上下文、参数查询 |
| `cleaning_winch`，扩展 | 一台动力设备 | 单机档案/配置 |
| `cleaning_rope_sensor`，扩展 | 一个逻辑钢绳位置与传感器绑定 | 两索身份/阈值 |
| `cleaning_run_record`，新增设计 | 一次已识别的上行记录 | 列表、当前/上一条、单次概况 |
| `cleaning_up_sample`，新增设计 | 一帧选中归档的上行测量 | 原始曲线、悬停、明细导出 |
| `cleaning_run_winch_day`，新增设计 | 一次运行、一台设备、一个采集日、一个运动用途的摘要 | 本机本次统计、跨日时长、参与记录去重 |
| `cleaning_exception_event`，新增设计 | 一次事件及有限快照，告警是其中明确标记的业务视图 | 告警中心、上/下行异常历史，不恢复旧通用告警表 |
| `cleaning_period_summary`，新增设计 | 系统或单机的一个自然周期结果 | 日/月/季/半年/年卡片、日期目录 |
| `cleaning_height_bucket`，新增设计 | 某周期、本机、某来源、一个高度区间的统计 | 高度—拉力平均曲线 |

概念关系如下，不代表每条关联都要求物理外键：

```mermaid
erDiagram
    t_user o|--o{ cleaning_run_record : operates
    t_user o|--o{ cleaning_exception_event : acts
    cleaning_system ||--o{ cleaning_winch : contains
    cleaning_winch ||--o{ cleaning_rope_sensor : binds
    cleaning_system ||--o{ cleaning_run_record : records
    cleaning_run_record o|--o{ cleaning_up_sample : associates
    cleaning_run_record ||--o{ cleaning_run_winch_day : summarizes
    cleaning_winch ||--o{ cleaning_run_winch_day : participates
    cleaning_system ||--o{ cleaning_exception_event : observes
    cleaning_run_record o|--o{ cleaning_exception_event : relates
    cleaning_system ||--o{ cleaning_period_summary : reports
    cleaning_winch ||--o{ cleaning_height_bucket : aggregates
```

## 3. 身份、单位和时间约定

- 内部仍沿用`system_id、device_id、winch_id、rope_id、tension_sensor_id`及其现有关系，不将通信身份改成页面编号，不恢复盒子身份。
- 页面公共编号为`systemCode、winchCode、sensorCode`，分别映射`system_code、winch_code、sensor_code`。例如QJXT-01是系统公共编号，不是四台设备共用的内部键；WJ-01、PS-01也是配置值，不按数组位置或后缀猜身份。公共编码的唯一映射由服务端完成，响应、推送、命令和导出使用同一编号。
- 新运行建议使用紧凑的`BIGINT UNSIGNED`内部主键及唯一公开`record_code`。页面`id/recordCode`用公开编号，采样关联内部键，避免每帧重复长编号。
- 采样内部自增ID只用于数据库定位；重放去重使用稳定的`device_id + capture_id + sample_seq`，采集实例跨重启不复用，重放沿用原标识。
- 库内统一kg、m、m/min；电机存公式对应的等效kg及必要原值，不标成N·m。新页面接口由后端输出t；前端切换kg时按明确单位格式化，不能把已为t的数值再除1000。当前旧设备接口输出kg属于代码现状，不能混入新契约。
- 时刻用`DATETIME(3)`保存UTC，应用与连接显式使用UTC；业务`DATE`按`Asia/Shanghai`计算。`DATETIME`类型本身不保存时区。数据库查询内部左闭右开；新告警历史CUSTOM页面输入首尾日期包含，服务端转换为开始日零点至结束日次日零点，不能把同名endDate直接当排除日。
- 不校验采集时间的早晚、年龄或与当前时钟的偏差，不因缓冲延迟、跨日或早于上一帧而拒收/丢弃。时间列只用于记录、查询、曲线位置、历时和日历统计；唯一采样标识负责去重，不以时间作数据有效性条件。
- 数据库可空字段保存NULL，新页面声明的可空字段保留键并明确返回JSON null，不能沿用当前NON_NULL序列化的省略行为。正常下行未归档、传感器无效和配置未取得分别说明；有效0保留。下面数值类型为建议存储精度，不代表现场精度或机械范围。
- 页面五态的待机名称统一为`IDLE`；当前代码`STANDBY`需要在实施中适配。数据表保留PLC原始状态及业务规则来源，不能将修改页面枚举理解为改写PLC码或以时间推断离线。

## 4. 现有三张设备表如何扩展

保留现有身份、绑定和唯一约束，不为页面复制第二套设备档案。

| 表 | 保留字段 | 建议补充的页面配置 |
|---|---|---|
| `cleaning_system` | `system_id、device_id、tank_id、ring_id、ring_own_weight_kg` | `system_code、system_name、configured_height_m、height_limit_min_m、height_limit_max_m`及下述参数版本/快照 |
| `cleaning_winch` | `winch_id、system_id、slot_no` | `winch_code、winch_name、last_maintenance_at`及两路阈值保存/生效快照 |
| `cleaning_rope_sensor` | `rope_id、winch_id、slot_no、tension_sensor_id` | `sensor_code、sensor_name、business_role、installation_position、warning_threshold_kg、protection_threshold_kg、threshold_version、threshold_rule_snapshot` |

名称/编号用VARCHAR；高度建议DECIMAL(10,3)，拉力DECIMAL(14,4)，速度DECIMAL(10,4)，维护时间DATETIME(3)。`system_code`唯一；`(system_id, winch_code)`唯一；`sensor_code`唯一并通过钢绳位置关联本机。公共编号选定后保持稳定，名称变化不改变内部关联。

此前拟议的`display_code`改由`system_code`表达公开系统编号，不再同时保留两套含义相同的页面系统编码。若未来迁移来源已有display_code，应显式映射而非自动猜值。`tank_id`只是现有内部关联，不作为页面systemCode的默认替代。

`rated_load_kg、rated_speed_mpm、monitor_min_kg、monitor_max_kg`不再作为本次页面要求新增的列；这不授权删除已有数据库扩展列或历史数据。预警/保护各自使用明确配置及规则，缺少配置保存NULL，不填27m、30m、1.4t等显示/示例值。

阈值版本及有限规则快照记录PLC实际使用的参数、适用对象及生效证据。拉力状态和普通/严重事件由PLC判断，后端不比较每笔拉力与数据库阈值新建故障；清洗模块解释已确认PLC状态，缺少信号语义则明确未确认。修改参数、保存阈值或普通读数变化不产生事件，也不直接改写设备工况。

页面目前只有最近维护时间，先不增加维护工单体系；需要维护历史再扩展。单机独立限位有明确需求和配置时再增加，不复制系统限位冒充本机实测。

PLC连接地址与轮询参数继续按现有接入配置管理。最新页面新增阈值修改目标，但真实写入点位、类型、倍率及生效回执仍待确认；不把新增接口声明当作现场可用能力。

### 4.1 三参数的保存与生效

为保持目标表数量，当前系统参数在`cleaning_system`内存储有限版本快照，不新增另一套设备配置表：

| 字段组 | 作用 |
|---|---|
| `settings_saved_version / settings_saved_snapshot / settings_saved_at` | 平台已成功保存的版本、三参数值与保存时刻 |
| `settings_applied_version / settings_applied_snapshot / settings_applied_at` | 经过已明确设备反馈确认生效的版本与值；未确认时可空 |
| `settings_apply_state / settings_message / settings_source_snapshot` | 保存、下发、未确认或生效的真实阶段，以及来源/反馈证据的有限快照 |

版本建议BIGINT；参数快照只放启动时间、运行间隔、运行速度及明确单位/规则版本。内部间隔可继续统一秒；页面`intervalMinutes`与内部秒数由后端转换。页面`speedMPerMinute`和详情`setSpeedMPerMinute`均为设定速度，不读取D61～D65实测速度来补值。

`setSpeedMPerMinute`作为成功详情的必返有效配置，应取已确认适用版本中的设定速度及作用范围。平台保存但设备尚未确认生效时，不覆盖已确认生效视图；从未取得有效设定值时不能用0、默认值或额定速度满足字段要求，应返回配置尚未就绪的明确结果。具体响应由接口契约统一。

尚未明确D100～D103、D105～D108的编码和读回关系，本轮不填映射，也不宣称这些参数已经联通。未来确认生效需要数据库快照和设备反馈共同闭环；当前仅内存设置视图不满足本设计。保存时刻、确认时刻只记录配置事实，不增加采集数据时间校验。

### 4.2 两路阈值的保存与生效

目标`PUT /api/command-center/winch/tension-thresholds`按真实systemCode、winchCode及两路sensorCode提交alarmThreshold/safetyValue，输入固定t，后端统一换kg存储。只做所属关系、两路完整不重复、有限正数、预警小于保护和已确认现场范围等必要检查；不把图形1.4t/3t当安全上限。

在`cleaning_winch`增加有限两路快照：`threshold_saved_version/snapshot/at`、`threshold_applied_version/snapshot/at`、`threshold_apply_state`及关联命令/确认依据。`cleaning_rope_sensor.warning_threshold_kg/protection_threshold_kg/threshold_version`仅代表已确认生效值。PENDING只保存申请，不覆盖旧有效视图；PLC两路完整生效确认后，按同一真实配置版本在短事务中更新本机applied快照及两路有效列。部分写入、只收到通信写应答或身份不符都不能APPLIED；保留真实未确认/失败原因。

保存与生效版本服务并发保护和追溯，不能由浏览器localStorage或默认数值伪造。生效协议未确认时不实现猜测下发，也不虚报成功；事件触发时另保存当时适用阈值，之后改参数不回写旧事件。本设计复用现有拓扑表，不为弹窗新增一套历史业务表。

## 5. 运行主表：直接支撑列表与七个概况

### `cleaning_run_record`

| 字段 | 建议类型 | 含义 |
|---|---|---|
| `id / record_code` | BIGINT UNSIGNED主键 / VARCHAR(64)唯一 | 内部关联与页面公开编号 |
| `system_id / device_id` | VARCHAR(64) | 所属系统、PLC来源 |
| `record_use / mode` | VARCHAR，允许未确认 | 生产/检修/回退等用途与AUTO/MANUAL模式，不混为启动方式 |
| `started_at / ended_at` | DATETIME(3)，可空 | 已确认实际边界，进行中不填未来结束时间 |
| `first_observed_at / last_observed_at` | DATETIME(3) | 首次/最后观测事实，不冒充未知实际起止 |
| `business_date` | DATE，可空 | 已确认开始时刻的北京时间日期，用于计数归属 |
| `state / result / termination_reason` | VARCHAR业务枚举 | 是否结束、是否确认完成、真实退出原因分开；清洗模块负责最终判定 |
| `start_method` | VARCHAR(24)，可空 | 有实际来源才填，不从AUTO推定定时 |
| `operator_user_id` | BIGINT，可空 | 可验证后台操作账号，现场动作不套用在线用户 |
| `rope_min_kg / rope_max_kg` | DECIMAL(14,4)，可空 | 本次PRODUCTION_UP全系统8路单绳有效采样集合的单个极值，不含回退/检修、电机、合计或下行快照 |
| `abnormal_count / has_data_gap` | BIGINT UNSIGNED / BOOLEAN | 属于本次的异常数、已知数据缺口，不是过期判断 |
| `parameter_snapshot / identity_snapshot` | 有限JSON对象 | 开始时已确认的参数值/生效版本、作用范围；当时公共编码/名称与内部身份映射、单位/换算规则版本 |
| `result_rule_version / result_snapshot` | VARCHAR / 有限JSON，可空 | 形成结果的实际规则版本和依据；必要的历史说明保留来源，不拿当前状态重算旧结果 |

本版`kind=RUN、recordScope=LEG、directionScope=UP`由接口约定输出，不必每行重复常量。一次系统同步上行是一条系统记录，四台参与不变成四次系统运行。

`durationSeconds`由已知起止相减，格式化时长不入库；有效运动时长另读分日摘要。列表常用极值和异常数存派生摘要，避免每页十条都读取十份原始曲线。entry.maxTension/minTension始终是本记录全系统8条单绳集合的极值，不随当前winchCode、图例或鼠标变化，不将8路相加；缺通道/观测缺口保留覆盖说明，不能声称缺失部分也已观测。

后端返回`runResultText、endReasonText、startMethodText`及本次设定速度、间隔等直接展示字段；文本根据保存的结论码、规则版本及历史快照生成，前端不从事件数组推测结果。无来源的启动方式或参数显式返回null及说明；不以当前已修改参数补写旧记录，不为历史处理次数制造0或五次。

单条RUN的`runCount=1`及各结果计数0/1由结构化结果生成，不在主表重复保存一整套周期计数字段。开始未知时不造`started_at/business_date`，按首次观测在边界未确认范围查询，不混入已确认开始日计数。

## 6. 上行采样表：一帧一行

### `cleaning_up_sample`

| 字段 | 建议类型 | 含义 |
|---|---|---|
| `id` | BIGINT UNSIGNED主键 | 内部采样键 |
| `device_id` | VARCHAR(64) | PLC来源 |
| `capture_id / sample_seq` | BINARY(16) / BIGINT UNSIGNED | 稳定采集实例与序号，组合去重 |
| `record_id` | BIGINT UNSIGNED，可空 | 已确认业务归属，不强挂最近一条记录 |
| `sampled_at / received_at` | DATETIME(3) | 采集时刻/入库时刻，统计使用前者 |
| `group_speed_mpm` | DECIMAL(10,4)，可空 | 独立保存同步速度 |
| `system_state_code / wN_state_code` | SMALLINT，允许缺测 | 实际D201与D203～D206原码，状态缺测不当离线 |
| `wN_archived`，N=1…4 | BOOLEAN | 本帧本机上行测量是否归档 |
| `wN_motion_role / wN_segment_code` | VARCHAR(24) / VARCHAR(64) | 本机生产、回退、检修、未确认上行用途及连续分段；后端分类，不是猜PLC阶段码 |
| `wN_height_m / wN_speed_mpm` | DECIMAL(10,3) / DECIMAL(10,4)，可空 | 本机高度与独立速度 |
| `wN_rope1_kg / wN_rope2_kg` | DECIMAL(14,4)，可空 | 两根钢绳各自测量 |
| `wN_motor_raw / wN_motor_force_kg` | BIGINT / DECIMAL(14,4)，可空 | 正确解码原值及已明确公式的等效力 |
| `wN_quality` | SMALLINT UNSIGNED，通道质量标记 | 返回页面时展开到各通道，不是PLC寄存器编码 |
| `mapping_snapshot` | 有限JSON，可空 | 无记录或与记录快照不一致时保留必要公共编码、名称、内部身份/换算规则来源 |

`wN_...`落地时展开成四组固定数值列，不是一列自由拼装测量JSON。小型身份/规则快照可以JSON，核心曲线保留数值列。

混合方向仅归档UP正常测量，正常下降通道标记未归档；下行异常另存事件快照。上行某通道无效，保留质量和其他有效通道。

同一生产记录内也可能包含RECOVERY_UP，不能仅用主表record_use判断样本用途。默认生产摘要/平均只取本机PRODUCTION_UP；其余上行原始保留，不增加生产次数。

有真实依据修正用途时保留原采样标识/PLC事实，重建旧/新角色摘要、主记录生产极值及受影响周期，不复制原始点，不让旧用途统计残留。

同步速度和本机独立速度分别保存，只在工况已明确时选择适用值，不无条件复制整组速度冒充四个独立实测。帧标识只表示采集关联，采集实现仍须满足明确一致性口径，不能擅自设置同步容差。

两索总拉力由后端以已明确的同帧、本机、方向、用途和通道有效性证据配对，再输出`ROPE_TOTAL`序列、质量及统计；不能靠相同高度或最接近时间补配。配对不成立时总拉力为null，原两路有效值仍保留。用于核对配对的来源事实不构成采样时间年龄/早晚校验，也不删除没有合计条件的原始采样。页面统一使用`sampleKey`，取代此前未实施的pointKey；按稳定帧、本机和分段关联生成，同一unit各来源共享可对照键，不因高度相同复用其他经过的键。sampleFrameId仍指实际帧，不与页面键合并，也无需为改名另建采样表。

总拉力点的速度来自该帧本机已确认适用速度，未知为null，不相加两索速度或默认复制钢绳1。周期总力速度只合并配对集合内有效速度的和/数量，其有效数可以少于有效拉力数。GOOD/PARTIAL仅在该测量有有效依据时计数，合法0正常保留；原始点sampleCount有效为1、无效为0，聚合点为真实有效数，缺失不按1补权重。

不能只保存整米点：真实时间、速度、峰值和原始导出都依赖过程采样。高度桶用于统计/展示，不覆盖原始帧，不为0～30m补点。断线、换向或回退后的片段不能按高度排序串成同一条连续实线。

## 7. 运行设备分日摘要：处理单机与跨天

### `cleaning_run_winch_day`

主键建议`(record_id, winch_id, stat_date, motion_role)`，日期为采集/已确认运动区间所属的北京时间日期，用途按每台实际归属拆分。

| 字段组 | 内容 |
|---|---|
| `record_id、winch_id、stat_date、motion_role` | 一次运行、本机、一天、一个用途；跨午夜或用途不同分别成行 |
| `first_sample_at / last_sample_at` | 实际参与采样范围 |
| `motion_duration_ms` | 可空，当日本用途可靠上行区间毫秒数；未知不填0，不与其他用途混算 |
| `height_min_m / height_max_m` | 本机当日实际高度范围 |
| `rope1_* / rope2_* / motor_* / pair_*` | 各自的`sum_kg、valid_count、min_kg、max_kg`，pair仅来自同帧两索均有效集合 |
| `speed_sum / speed_valid_count` | 适用速度自己的有效和/数量 |
| `curve_rope1_count / curve_rope2_count / curve_motor_count / curve_pair_count` | 同时有有效本机高度并满足UP曲线规则的各来源样本数 |
| `curve_rope1_* / curve_rope2_* / curve_motor_* / curve_pair_*` | 与上述count同一集合的sum_kg、min_kg、max_kg；支持完整COUNT曲线统计，不能直接套用包含缺高度测量的原始摘要 |
| `pair_speed_sum / pair_speed_valid_count` | 配对有效拉力集合中、本机适用速度有依据的和/数量；速度未知不补0，也不沿用独立单绳集合 |
| `has_data_gap` | 已知缺口说明 |

数值和建议DECIMAL(30,6)，数量BIGINT UNSIGNED，单值极值DECIMAL(14,4)。同次详情合并本机少量分日行：和/数量求平均、极值分别取最小/最大，不平均平均值。

默认本次生产摘要、周期生产计数对应测量、有效运动时长和曲线均明确筛选PRODUCTION_UP；检修/回退工时另列用途。平均曲线仍保留有效异常峰值，不能因报警就删去生产测量。

高度缺失但拉力有效时，仍可参加记录拉力极值，却不能成为高度曲线点，因此一般测量数量与`curve_*_count`分开。未归属记录的原始上行采样保留，不自动混入生产计数/均值。

curve_*保留完整可画PRODUCTION_UP曲线统计，原始rope1/rope2/motor/pair摘要保留缺高度有效测量，两种集合不能混用。新版页面已删除右侧均值/极值双表，不再强制输出series.statistics/rawStatistics或unit.ropeExtrema；这些摘要继续供运行头、归档和明确报表使用，不随图例、分页或降采样改值。总力极值来自逐点有效配对后的pair集合，不能将单绳各自极值相加。

本机今日次数按主表开始日及参与关系去重；不能因四台同步或跨日行数增加系统次数。某来源参与周期曲线的记录数，在目标采集日期内对`curve_*_count>0`的记录去重并预计算；一条运行跨两天，不能把两天各1条相加当月2条。

## 8. 异常事件表：一次持续过程一行

### `cleaning_exception_event`

| 字段 | 规则 |
|---|---|
| `id / event_code` | 内部主键、唯一稳定公开编号；告警alarmCode映射此编号，持续、升级、结束不换号 |
| `system_id / device_id` | 系统及PLC来源，公开deviceCode展示实际动力设备/现场设备，不当内部PLC键 |
| `winch_id / rope_id / sensor_id / record_id` | 可空；内部通道区分同时发生的不同过程；record_id仅真实已确认关联，不能因换轮自动换事件号 |
| `controller_session_id / plc_event_id / source_key` | 已确认的控制器会话、PLC过程编号或通道/错误类型身份；保留真实原值，没有来源不猜 |
| `source_kind / is_alarm` | PLC信号或已确认连接监测的PLATFORM事件；不根据拉力比较阈值自行产生事件 |
| `target_type / target_snapshot` | 真实对象及触发时身份/名称；页面只显示所属设备，内部仍保留通道定位 |
| `direction / occurred_height_m / fault_code / content` | 发生方向、高度及PLC错误原文，不从颜色/阈值改写错误 |
| `occurred_at / occurred_at_source / first_observed_at` | 真实开始与首次观测分开；真实时刻未知时保留NULL和来源说明 |
| `updated_at / revision` | 后端接受的事件更新时刻与递增版本；重复上报不增加事件数量，不以轮询次数伪造版本 |
| `event_state / ended_at` | ACTIVE/ENDED及明确现场过程结束时刻；断联、页面关闭、归档或换轮均不等于结束 |
| `continuity / source_update_snapshot` | 连续性/缺口说明及有限原始信号依据；无可靠结束信号时保持未确认，不编造已结束 |
| `severity / threshold_kind` | PLC明确提供的NORMAL/SERIOUS及阈值类别；等级当前值或过程最高值的现场语义需确认，不根据拉力推算 |
| `trigger_value_kg / peak_value_kg / peak_at / peak_source / peak_coverage` | 触发值、已知过程峰值/时刻、PLC提供或观测统计来源及覆盖；无峰值依据不以触发值补造 |
| `threshold_kg / threshold_version / threshold_snapshot` | 当时适用阈值及配置/生效证据；后续设置不覆盖历史 |
| `rule_version / explanation_snapshot` | 事件身份/信号解释规则及必要事实说明，不再生成当前页面未显示的人工处理流程 |
| `trigger_snapshot / end_snapshot / peak_snapshot` | 有限必要事件点，高度/速度/两索/电机/质量；不保存连续DOWN窗口 |
| `recovered_at / terminated_at / outcome / archived_at` | 若旧业务已有真实事实可保留审计；不替代event_state/ended_at，不表示当前新增处理/恢复按钮 |
| `operator_user_id` | 仅可验证后台动作存在时填写，PLC自行上报不套用在线浏览账号 |

一次过程首次开始分配稳定event_code，持续和普通升级严重更新同一行及revision，PLC明确结束只更新event_state/ended_at，后续新过程才创建新号。等级降回普通是否同一过程及level表示当前还是最高等级仍按现场明确语义，不由数值猜测。每次写入连同重放标识/待重算标记提交，重放不重复计数。

PLC有稳定过程号时按系统、控制器会话和PLC编号命名空间去重；没有时由可靠开始/变化/结束信号形成可持久恢复的源过程关联。不能只用deviceCode合并同机两路事件，也不能把后端重启/PLC编号复用当原过程延续。已确认唯一来源键才建立对应唯一约束；可空字段组合不能冒充完整去重机制。未知过程协议明确记录待接，不用新增数据库表掩盖缺失信号。

跨轮持续事件不换alarmCode，record_id只保存已确认主归属；多运行关联规则未定时不强挂最近一条，也不在有限JSON中累积无限轮次。下行事件可以没有record_id，仍独立保存查询；持续过程不能因运行结束反向伪造告警结束或恢复。

无PLC准确开始时刻时occurred_at保留NULL，first_observed_at保存真实观测。页面occurredAt使用实际/首次观测对应的显示时刻，明确timeBasis=ACTUAL/FIRST_OBSERVED；actualOccurredAt单独保留可空真实时刻。查询日期、排序和导出用同一说明口径，不把观测时刻称为精确物理发生，也不校验采样年龄或先后。

持续更新不能把起点改成最新采样时间。后续补得明确真实发生时刻时，可作为同号事实修订补齐actualOccurredAt及timeBasis，推进revision并重建受影响日期统计；不创建第二条事件，不把更正解释为再次发生。

峰值可直接来自PLC，或在PLC已开启的事件过程中增量整理对应有效采样。该计算不重新判断是否故障。DOWN过程仍不连续落采样库，只更新事件峰值标量及有限峰值点；存在断联、首次观测较晚或缺通道时标观测覆盖不完整，不声称掌握整个下降全程峰值。原有UP原始保存和事件峰值统计分别去重，质量无效不补0。

告警查询改为`/api/command-center/alarms/latest`及`/api/command-center/alarms/history`，不再将alarm-dashboard、旧统计卡或处理结果作为新页面要求。latest读取所有持续/结束未确认事件及明确近期窗口内已结束事件，跨日持续项不能被TODAY裁掉；hasAttention由后端按已确认PLC/连接事件给出，进入页面不清除。历史先按上海发生日期、等级、设备筛选再分页，total按稳定事件编号计数，升级/结束不增加条数。CUSTOM首尾日期包含，数据库内部转换右开区间，实际窗口回显。

recent完整集合与历史页互不补入。latest的近期展示窗口只决定列表成员，不删除永久事件；若完整集合超响应预算，应明确未能提供完整快照而不能静默截断。非告警技术事件保留is_alarm=false，不强改为普通告警；无RUN关联异常仍可通过`/api/command-center/events/history`查询、`/api/command-center/events/{eventCode}`查看详情或导出追溯，不因移除alarm-dashboard失去入口。

当前页面只普通/严重及所属设备，没有类型、内部部件或人工确认/处理/恢复操作。旧activeCount/todayRecoveredCount等不再强制输出，也不为删除的卡片增加统计列。事件次数、保护异常次数和无来源处理尝试数分别保留含义；D3=50不证明五次过程明细，未知不补写。

## 9. 周期统计表：报表、计数、日期目录

### `cleaning_period_summary`

同一张表覆盖系统/单机及日/月/季度/半年/年。

| 字段组 | 内容 |
|---|---|
| `system_id、scope_kind、scope_id` | 范围仅SYSTEM/WINCH；scope_id非空，分别为真实系统/动力设备ID |
| `period_type、period_start、period_end` | 自然周期类型，按北京时间计算后存UTC，左闭右开 |
| `run_count` | 本周期确认开始的生产上行数 |
| `completed_count、normal_completed_count、recovered_count` | 上述记录中完成、正常、异常恢复后完成的数量 |
| `terminated_count、ongoing_count、unconfirmed_count` | 明确终止、仍进行、结果未确认的数量 |
| `up_event_count / down_event_count / stopped_event_count / unknown_direction_event_count` | 按已确认发生/首次观测日期统计，与记录计数分开；已知停止和未知方向分别留存 |
| `motion_duration_ms` | 落在本周期内的可靠上行运动区间合计 |
| `rope_min_kg / rope_max_kg` | 本周期采集日期内有效上行钢绳极值 |
| `rope1_* / rope2_* / motor_* / pair_*` | WINCH范围各来源sum_kg、valid_count、min_kg、max_kg等可合并统计，供运行摘要与归档比较使用；不从曲线缩减点反推 |
| `curve_any_record_count / curve_rope1_record_count / curve_rope2_record_count / curve_motor_record_count / curve_pair_record_count` | WINCH范围曲线组及各来源参与本期曲线的去重记录数；组数量按来源并集去重，不相加各来源数 |
| `data_from / data_through / has_data_gap` | 已纳入的数据范围与已知缺口，不用于过期判断 |
| `rule_version、rule_snapshot、computed_version、requested_version、needs_rebuild、built_at` | 规则版本与有限规则快照、计算版本及持久重算标记 |

唯一键`(system_id, scope_kind, scope_id, period_type, period_start)`。不以`winch_id=NULL`代表系统构造可空唯一键，否则MySQL可能允许重复系统周期行。

**归属分开：记录计数与结果按开始日；测量/曲线按采集日；事件按实际可确认发生/观测日；时长按区间交集拆分。** 例如22日23:58开始、23日00:08结束，结果回填22日记录组；可靠运动时长拆为两日2/8分钟，采样各归实际日期。

周期测量摘要可合并设备分日摘要，因此不会把跨日整条记录的极值全归开始日；没有高度的有效拉力也不因未进高度桶而遗漏。单机参与曲线的记录数从轻量分日摘要去重，不能相加每日参与数。

系统同步运行次数不相加四台设备行。系统运动历时与四台设备工时合计分别命名。单机累计小时读取不重叠的“已结束年＋当前年已结束月＋当前月已结束日＋当日”运动时长，除3600000，不把年/月/日重复相加，不由采样数乘固定间隔推算。

本版没有独立、可持久重建的系统上行运动区间来源时，SYSTEM范围的motion_duration_ms为空；四台日总量既不能求和当系统历时，也不足以还原区间并集。WINCH范围默认只累计PRODUCTION_UP，未知部分保留覆盖说明，不能让SUM忽略NULL后冒充完整累计。

原始数据/事件/结果提交时，同事务将受影响日范围标记待更新并推进requested_version。后台重建日结果/高度桶，提交时推进计算版本并标记上级周期。若期间又有数据，保留后续重算标记，不清掉新工作。

无需新增任务表。首次未算好的built_at为空，显示统计准备中，不把占位行当0次。历史月份提前生成保存，查询只读结果；补传或结果变化仅重算受影响范围，重试覆盖结果而非在旧总数上再加一次。

“数据归档”页面的变化图、左右比较序列和summary由统计模块从已保存周期/分日摘要准备。自选范围保留左图、当前和参考三组完整边界；后端回传实际范围和对应轴，不让浏览器按数组下标拼接。同比采用接口约定的真实同期，不能把整年和今年截至时刻混作等长比较；无参考数据、参考均值为0或覆盖不足时，变化率为null并说明。历史月份继续复用已算结果，不以页面一次请求为由重新扫描全年原始帧。

## 10. 高度统计表：为附件平均曲线单独设计

### `cleaning_height_bucket`

| 字段组 | 内容 |
|---|---|
| `system_id / winch_id` | 实际系统、本机身份 |
| `source_role` | SENSOR1/SENSOR2/SERVO/ROPE_TOTAL，配对总力单独累计；此前方案PAIRED_SUM统一映射为ROPE_TOTAL，本表尚未实施 |
| `period_type / period_start / period_end` | 日/月/季度/半年/年实际范围 |
| `height_rule_version / bucket_index` | 明确的高度分箱规则与区间编号 |
| `height_from_m / height_to_m` | 统计高度边界，不是机械限位 |
| `sum_kg / valid_count` | DECIMAL(30,6)建议 / BIGINT UNSIGNED，有效和与数量 |
| `min_kg / max_kg` | DECIMAL(14,4)，该桶原始有效值极值 |
| `speed_sum / speed_valid_count` | 对应速度自己的和与数量 |
| `data_from / data_through / computed_version` | 实际参与范围、结果版本 |

唯一键`(system_id, winch_id, source_role, period_type, period_start, height_rule_version, bucket_index)`。按定义只聚合UP，生产用途/有效性/分箱规则固定在规则版本中。换规则需重建受影响结果，查询不能混合两个版本。

宽度依据确认的测量精度配置，本文不猜现场数值。平均=sum/valid_count，合并日/月保留和/数量/极值，不等权平均均值。配对总力只用同帧两索均有效集合，不把独立均值直接相加。

周期曲线按逻辑钢绳位置/电机角色聚合；更换传感器时，不能用当前sensorId冒充整个历史的物理来源，具体原始身份从记录/采样快照追溯。

高度桶sampleCount是该来源有效样本数，参与运行数必须按记录去重，不能作平均权重。内部继续维护曲线来源参与数量及并集，页面不因旧DTO名称而同时输出多套计数。

周期报表需要均值时，从完整基础桶sum合计/count合计得到；“最高/最低平均”取基础桶均值极值，原始瞬时极值从原始周期摘要取得，缺高度有效测量仍进入原始摘要。ROPE_TOTAL只聚合共同有效配对集合。图形缩减、显示合桶和分页不改变已定义的统计结果。

新版逐点表不强制返回statistics/rawStatistics/ropeExtrema/defaultUnitId，但删除展示不等于删除预计算与数据能力。基础高度桶仍持久保存供动力平均图、归档和导出，后端无需重扫全年原始帧。平均点sampleKey由周期、本机、规则/计算版本及桶定位关联，sampleFrameId/time为null，不冒充现场帧。

无样本高度为空，不按数组下标拼同高度，不造0线；平均点标为统计坐标，不能冒充真实采样时间/高度。

单次顶部极值、周期原始测量极值、平均曲线各桶均值的极值三者分开，默认均按已明确的生产UP范围，不纳入回退/检修。不能从已简化图表反推原始极值。已结束月份的高度统计提前保存，不能用时间桶平均代替高度桶。

## 11. 页面字段与数据库逐项对应

| 页面字段 | 来源 | 接口处理 |
|---|---|---|
| `systemCode / systemName / systemHeight` | `cleaning_system.system_code / system_name / configured_height_m` | 公共编号映射真实内部系统，配置高度与当前高度分别命名，不默认TANK-03 |
| 首页状态/方向/当前拉力高度速度 | 最新业务快照＋已确认配置 | 上下行每秒完整推送，状态/质量/说明均由后端给出，不轮询数据库最新一行 |
| `systemCurrentHeight` | 独立系统/机构高度来源 | 无来源返回null及说明，不拿四机均高冒充 |
| `winchCode / winchName / groupNo` | `cleaning_winch`公共编号、名称、slot_no | 服务端解析内部winch_id，直接返回名称；额定载荷/速度退出本次页面字段 |
| `sensorCode / sensorName / metric / alarmThreshold / safetyValue` | `cleaning_rope_sensor`及有效阈值版本 | 后端返回真实身份、t单位阈值及状态结论；前端不根据读数与阈值推导 |
| `maintenanceAt` | `cleaning_winch.last_maintenance_at` | 无来源显示暂无数据 |
| `setSpeedMPerMinute` | `cleaning_system.settings_applied_snapshot`及已确认作用范围 | 成功详情必返真实有效设定速度；无值返回配置未就绪，不用实测速度或默认值填补 |
| `ROPE_TOTAL / torqueForce` | 最新快照或相应历史事实 | 后端两索严格配对求和并返回质量；电机独立反馈，不用两索之和替代 |
| `todayCompletedCount` | DAY＋WINCH周期行 | 今日开始的上行中确认完成的数量，不改成所有今日结束记录 |
| `runningHours` | 不重叠周期PRODUCTION_UP运动时长 | 明确已确认覆盖范围，不混入回退/检修或假称完整上下行累计 |
| 可选当前/上一条记录 | 运行主表＋本机参与关系 | 最小详情不强制这些未展示字段；确有用途时稳定排序，无记录返回null |
| 历史`entries / total` | 运行主表分页/条件计数 | 每页10条，按一次生产上行；不含无限原始曲线数组 |
| `availableDates / dataRange` | 真实记录日期目录或轻量主记录索引 | includeAvailableDates时返回全系统真实可查询日期，不受当前页/当前日期筛选截断；未确认边界单独说明 |
| 单次entry及结果中文、设定速度/间隔 | 主表＋历史参数/身份/结果快照 | 直接给最终结论、实际起止、时长和单位；不取当前参数补旧记录 |
| 历史`sensors / servos`与逐点表 | 已保存上行采样选定范围 | ROPE1/ROPE2/SERVO三来源、真实绳号/身份、顺序/分段和稳定键；有界分批最终完整，不强加总力或旧双表统计 |
| 动力页曲线与合计 | 上行采样、基础高度桶及真实两索配对 | 单台四范围查询需要时由后端返回ROPE_TOTAL；当前页面SUM需适配，不能继续前端配对 |
| 概况/归档/报表统计 | 运行分日和周期摘要 | 保留计算能力；statistics/rawStatistics/ropeExtrema/defaultUnitId不因旧UI成为页面必返 |
| 悬停实际值和总力 | 指定记录/分段/帧的上行采样 | 后端提供sampleKey及已处理读数，前端不自行配对，不用别的时间拼同步读数 |
| `exceptionEvents / abnormalCount` | 事件表＋记录摘要 | 当前页长度与异常总数分开 |
| 独立下行异常 | 事件表DOWN事件 | 无RUN关联仍可通过事件查询/导出发现；is_alarm=false不强改为普通告警，不画完整下降线 |
| 最新recent、hasAttention | 同一事件表的持续及近期过程 | 独立latest完整快照；全系统红点由后端给出，事件开始跨日不丢失 |
| 历史告警list、total | PLC/已确认PLATFORM事件及触发快照 | 独立history按日期/等级/设备筛选分页，同号更新revision；ENDED不代表人工处理/恢复 |
| 阈值PENDING/APPLIED | 本机保存/生效快照＋两路实际配置 | PENDING保留旧值，确认两路实际生效才更新显示，按t输入kg存储 |
| 动力统一对比`units / series` | 运行采样、周期摘要＋高度桶 | COUNT/DAY/MONTH/YEAR及逐点表由后端准备；UP、来源、范围/版本明确；无去年同期不造线 |
| 归档`changePoints / comparisonCurrent / comparisonPrevious / summary` | 已保存周期/分日统计 | 后端对齐两图、比较范围与变化率；分母0或无可比数据为null并说明 |
| 原始明细导出 | 后台读上行采样和身份快照 | 不用图表降采样结果代替原始数据 |

名称、rangeLabel、中文statusText、结果文案、缺失原因和entries包装由后端根据配置/事实准备；前端只做字体颜色、小数位、明确单位格式化、勾选和鼠标交互。progressPercent无可靠依据不生成，不用高度除30补造。首页取初始快照后每秒推送；详情和对比图为查询快照，不每5秒刷新，也不复用首页订阅偷偷更新详情。

全系统availableDates只由实际记录生成，不能用尚未计算的周期占位行补日期。可用主表`(system_id,business_date,id)`轻量索引按真实日期去重，或由保存器同步维护DAY目录中的真实记录存在标记；不扫描原始采样，也不从当前筛选结果推算完整日期。日期项目虽可覆盖多年，响应不携带对应原始采样。

## 12. 索引与受限查询

| 查询 | 建议索引/方法 |
|---|---|
| 公共编号解析 | system_code唯一键、`(system_id,winch_code)`唯一键、sensor_code唯一键；同时核对真实系统归属 |
| 系统历史倒序 | 主表`(system_id, started_at, id)`，常用结果筛选再按实际查询补组合索引 |
| 全系统可查询日期 | 主表`(system_id,business_date,id)`去重真实日期或同步维护的轻量DAY目录，不套列表日期筛选 |
| 单机当前/上一条及参与历史 | 分日表`(winch_id, record_id)`；主表EXISTS或先去重，防跨日重复 |
| 本次曲线 | 采样`(record_id, sampled_at, id)`，精确帧用稳定采样标识 |
| 设备原始时间范围 | 采样`(device_id, sampled_at, id)` |
| 采样重放去重 | 唯一`(device_id, capture_id, sample_seq)`，内容冲突要暴露 |
| 分日汇总/来源参与去重 | 分日表`(winch_id, stat_date, record_id)`，用途结合主表筛选 |
| 系统/单次异常时间轴 | 事件`(device_id, first_observed_at, id)`和`(record_id, first_observed_at, id)` |
| 同过程重放去重 | event_code唯一；仅对已确认非空PLC会话/过程来源建立唯一键，内部通道保留，不只按设备合并 |
| 最新持续/近期事件 | 事件`(system_id,is_alarm,event_state,ended_at,id)`及真实发生排序索引；跨日持续不套TODAY |
| 告警历史及所属设备筛选 | 事件系统/显示发生日期/稳定编号索引，按使用补severity或winch_id；筛选后分页，原始通道仍供追溯 |
| 同事件版本更新 | 按event_code与revision条件更新；真实新变化推进版本，重复重放不增加计数 |
| 周期/高度结果 | 使用相应唯一键完整定位或前缀查询 |
| 后台待更新扫描 | 周期`(needs_rebuild, period_start)` |

主键已覆盖的索引不重复建立；原始表不为每个测量值都建索引。先取一页主记录，再批量取摘要，避免旧DAO逐段查多组测点的附加查询。

新页面曲线由统一对比/历史详情接口接收systemCode及本机、记录/周期范围，仍限制单序列及总响应点数。逐点表使用原始点续读，最终覆盖全部已存选择范围；曲线概览可以缩减但不能当完整表。COUNT“全部次数”按有界分组继续。告警latest为完整近期集合，history为后端筛选分页，二者不互相补数据；大范围CSV后台生成，不把数据库全库交给浏览器。

## 13. 写入、迁移和当前缺口

拟议写入为：PLC有序帧 → 上层表示入口/大厅 → 清洗编排选择上行与异常 → 持久待保存缓冲 → 统计保存器/DAO短事务。事务处理采样去重、运行/分日摘要、事件与待更新标记，提交后确认缓冲消费。下层不回调上层，门面委派辅助类。

PLC负责拉力阈值判断，清洗模块解释PLC状态并维护事件过程、归属和运行结果，命令模块维护阈值下发状态；统计模块负责可靠保存、观测峰值、预计算、查询和导出；配置身份由设备管理/DAO提供。新增页面职责不增加第五个清洗业务服务。新页面的中文结论、ROPE_TOTAL、最终统计由对应模块辅助类准备，不能塞回前端或集中在服务门面。

待保存记录使用稳定的公开record_code关联。数据库暂时不可用时仍沿用该编号；DAO入库时取得内部主键再关联采样，不能在重试时生成另一条运行或因暂时没有自增ID而丢掉原有关联。

摘要只累计本次真正新接受的数据，或按确定范围重算覆盖；提交响应丢失后的重放不能重复增加次数、时长和数值之和。页面摘要与原始事实用一套权威口径维护，不各算一套。

采样大表不依赖逐行跨表外键维护生命周期，业务关联由同事务和稳定标识保证；轻量配置/摘要表可保留外键。未来分区须重新审阅MySQL唯一键/外键限制，不能保证现有键不变就直接按月分区。

当前三张旧历史表`cleaning_telemetry_batch、cleaning_telemetry_segment、cleaning_segment_measurement`只有批次/分段统计时，不能迁成假原始点或假高度曲线。实施先新增并验证新链路，旧数据按真实来源保留，审阅迁移后决定退役，不混写两套并称作同一权威。

当前转换器的单机方向仍沿用系统方向，混合方向归档前必须正确取得本机方向事实。当前实时缓冲还会合并旧帧，可靠历史须在覆盖前选出并接收上行/异常。这两项不是新增数据库表就能解决。

永久数据按实际上行归档时长、异常快照及摘要/高度桶/索引/备份估算，不假定恰好减少50%。上行原始与事件不自动删除；已确认入库的待保存文件可回收，不等于删除业务历史。

实施顺序：公共编号/对象名称及参数保存与生效来源 → 运行、采样、含告警快照的事件 → 分日摘要与列表/详情 → 周期/高度预计算与归档比较。未确认的上行完成点位、处理次数、启动来源、配对条件、阈值/保护和限位不补写算法。

本轮只形成表、字段、关联、索引和页面映射设计，没有执行建表、迁移、删表或修改后端实现。当前7表与真实接口仍按原代码运行。
