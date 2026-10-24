# 页面对外接口契约：输入、返回与职责

修订日期：2026-10-24。依据本轮[前后端职责修订原文](reference/frontend-backend-revision-20261024.md)及[对齐决定](frontend-backend-contract-alignment.md)。本文定义新的页面目标接口，不表示路由、字段、每秒完整推送或历史功能已经实现。

**本页只描述目标。** 当前Java实际接口、kg/STANDBY/NON_NULL返回、命令和鉴权限制已移到[当前接口参考](current-api-reference.md)。以前拟议的`/api/cleaning/devices/{deviceId}/history/...`不再作为页面正式目标，不新增两套未来接口供前端猜选；当前七个设备接口尚未因文档修订而迁移。

## 1. 通用规则与接口目录

### 1.1 身份、单位、响应

API基地址为`/api`，下文`C`表示`/api/command-center`。请求统一使用实际公开systemCode，例如配置中的QJXT-01；动力设备用winchCode，传感器用sensorCode。后端解析内部system_id/deviceId/winchId并核对归属，不让客户端同时提交另一套内部键，不回退到固定TANK-03。

请求携带`Authorization: Bearer <访问令牌>`，写入使用JSON，GET没有请求体。操作者从登录身份取得，不接受客户端自报operatorId；授权系统上下文从user-info取得。

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {}
}
```

| 项目 | 新页面约定 |
|---|---|
| 空值 | 定义的可空字段必须出现并为null；空数组[]。0是实际有效值，不表示缺失 |
| 单位 | 页面拉力t、高度m、速度m/min；数据库仍kg。后端页面转换一次，前端可做t/kg显示格式化 |
| 时间 | ISO 8601带时区；业务日历Asia/Shanghai。updatedAt是实际采样/内容时间，asOf是视图读取或统计快照时刻 |
| 查询区间 | 时刻和日期均开始包含、结束排除；格式/查询起止检查不是采样时间校验 |
| 业务输出 | 后端给实际名称、状态/结果中文、质量、原因、配对总力和统计；前端不重新推导 |
| 五态 | 页面IDLE/RUNNING/MAINTENANCE/FAULT/OFFLINE，内部STANDBY可映射IDLE；未知为null＋statusValid=false |
| 类型 | 编号、pointKey、序号、版本为string；数量integer；测量number或null；数组无项[] |

显式null是新页面DTO的实施要求，当前全局NON_NULL尚未改变。下文返回对象均放在Result.data内，文件下载与SSE另有协议。

采集数据不校验早晚、年龄、时钟偏差或是否早于上一帧，不因延迟补入拒收、丢弃或判离线。两索合计基于已确认采集帧、对象、方向、分段和有效性，由后端配对；不猜时间容差，不按相同高度拼接不同过程。

### 1.2 正式页面接口

附件15个HTTP方法/路径在本后端采用下表。认证地址存在，但修订字段及保护仍需适配；其余页面地址属于待实现目标。

| 方法和地址 | 核心输入 | 返回用途 |
|---|---|---|
| POST `/api/auth/login` | username、password | 认证与令牌 |
| POST `/api/auth/logout` | 登录上下文 | 退出处理说明 |
| GET `/api/auth/user-info` | 登录上下文 | 用户、权限、授权系统 |
| POST `/api/auth/refresh` | refreshToken | 刷新结果 |
| GET `C/home` | systemCode | 首页完整初始快照 |
| POST `C/emergency-stop` | systemCode、requestId | 急停受理阶段 |
| GET `C/winch/detail` | systemCode、winchCode | 本机详情快照 |
| GET `C/winch/tension/trend` | systemCode、winchCode、viewType、comparisonView | 四来源曲线与统计 |
| GET `C/archive` | systemCode、指标及比较范围 | 左右图、对齐轴和汇总 |
| GET `C/alarm-dashboard` | systemCode、筛选、分页 | 告警统计/列表/详情 |
| GET `C/history/list` | systemCode、scope、范围、分页 | 单次记录与日期目录 |
| GET `C/history/detail` | systemCode、recordCode | 单次概要、曲线、统计、事件 |
| GET `C/parameter-settings` | systemCode | 已保存与已生效参数 |
| PUT `C/parameter-settings` | systemCode、requestId、expectedVersion、三参数 | 保存/下发阶段 |
| GET `/api/users` | keyword、page、pageSize | 只读用户列表 |

另外确定必要的目标传输/后续读取入口：`GET C/home/stream`、`GET C/commands/{commandCode}`、`GET C/history/point`及第8节四个导出任务接口，不将其算成附件15项已经实现。

旧rangeType分支、独立run-comparison和独立torque-force/trend不再正式对接。视频、模型上传/运算、巡检和新分析模块没有本轮接口。远程启动、复位、解除急停、暂停/恢复、单轴升降仍未开放。

### 1.3 响应预算

| 请求 | 初版预算建议 |
|---|---|
| 历史/告警/用户列表 | page默认1、pageSize默认10，最大100 |
| 导出记录列表 | page默认1、pageSize默认20，最大100 |
| COUNT曲线 | 每批默认5个运行分组、最多10；cursor继续，返回总分组数 |
| 单次/周期曲线 | 每序列默认500、最多2000点；整个响应最多16000点及断点 |
| 日期目录 | 每批最多366个有记录日期，独立日期游标继续，不受列表筛选限制 |
| 导出选中编号 | 最多100项；更大集合按范围导出 |

预算限制一次响应，不限制历史年限。后端分批读取并返回实际点数、粒度、完整性、游标；不能静默截断、跨缺口连线或把全库给前端。普通跨HTTP分页不承诺固定数据库快照，完整一致文件用导出任务。

## 2. 首页：初始快照与每秒推送

### 2.1 GET C/home

Query必填systemCode。返回HomeSnapshot，一个系统、四台动力设备、八个传感器，未知测量/配置显式null并说明。

| 字段 | 类型 | 含义 |
|---|---|---|
| systemCode、systemName | string | 真实编号/名称 |
| available | boolean | 是否取得业务快照，不等于online |
| systemStatus、systemStatusText、statusValid | string或null / string / boolean | 最终五态及未确认说明 |
| online | boolean或null | 实际通信事实，无依据不假报离线 |
| systemHeight、systemCurrentHeight | number或null，m | 真实配置高度和独立当前高度；不填30或四机均高 |
| isMoving、movementDirection | boolean或null / UP、DOWN、STOP或null | 明确运动/方向；D201运行不能自动证明位移 |
| averageTension、averageSpeed | number或null | 后端有效均值，t与m/min |
| captureId、sequence、updatedAt | string或null / string或null / time或null | 实际采集实例、序号和时刻 |
| asOf、pushedAt | time / time或null | 视图读取、推送时刻；HTTP的pushedAt=null |
| winches、sensors | Winch[] / Sensor[] | 已配置的各对象和独立状态 |
| faultSources、notices | object[] | 故障定位、业务说明，空时[] |

Winch字段：`winchCode、winchName、groupNo、operatingStatus、statusText、statusValid、direction、directionText、speed、speedSource、sensorCodes`。编码/名称string，groupNo为integer，sensorCodes为两项string数组；工况/方向可null，speed为m/min或null；speedSource为AXIS/SYNCHRONOUS/UNCONFIRMED。只保留operatingStatus一个权威工况，不并列旧status让前端选择。

Sensor字段：`sensorCode、sensorName、winchCode、groupNo、metric、position、pressure、unit、status、statusText、statusValid、quality、missingReason、alarmThreshold、safetyValue`。pressure是拉力、unit固定t；三项数值可null。status为NORMAL/WARNING/ALARM/FAULT/OFFLINE或null；quality为GOOD/PARTIAL/INVALID/OFFLINE，missingReason可null。未确认metric/position显式null，不按数组位置猜角色。

faultSources项：`{objectType:SYSTEM/WINCH/SENSOR,objectCode,objectName,faultCode,reasonCode,message}`。未知设备归属不强填；单传感器故障不自动等于动力设备本体损坏，普通系统故障不无条件改写四台状态；共享急停影响明确SYSTEM_EMERGENCY，不声称四台本体均坏。

notices项：`{code,message,objectType,objectCode}`，全局说明对象可null。阈值缺失时数值null、说明THRESHOLD_NOT_CONFIGURED；无证据的测点NORMAL/WARNING/ALARM不可编造，status=null、statusValid=false。后端比较已确认配置，现场联锁仍由PLC执行，不因此增加自动控制下发。

### 2.2 GET C/home/stream

Query同一个systemCode；Header为Bearer访问令牌和Accept:text/event-stream。选择SSE沿用现有基础设施，但新地址和发送行为仍待实现。

先GET home，再订阅。连接成功立即发完整HomeSnapshot，之后每1秒完整发送，**同序号也发**。无新采样时updatedAt、captureId、sequence不改成当前时间，pushedAt仅说明发送；无数据仍发available=false与null。

```text
event: home
id: connection-local-17
data: {"code":200,"message":"快照推送","data":{"systemCode":"QJXT-01"}}
```

例子仅缩略data，正式data与HomeSnapshot一致。id为连接内发送游标，不是采样序号，也不表示新采样或历史已保存；不承诺Last-Event-ID历史补发，重连重新取得完整快照。

初版沿用每连接60秒后重新鉴权、最多64连接预算。切换系统关闭旧连接；按systemCode和采集实例/序号匹配，慢HTTP响应不能覆盖之后新采样。同序号可更新发送元数据，不更新采样事实。断流只提示连接中断并保留快照时刻/旧显示标识，不按经过时间宣布设备离线或数据无效。

首页故障/离线/异常测点不跳告警；保留正常详情入口和独立告警菜单。IDLE黄色、具体故障红色为前端固定样式，后端不输出CSS。

## 3. 单机详情与统一拉力对比

### 3.1 GET C/winch/detail

systemCode、winchCode必填。进入、主动刷新和重试时读一次；不订阅首页SSE，不5秒自动刷新。

| 返回字段 | 类型 | 说明 |
|---|---|---|
| systemCode、updatedAt、asOf | string / time或null / time | 系统与真实快照时刻 |
| winch、sensors | Winch / Sensor[] | 当前这台及两测点，首页同口径 |
| currentHeight、currentMode | number或null / AUTO、MANUAL或null | 本机高度m、模式 |
| setSpeedMPerMinute | number，必返有效 | 已确认适用生效设定，不用实际/额定速度替代 |
| todayCompletedCount、countBasis、countText | integer或null / STARTED_DATE / string | 今日开始记录中的确认完成数与说明 |
| runningHours、motionCoverage | number或null / COMPLETE、PARTIAL、UNCONFIRMED | 本机生产UP可靠运动小时，不当完整上下行工时 |
| maintenanceAt | time或null | 最近维护真实记录 |
| torqueForce | object或null | 独立反馈`{value,unit:"t",sourceName,quality,missingReason}` |
| configurationVersion、statisticsState、notices | string / READY、UPDATING、NOT_BUILT / array | 生效版本、统计状态、说明 |

成功详情不含ratedLoad/ratedSpeed，不强制未展示的当前/上一记录摘要。未取得有效适用设定速度时409 CONFIGURATION_NOT_READY，不能把0、默认10或实测值填成功响应。新参数仅保存或写应答确认时继续展示原已确认生效版本；没有旧版本则未就绪。

### 3.2 GET C/winch/tension/trend

| Query | 类型 | 必填/默认 |
|---|---|---|
| systemCode、winchCode | string | 是 |
| viewType | FORCE_COMPARISON | 是，仅此正式视图 |
| comparisonView | COUNT/DAY/MONTH/YEAR | 是 |
| cursor | string | 可选，续读时保持原筛选 |
| maxPointsPerSeries、unitPageSize | integer | 按第1.3节；unitPageSize仅COUNT |

COUNT返回kind=BATCH，一次生产上行一组；其余为kind=PERIOD。旧通用kind的WEEK/MONTH/WHOLE不作为本期正式输出类型。

| comparisonView | 后端units分组 |
|---|---|
| COUNT | 今日开始的实际生产上行记录，含进行中；分批返回，回显开始日口径 |
| DAY | 今日、昨日、本月、本年 |
| MONTH | 本月、上月、去年同期 |
| YEAR | 本年、去年同期 |

顶层返回：`systemCode、winchCode、kind、comparisonView、units、series、heightMin、heightMax、heightLimits、dataRange、asOf、updatedAt、complete、nextCursor、totalUnits、loadedUnits、aggregation、notices、pointReadings`。未知高度/限位、nextCursor显式null；无实际高度不造0～30m。无数据的预期周期仍有empty=true的unit与原因，不画0线。

| 对象 | 内容 |
|---|---|
| units[] | id、name、periodType或null、recordCode或null、startTime、endTimeExclusive、dataFrom、dataThrough、empty、emptyReason、runState、runResultText、recordCount、sourceRecordCounts、statisticsState |
| series[] | unitId、winchCode、sourceRole、sourceCode或null、name、unit=t、segments、statistics |
| sourceRole | SENSOR1/SENSOR2/ROPE_TOTAL/SERVO，四来源由后端准备，图例仅选择已返数据 |
| segments[] | segmentCode、motionRole、gapBefore、points；原始按采集顺序，平均按高度桶，不跨缺口连线 |
| statistics | averageTension、maxTension、minTension、validSampleCount、maxAverageTension、minAverageTension、scope、granularity、message；未知值null |

原始点：`{pointKey,sampleFrameId,time,height,value,quality,missingReason,speed,direction,motionRole,sampleCount:null}`。平均点：`{pointKey,sampleFrameId:null,time:null,height,heightFrom,heightTo,value,quality,sampleCount,speed,speedSampleCount}`；value为后端sum/count得到的t值，不是原始帧。缺失数值显式null。

pointReadings为`[{pointKey,time,height,readings:[{sourceRole,value,unit,quality,speed,missingReason}]}]`，由后端准备同unit可对照数据，计入总预算。不能配对的来源值null，不取另一次同高度点凑数；前端只按key读取。平均桶标明统计区间，不冒充同时现场采样。

statistics.scope区分RECORD_PRODUCTION_UP/PERIOD_VALID_SAMPLES/HEIGHT_BUCKET_MEANS；原始极值与平均曲线最高/最低平均分别返回，不随前端名称改变。有效报警峰值仍保留；无高度但拉力有效参与原始摘要，不进高度曲线。ROPE_TOTAL只统计确实配对集合，不能相加不同样本集合的独立均值。

recordCount是该分组四来源并集去重记录数，sourceRecordCounts是各来源去重数，points.sampleCount是桶有效样本数；前端不重算。图例显隐不改变分组统计。生产平均只取PRODUCTION_UP，恢复/检修原始另标用途/段，不加生产次数；正常DOWN没有完整历史曲线。aggregation回显NONE/MIN_MAX/HEIGHT_BUCKET及实际粒度；complete只表示范围加载完整，不表示无缩减。

## 4. 历史列表与单次详情

### 4.1 GET C/history/list

| Query | 类型 | 必填/默认 |
|---|---|---|
| systemCode、scope | string / RUN | 是，仅上行单程列表 |
| startTime、endTime、endExclusive | time/time/boolean | 是，带时区，固定右开true |
| dateBasis | STARTED/FIRST_OBSERVED | 默认STARTED，观测筛选不替代实际开始日 |
| includeAvailableDates | boolean | 默认false，日历首次加载建议true |
| availableDatesCursor | string | 可选，全历史日期目录独立续读 |
| page、pageSize | integer | 默认1/10，最大100 |
| winchCode、runResult | string | 可选，真实参与设备与结果筛选 |

返回`{systemCode,entries,total,page,pageSize,filters,availableDates,availableDatesComplete,availableDatesNextCursor,datesBasis,asOf,dataRange}`。按所选时间依据倒序、recordCode稳定排序；STARTED不含无实际开始记录，FIRST_OBSERVED可查但不改startedAt。

availableDates覆盖本系统全历史可查询RUN日期，与当前列表时间/结果/分页筛选独立；STARTED与观测日期目录明确datesBasis。轻量日期目录升序分批，最后complete=true；未请求时dates=[]、complete=false，不代表全历史为空。只有异常的日期不造RUN。

RunEntry固定字段，可空值显式null：

| 字段 | 口径 |
|---|---|
| recordCode、systemCode、recordScope、directionScope | 稳定编号、实际系统、固定LEG/UP；四台同步仍一条 |
| startedAt、endedAt、firstObservedAt、lastObservedAt | 实际边界与观测分开 |
| durationSeconds、durationText、motionDurationSeconds | 记录历时、后端说明、可靠系统生产运动时长；未知null |
| state、runResult、completionKind、runResultText | state=IN_PROGRESS/ENDED/UNCONFIRMED；result=COMPLETED/TERMINATED/UNCONFIRMED或null；完成分类NORMAL/RECOVERED/UNCONFIRMED或null；中文为后端最终结论 |
| endReason、endReasonText、startMethod、startMethodText | 真实结束/启动原因及说明，AUTO不等于定时 |
| setSpeedMPerMinute、intervalMinutes、startTime、parameterVersion | 当次开始时已确认参数；未知null，不用当前配置回填 |
| abnormalCount、alarmCount、handlingAttemptCount | 异常事件数、告警数分开；无逐次处理事实时attempt为null，不由D3=50补五次 |
| maxTension、minTension、unit | 生产UP有效钢绳原始极值t，不含电机/配对总力/下行快照 |
| hasDataGap、boundaryConfirmed、operatorName | 已知缺口、边界依据、可验证后台账号或null |

todayCompletedCount及周期结果按开始日记录组；跨日结束回填原开始日，不在结束日新增一次。前端直接展示runResultText，不看事件数组猜结果。

### 4.2 GET C/history/detail

必填systemCode、recordCode。可选winchCode（不填四台）、maxPointsPerSeries、cursor、eventCursor，以及成对fromAt/toAt或heightMin/heightMax用于局部查询；显示高度不等于机械限位。

返回`{systemCode,entry,sensors,servos,ropeTotals,statistics,exceptionEvents,eventTotal,eventsComplete,eventNextCursor,heightMin,heightMax,heightLimits,pointReadings,asOf,complete,nextCursor,aggregation}`。series、统计和取点关系沿用第3.2节，明确每台两索、一伺服、一配对总力，不混四台总和。未知参数/限位null。

概要/统计采用完整事实口径，曲线首批为有界概览，后续cursor或局部范围继续。事件默认最多10项，eventTotal为完整关联数量，eventCursor续读，不等于首批数组长度。回显请求范围与asOf；普通多次请求不承诺同一不变数据库版本，一致文件走导出。

事件项：eventCode、alarmCode或null、direction、occurredAt或null、firstObservedAt、outcome、outcomeText、recoveredAt或null、terminatedAt或null、reason、snapshots。最多触发/恢复/终止三类必要快照，不放连续DOWN窗口；没有可靠RUN关联的下降事件独立查询，不挂最近一条上行。

### 4.3 GET C/history/point

Query必填systemCode、recordCode、pointKey，winchCode可选。读取已返回的真实原始点，返回`{systemCode,recordCode,pointKey,sampleFrameId,time,winchReadings,quality,message}`，最多四台实际归档或事件相关读数，身份/质量/总力均后端准备。原始UP不补正常DOWN，事件点从事件快照取。

平均桶没有真实sampleFrameId，直接展示已返回的桶读数，不调用此接口伪造现场帧。pointKey只关联既有点，不以客户端高度/时间重新匹配其它采样。

## 5. 告警中心与独立事件

### GET C/alarm-dashboard

| Query | 类型 | 必填/默认 |
|---|---|---|
| systemCode | string | 是 |
| range | TODAY/7D/30D/CUSTOM | 默认TODAY，仅历史列表日期窗口 |
| startDate、endDate | date | CUSTOM必填，左闭右开 |
| deviceCode、sensorCode | string | 可选；deviceCode在本接口映射winchCode，不是内部deviceId |
| category | string | 可选，取后端类别清单 |
| view | ACTIVE/HISTORY | 默认ACTIVE，覆盖全历史尚未确认恢复 |
| recordKind | ALARM/EVENT | 默认ALARM；EVENT限view=HISTORY，查询全部真实事件（含非告警），不要求关联RUN |
| page、pageSize | integer | 默认1/10，最大100 |
| alarmCode、eventCode | string | 详情定位可选且互斥；真实非告警事件可按eventCode查看 |

返回`{systemCode,statistics,historyRange,recordKind,list,total,page,pageSize,filters,asOf,detail,categoryOptions}`。非详情查询detail=null，categoryOptions为code/text集合。recordKind=ALARM时list是告警集合；EVENT时list是事件集合，前端可先分页发现独立DOWN事件，再按eventCode查看详情，不要求先知道编号或具备RUN关联。EVENT与ACTIVE组合返回400；其余筛选含义不变。

历史窗口按Asia/Shanghai自然日：TODAY为今日零点至次日零点，7D从今日前6天零点至次日零点，30D从今日前29天零点至次日零点；CUSTOM采用传入右开区间。窗口不校验采样新旧，响应另列asOf及实际数据覆盖。

statistics含activeCount、seriousActiveCount、recoveryUnconfirmedCount、todayCount、todayRecoveredCount、scope、asOf和说明。固定scope=SYSTEM_ALL，不受设备/测点/类别/历史日期筛选裁剪。activeCount含所有尚未确认恢复的历史告警；serious只含已明确严重级别，未知不猜；今日发生与今日恢复分别按对应实际/观测时刻。

ACTIVE列表保留设备/测点/类别筛选，日期窗口不生效，historyRange说明NOT_APPLIED_TO_ACTIVE。HISTORY按实际发生/首次观测日期筛选，默认该时刻倒序、eventCode第二排序，稳定事件标识去重，持续异常不每帧新增。statistics始终只统计明确isAlarm=true的告警，不随recordKind变化。

告警项固定字段：alarmCode、eventCode、systemCode、objectType、objectCode、objectName、winchCode、sensorCode、category、categoryText、level、levelText、state、stateText、handlingText、occurredAt、firstObservedAt、recoveredAt、archivedAt、recoveryState、content、triggerValue、threshold、unit、thresholdKind、thresholdKindText、configurationVersion。未知编码/数值/时间null并说明；拉力单位t，其余按真实量纲。

类别/等级/阈值类别按明确配置/PLC事实，没有依据不编造。recoveryState为ACTIVE/RECOVERED/UNCONFIRMED；state为后端显示结论，归档与恢复原事实分开，archivedAt非空或运行已终止不证明故障恢复。历史阈值、版本和名称来自触发时快照，不用新配置改写旧告警；处理次数无来源仍null。

Event字段为eventCode、isAlarm、alarmCode或null、recordCode或null、systemCode、objectType、objectCode、objectName、direction、occurredAt或null、firstObservedAt、outcome、outcomeText、recoveredAt或null、terminatedAt或null、reason、hasSnapshots。EVENT列表返回这些摘要，不带连续采样；非告警事件不强造等级/阈值。detail包含Event、alarm（非告警为null）、最多触发/恢复/终止三个阶段快照及后端说明。

## 6. 数据归档：两图、比较与预计算

### GET C/archive

必填systemCode，按页面条件传winchCode、metric、tensionMetric、period、comparisonType、recordCode。三组日期各自成对、相互独立：

| 范围 | 日期输入（均左闭右开） |
|---|---|
| 左侧变化图 | changeStartDate、changeEndDate |
| 右侧当前区间 | currentStartDate、currentEndDate |
| 右侧参考区间 | referenceStartDate、referenceEndDate |

period为DAY/MONTH/QUARTER/HALF_YEAR/YEAR；comparisonType为PREVIOUS_PERIOD/YEAR_OVER_YEAR/CUSTOM。CUSTOM明确三组区间；自动参考区间由后端按日历确定并回显。附件未提供metric/tensionMetric完整前端取值，本轮不猜其编码；实施前核对实际页面枚举，仅接已有明确来源的指标，不以占位值假装支持未知分析。

返回`{systemCode,winchCode,metric,tensionMetric,period,comparisonType,changePoints,comparisonCurrent,comparisonPrevious,comparisonAxis,requestedRanges,effectiveRanges,dataRange,asOf,summary,complete,nextCursor,aggregation,notices}`。后端生成axisKey和真实当前/参考位置、中文标签，前端按key显示，不按数组下标拼不同区间。

summary含currentStatistics、referenceStatistics、absoluteChange、changeRatePercent、comparable、comparisonReason；统计含均值、原始极值、有效数、记录数、完整性及范围。可比且参考非0时变化率为(当前-参考)/参考×100，10表示10%；参考0或数据不足则null并说明，不替换任意小分母。

DAY/MONTH等仍从持久周期报表和高度桶读取；历史完整月份提前计算。去年同期按相同日历位置及已明确截止，回传实际范围/覆盖，不固定用去年全年，不按365天或30天机械偏移。闰日/不等长月采用有效日历边界并说明；不足以组成同口径结果则comparable=false，必要边界切片由后台准备，不在请求中扫全月/全年原始，不让前端算。

数值按sum/count合并、极值取原始极值、参与记录去重；次数按开始日、测量按采集日、事件按实际/观测日、可靠运动时长跨日拆分。无系统运动区间依据则系统工时null，正常DOWN不能补全程曲线或统计。高度按本机分箱，无高度有效拉力仍参与原始摘要；显示合并桶回显真实粒度，不冒充原始采样。

## 7. 急停与三参数设置

### 7.1 POST C/emergency-stop 与命令读取

JSON必填systemCode、requestId，可选reason（最长512个Java长度单位）。requestId为1～128位A-Za-z0-9._-，同一操作重试沿用；操作者来自登录。附件只示意systemCode，本契约补requestId沿用既有幂等规则。前端长按3秒是防误触交互，不是PLC脉冲持续时间。

```json
{
  "systemCode": "QJXT-01",
  "requestId": "stop-20261024-001",
  "reason": "操作员请求急停"
}
```

受理HTTP202，返回`{systemCode,requestId,commandCode,commandStatus,phase,deviceExecutionConfirmed,message,createdAt,updatedAt}`。commandStatus沿用CREATED/QUEUED/SUCCEEDED/REJECTED/FAILED/UNCONFIRMED/BLOCKED；phase表达ACCEPTED/QUEUED/WRITE_CONFIRMED/UNCONFIRMED/REJECTED等实际阶段，不能用工况EMERGENCY_STOP替代命令状态。

`GET C/commands/{commandCode}?systemCode=...`返回同一回执；commandCode映射内部commandId，后续需补查找能力，不等同当前按requestId查询。WRITE_CONFIRMED或内部SUCCEEDED只证明写应答；无明确现场关联时deviceExecutionConfirmed=false、说明未验证停止，不自动重发不确定命令。

目标M50写1，真实地址/类型按核实配置，不回退M15、不写0解除、不承诺M2必响应；M4不允运行或M11不安全不应阻挡有权限急停。文档不自动适配现有点位。

### 7.2 GET/PUT C/parameter-settings

GET必填systemCode，返回`{systemCode,savedVersion,savedSettings,appliedVersion,appliedSettings,applyStatus,commandCode,message,asOf}`。settings均为`{startTime,intervalMinutes,speedMPerMinute}`，未知对象null；已保存与已生效不可冒充同一状态。

PUT必填systemCode、requestId、expectedVersion、startTime、intervalMinutes、speedMPerMinute。三项业务参数不变，编号/版本只服务幂等与并发；expectedVersion取真实savedVersion，无版本则未就绪，不猜0。

```json
{
  "systemCode": "QJXT-01",
  "requestId": "settings-20261024-001",
  "expectedVersion": "7",
  "startTime": "08:20",
  "intervalMinutes": 30,
  "speedMPerMinute": 10
}
```

示例不表示现场有该版本/值。startTime为HH:mm，intervalMinutes为正整数分钟，后端换秒进入内部逻辑；速度按已确认配置和当前软件范围（目前>0且≤10m/min，非PLC额定承诺）。必要校验归所属模块一次处理，不逐层重复。

PUT返回参数视图与requestId/commandCode。applyStatus为NOT_READY/SAVED/PENDING_APPLY/WRITE_CONFIRMED/APPLIED/UNCONFIRMED/FAILED。SAVED只说明平台保存，WRITE_CONFIRMED只是写应答；只有确认设备生效才APPLIED并更新appliedSettings。失败保留已存和原生效版本，不假装自动回滚PLC。

当前配置持久化/回读尚未闭环；D105～D108编码未确认，不强配D100～D103，不把D105当HH:mm或D107当速度。阈值不塞三参数，不新增阈值页面或中转时间控制。

## 8. 导出记录与文件

采用既定后台任务，不另建同步GET history/export分支，也不让浏览器分页拼CSV。四类统一流程，页面RUN_SAMPLES映射内部UP_SAMPLES；详细列与快照见[导出方案](data-export-plan.md)。

| 方法/路径 | 输入 | 返回 |
|---|---|---|
| POST `C/exports` | JSON systemCode、exportType、范围、unit | HTTP202、ExportTask |
| GET `C/exports` | systemCode、exportType/state可选、page/pageSize | 当前账号list/total/page/pageSize |
| GET `C/exports/{taskCode}` | systemCode、任务编号 | 状态、冻结范围、文件清单 |
| GET `C/exports/{taskCode}/files/{fileCode}` | systemCode、任务/文件编号 | 完整发布CSV/ZIP流，成功不套JSON |

exportType=RUN_LIST/RUN_SAMPLES/EXCEPTIONS/PERIOD_STATISTICS，unit=t/kg默认t。RUN_LIST提交已查询startTime/endTime（右开）或recordCodes；RUN_SAMPLES用recordCode/recordCodes或明确采集范围，motionRole默认ALL_UP；EXCEPTIONS用事件范围/筛选或eventCodes；PERIOD_STATISTICS用已提交周期范围及本机条件。编号集合与范围互斥，不接收page/pageSize或SQL。

列表覆盖全部筛选结果，单次原始包含实际保存的所有UP用途/设备/来源，不随图例隐藏删除；DOWN只导出事件点。无RUN关联的UP可按采集范围导出，不造关联。单位和说明由后端输出，前端不重算统计。

ExportTask字段：taskCode、systemCode、exportType、state（QUEUED/RUNNING/SUCCESS/FAILED）、phase、filters、unit、templateVersion、createdAt、startedAt、snapshotAt、finishedAt、processedRows、outputRows、files、tableCounts、errorCode、errorMessage。未取得字段null，未发布files=[]，无可靠总量不报假百分比。

files项：fileCode、fileName、mediaType、sizeBytes、dataRows、rowKind、downloadable。tableCounts说明ZIP各表粒度/行数，合计数据行不叫运行次数；文件放持久文件存储，不存BLOB。完整写完核对才发布，后补数据新建任务，失败不下载半文件。

冻结提交条件/单位/模板，内容以真正执行时一致快照为准；maxId或ID清单不冻结可变字段。任务与下载都查登录、systemCode与创建者归属。CSV UTF-8 BOM，单文件CSV、多文件ZIP；Content-Disposition用实际系统编号，不固定QJXT-01。服务端/浏览器有界流式处理，不fetch.blob全量缓存大文件，不把令牌放URL。

未完成下载409，缺失404，无权403。开始流之前错误可返回JSON，流中断不当下载完整成功。

## 9. 登录、只读用户与占位页面

login输入username/password，返回token、refreshToken、expiresIn、expiresInUnit=MILLISECONDS，明确沿用当前毫秒不悄改秒。refresh输入refreshToken，返回新token；只有实际实施轮换才返回新refreshToken，当前未轮换不宣称已做。

user-info目标含id、username、nickname、avatar、email、phone、roles、permissions、systems、currentSystemCode；systems为授权`{systemCode,systemName}`列表，currentSystemCode为有效选择或null。单系统也由后端配置提供；可空资料null、角色显示名称由后端返回。

logout返回真实处理范围和message，当前仅清请求上下文、没有JWT服务端撤销，不能宣称旧令牌全部失效。当前auth和用户管理权限缺口见[现有接口](current-api-reference.md)，保护实施完成后再开放目标页面。

`GET /api/users`：keyword可选，page默认1、pageSize默认10最大100。后端按账号/姓名/角色名称或编码搜索，创建时间倒序、用户ID第二排序，返回`{list,total,page,pageSize,asOf}`。list项为`{id,username,realName,roleCode,roleName,status,statusText,phone,email,createTime}`，ID字符串、时间带时区、缺值null，不返回密码/哈希。需登录且user:read，不沿用旧fallback permitAll。

页面只读，不新增用户创建/编辑/删除入口。模型管理、历史巡检、数据分析保留待开放，视频范围外。

## 10. 错误与页面调用顺序

目标错误为`{code,message,data:{errorCode,...必要公开信息}}`，无业务对象时data=null。安全过滤器401现状仍可能无体，前端兼容。旧命令HTTP200＋REJECTED等以现有接口参考为准，此处不声称已修改。

| HTTP | 场景 |
|---|---|
| 200 | 查询成功；空态/真实缺值/统计准备情况用结构说明 |
| 202 | 命令或导出已受理，不代表已停止/生效/生成文件 |
| 400 | 必要字段、范围、预算不合法，不是采样时间早晚错误 |
| 401/403 | 未认证/无权限或任务归属不符 |
| 404 | 系统、设备、记录、点、事件、任务/文件不存在或归属不符 |
| 409 | CONFIGURATION_NOT_READY、版本冲突、任务未完成等明确未就绪 |
| 429/503 | 受理资源满/依赖暂不可用，不能伪装空历史 |
| 500 | 未分类错误，不返回堆栈 |

```json
{
  "code": 409,
  "message": "尚未取得本设备已确认生效的设定速度",
  "data": {
    "errorCode": "CONFIGURATION_NOT_READY",
    "systemCode": "QJXT-01",
    "winchCode": "WJ-01"
  }
}
```

| 页面操作 | 调用方式 |
|---|---|
| 首页 | user-info系统上下文→home→home/stream每秒完整快照 |
| 单机详情 | 主动winch/detail，不跟随SSE |
| 对比图 | 统一trend按comparisonView查询；图例选择现有来源，cursor继续批次 |
| 历史 | list摘要/独立日期目录→detail有界概览→局部/cursor/point |
| 告警 | alarm-dashboard筛选；alarmCode/eventCode定位详情 |
| 归档 | 提交完整三组区间→后端两图、对齐轴、汇总，不在前端算同比 |
| 操作 | 真实账号提交→显示阶段→命令读取/重新查询；设备态仍按采集 |
| 导出 | 创建任务→查询状态/列表→完整发布后下载 |

前端可做必填/数字/查询起止提示、日期单位格式化、固定样式和加载/网络/空态提醒。状态、配对总力、统计、同比、历史结果、告警恢复和处理要求由后端返回；缺少说明时只用通用失败提示，不造业务原因。

本轮仅文档。当前Java、PLC模板和七表基线未迁移；完整规则见[业务总览](business-flow-and-page-data-overview.md)、[数据库设计](database/page-driven-database-design.md)、[职责对齐](frontend-backend-contract-alignment.md)。
