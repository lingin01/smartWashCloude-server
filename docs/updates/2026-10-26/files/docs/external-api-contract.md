# 页面对外接口契约：输入、返回与职责

2026-10-26历史业务含义更新见[实时、M106整轮历史与统计](updates/2026-10-26/realtime-history-statistics-overview.md)：历史列表一次M106整轮一条，详情上行0.5米留点，M60/M61形成三种结果。本文原上行单程记录和往返关联字段需后续按新含义统一DTO，不新增第二套历史接口；本轮未改路由或代码。

修订日期：2026-10-25。依据本次[全部页面报告](reference/frontend-pages-report-20261025.md)、[PLC告警红字说明](reference/plc-alarm-revision-20261025.md)及[对齐决定](frontend-backend-contract-alignment.md)。本文定义新的页面目标接口，不表示路由、字段、每秒完整推送或历史功能已经实现；本次图表细则见[详情拉力对比处理说明](detail-force-comparison-processing.md)，Controller方法、DTO与服务路径见[实施清单](controller-api-specification.md)。

**本页只描述目标。** 当前Java实际接口、kg/STANDBY/NON_NULL返回、命令和鉴权限制已移到[当前接口参考](current-api-reference.md)。以前拟议的`/api/cleaning/devices/{deviceId}/history/...`不再作为页面正式目标，不新增两套未来接口供前端猜选；当前七个设备接口尚未因文档修订而迁移。

## 1. 通用规则与接口目录

### 1.1 身份、单位、响应

API基地址为`/api`，下文`C`表示`/api/command-center`。请求统一使用实际公开systemCode，例如配置中的QJXT-01；动力设备用winchCode，传感器用sensorCode。后端解析内部system_id/deviceId/winchId并核对归属，不让客户端同时提交另一套内部键，不回退到固定TANK-03。

除登录/刷新按各自凭据外，请求携带`Authorization: Bearer <访问令牌>`，写入使用JSON，GET没有请求体。操作者从登录身份取得，不接受客户端自报operatorId；授权系统上下文从user-info取得。

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
| 查询区间 | 运行/采样时刻范围左闭右开；告警history的CUSTOM日期明确包含首尾，内部转右开；格式/起止检查不是采样时间校验 |
| 业务输出 | 后端给实际名称、状态/结果中文、质量、原因、配对总力和统计；前端不重新推导 |
| 五态 | 页面IDLE/RUNNING/MAINTENANCE/FAULT/OFFLINE，内部STANDBY可映射IDLE；未知为null＋statusValid=false |
| 类型 | 公开编号、sampleKey、采集序号、配置版本为string；事件revision为正安全整数；已知数量integer，未知null；数值有效0保留 |

成功业务码固定code=200，包括HTTP202表示已受理的情况；不能把当前旧接口的code=202直接用于新页面。失败使用相应非2xx HTTP及非200业务码，data.errorCode给出原因。显式null是新页面DTO的实施要求，当前全局NON_NULL尚未改变。下文返回对象均放在Result.data内，文件下载与SSE另有协议。

采集数据不校验早晚、年龄、时钟偏差或是否早于上一帧，不因延迟补入拒收、丢弃或判离线。两索合计基于已确认采集帧、对象、方向、分段和有效性，由后端配对；不猜时间容差，不按相同高度拼接不同过程。

### 1.2 正式页面接口

附件17个HTTP方法/路径在本后端采用下表。认证地址存在，但修订字段及保护仍需适配；其余页面地址属于待实现目标。

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
| GET `C/alarms/latest` | systemCode、deviceCode可选 | 完整近期事件与hasAttention |
| GET `C/alarms/history` | systemCode、range、levels、设备、日期、分页 | 服务端筛选的事件历史 |
| PUT `C/winch/tension-thresholds` | systemCode、winchCode、两路阈值、requestId、expectedVersion | PENDING/APPLIED与真实生效快照 |
| GET `C/history/list` | systemCode、scope、范围、分页 | 单次记录与日期目录 |
| GET `C/history/detail` | systemCode、recordCode | 单次概要、曲线、统计、事件 |
| GET `C/parameter-settings` | systemCode | 已保存与已生效参数 |
| PUT `C/parameter-settings` | systemCode、requestId、expectedVersion、三参数 | 保存/下发阶段 |
| GET `/api/users` | keyword、page、pageSize | 只读用户列表 |

另外确定必要的目标传输/后续读取入口：`GET C/home/stream`、`GET C/commands/{commandCode}`、`GET C/history/point`、`GET C/events/history`、`GET C/events/{eventCode}`及第8节四个导出任务接口，不将其算成附件17项已经实现。

旧alarm-dashboard、旧rangeType分支、独立run-comparison和独立torque-force/trend不再正式对接。视频、模型上传/运算、巡检和新分析模块没有本轮接口。远程启动、复位、解除急停、暂停/恢复、单轴升降仍未开放。

### 1.3 响应预算

| 请求 | 初版预算建议 |
|---|---|
| 运行历史/告警历史/独立事件/用户列表 | page默认1、pageSize默认10，最大100 |
| 最新告警 | 完整近期集合最多1000项预算，超限503并说明，不能静默截断；前端可本地每页10条 |
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

notices项：`{code,message,objectType,objectCode}`，全局说明对象可null。阈值缺失时数值null、说明THRESHOLD_NOT_CONFIGURED；无证据的测点NORMAL/WARNING/ALARM不可编造，status=null、statusValid=false。传感器NORMAL/WARNING/ALARM依据明确PLC状态信号映射；阈值配置用于展示和下发，后端不比较每笔拉力自行新增故障。缺少PLC普通/严重映射时不猜等级；缺少阈值不应抹去已确认PLC错误。

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

Query必填systemCode、winchCode。响应WinchDetailResponse；进入、切换设备、浏览器刷新或失败重试时查询，不订阅首页SSE、不5秒自动刷新。详情成功后再请求趋势。

| 返回字段 | 类型/说明 |
|---|---|
| systemCode、updatedAt、asOf | string、真实快照时刻或null、查询时刻 |
| winch、sensors | 第2节Winch及本机两路Sensor；来源编号/名称/ropeNo由后端提供 |
| currentHeight、currentMode | m或null；AUTO/MANUAL或null |
| setSpeedMPerMinute、configurationVersion | 已确认适用设定速度number、真实生效配置版本string；成功详情必须有效 |
| todayCompletedCount、countBasis、countText | integer或null；STARTED_DATE；今日开始记录中的确认完成数及中文说明 |
| runningHours、motionCoverage | 本机生产UP可靠运动小时或null；COMPLETE/PARTIAL/UNCONFIRMED，不当全上下行工时 |
| maintenanceAt、torqueForce | 维护实际时间或null；独立反馈{value,unit:t,sourceName,quality,missingReason}或null |
| thresholdSettings | {savedVersion,savedThresholds,appliedVersion,effectiveThresholds,applyStatus,commandCode,message}；两路对象见第7.3节 |
| statisticsState、notices | READY/UPDATING/NOT_BUILT及说明 |

成功详情不含ratedLoad/ratedSpeed。未取得真实有效速度时409 CONFIGURATION_NOT_READY，不能用实际、额定或默认速度填成功响应。阈值未就绪不必隐藏其他已知测量：可空版本/配置如实null，说明未同步。传感器显示的是已确认适用阈值；申请草稿不能覆盖它，部分写入时明确确认范围。

### 3.2 GET C/winch/tension/trend

| Query | 类型 | 必填/默认 |
|---|---|---|
| systemCode、winchCode | string | 是 |
| viewType | FORCE_COMPARISON | 是 |
| comparisonView | COUNT/DAY/MONTH/YEAR | 是 |
| resolution | OVERVIEW/RAW | 默认OVERVIEW；完整点表用RAW分批取得 |
| cursor | string | 可选，续读保持原筛选/分辨率 |
| maxPointsPerSeries、unitPageSize | integer | 第1.3节预算，unitPageSize仅COUNT |

COUNT为kind=BATCH，一次生产上行一组；其余为PERIOD，横轴仍是高度。分组固定标识：DAY返回TODAY/YESTERDAY/MONTH/YEAR；MONTH返回MONTH/PREVIOUS_MONTH/PREVIOUS_YEAR；YEAR返回YEAR/PREVIOUS_YEAR。缺同期数据保留empty=true的分组与原因，不造0线。COUNT按实际开始时间升序、稳定记录编号排序，未知边界单列说明，后端顺序稳定；初次前端选择首个已返组，不强制defaultUnitId。

返回WinchTrendResponse：`{systemCode,winchCode,kind,comparisonView,units,series,heightMin,heightMax,heightLimits,dataRange,asOf,updatedAt,resolution,complete,nextCursor,totalUnits,loadedUnits,loadedPointCount,aggregation,notices}`。计数只在确知时给值，否则null；未知限位null，不填0～30m。

| 对象 | 字段和含义 |
|---|---|
| units[] | id、name、recordCode或null、periodType或null、startAt、endAt、endExclusive=true、rangeLabel、dataThrough、runState、recordState、runResult、runResultText、abnormal、empty、emptyReason、includedRunCount |
| series[] | id、unitId、winchCode、sourceRole、sourceCode或null、sourceSensorCode或null、name、unit=t、empty、emptyReason、segments |
| sourceRole | SENSOR1/SENSOR2/ROPE_TOTAL/SERVO；总力是派生来源，不是第三个实体传感器 |
| segments[] | segmentCode、motionRole、gapBefore、points；保留换向、用途和缺口 |
| 原始points[] | sampleKey、sampleFrameId、sequence、time、height、value、speed、direction、motionRole、quality、valueValid、speedValid、missingReason、sampleCount |
| 平均points[] | sampleKey、sampleFrameId=null、time=null、height、heightFrom、heightTo、value、speed、quality、sampleCount、speedSampleCount、heightRuleVersion、computedVersion |

原始有效sampleCount=1，无效=0；平均为真实有效样本数，0时value=null，尚未构建的数量null。拉力与速度分别保留有效性，拉力缺失不强制抹去有依据的速度。有效0保留；GOOD/PARTIAL仍须对应通道有效依据，原始点不因缺sampleCount就默认1。sampleKey由明确帧/本机/分段生成，平均键来自周期/本机/桶与版本；相同高度重复经过不能同键，不能按邻近时间拼接。过程顺序用采集序号，不做采样时间校验。

图表runState仅RUNNING/COMPLETED/null：分别表示进行中/已结束/未知；COMPLETED不能证明业务成功。recordState保留IN_PROGRESS/ENDED/UNCONFIRMED，最终runResult按第4节另行表达。PERIOD不适用的运行字段null。

ROPE_TOTAL在原始同帧、本机、用途与两路有效关联明确时先求和再聚合。总力速度用该帧已确认适用本机速度，不能相加速度或默认抄第一根；平均速度使用配对集合自己的有效和/数量。平均曲线用已保存sum/count合并，不直接相加独立单绳均值。原精度计算，页面边界kg转t，前端最后格式化。

新版右侧是当前曲线逐点表，**不强制返回statistics、rawStatistics、ropeExtrema、defaultUnitId或旧四来源单点表**。数据库摘要仍供列表/归档使用。动力页SUM需改为消费ROPE_TOTAL；历史页没有SUM入口。

OVERVIEW明确NONE/MIN_MAX/HEIGHT_BUCKET及显示粒度，只供概览，不冒充全点。RAW在COUNT返回实际保存的原始点，在PERIOD返回完整基础桶；均分批续读，最后complete=true才表示请求范围已读完，不表示无采集缺口。当前前端尚无这一续读契约，需同步改点表加载，不能一口气返回全年原始数据。

## 4. 系统历史列表与单次详情

### 4.1 GET C/history/list

| Query | 必填/默认 |
|---|---|
| systemCode、scope=RUN | 是，一次系统生产上行，四台同步仍一条 |
| startTime、endTime、endExclusive=true | 是，ISO带时区，startTime <= startedAt < endTime |
| dateBasis | STARTED默认；FIRST_OBSERVED可查未知实际开始记录，不改startedAt |
| includeAvailableDates、availableDatesCursor | 默认false；页面首次true，日期目录独立续读 |
| page、pageSize | 1/10，最大100 |
| runResult | 可选，第4.2节最终结果枚举 |
| winchCode | 可选导航上下文，仅回显；不把系统列表筛成单台，也不改全系统极值 |

返回RunListResponse：`{systemCode,entries,total,page,pageSize,filters,availableDates,availableDatesComplete,availableDatesNextCursor,datesBasis,asOf,enabledFrom,dataRange}`。默认所选开始依据倒序、公开记录id倒序；total是筛选系统记录数，不是曲线、设备或事件数。

availableDates来自本系统全历史真实RUN日期目录，不受当前页/日期/结果筛选截断；按日期独立分批，前端必须读取至availableDatesComplete=true才称完整日历。未请求目录时数组[]、完整false；只有异常的日子不造RUN。当前前端只接单次完整目录，需适配续读，不能把366个日期上限当永久保留期限。

### 4.2 RunEntry：列表、详情共用

| 字段 | 类型与范围 |
|---|---|
| id、kind、name | 公开稳定record_code、固定RUN、实际名称；前端entry.id作为下一请求recordCode，不另造主键 |
| startDate、endDate | 上海业务覆盖日期或null，结束日标签不代表查询右开时间 |
| startedAt、endedAt、firstObservedAt、lastObservedAt | 实际边界与观测边界分开，未知null |
| state、runResult、completionKind | IN_PROGRESS/ENDED/UNCONFIRMED；下表结果或null；NORMAL/RECOVERED/UNCONFIRMED或null |
| runResultText、statusText、endReason、endReasonText | 后端最终结果说明；statusText供现页面，前端不再依据事件数组猜结果 |
| startMethod、startMethodText、sequenceNo | SCHEDULED/MANUAL或null，真实来源文字，当日序号有依据才给；AUTO不是定时启动证据 |
| durationSeconds、durationText、motionDurationSeconds | 记录历时与可靠生产运动时长分开，未知null；不把实时当前时钟补成旧记录时长 |
| setSpeedMPerMinute、intervalMinutes、startTime、parameterVersion | 该次开始时已确认参数快照，未知null，不用当前设置回填 |
| maxTension、minTension、unit、extremaScope | 本系统该记录全8路单绳有效PRODUCTION_UP原始集合极值，t；固定SYSTEM_EIGHT_ROPES_PRODUCTION_UP |
| abnormalCount、alarmCount、handlingAttemptCount | 可靠关联的保护异常过程数、告警过程数、实际处理尝试数；无来源null，不由D3=50补5次 |
| runCount、completedCount、normalCompletedCount、recoveredCount、faultTerminatedCount、incompleteCount | RUN计数为1或未确认；其余依据已确认最终结论0/1，未确认部分null，不把进行中当失败 |
| exceptionEvents、exceptionEventsComplete、eventTotal | 有界真实保护过程摘要；无明细可[]但完整false，不能把未加载叫没有事件 |
| hasDataGap、boundaryConfirmed、operatorName、dataCoverage | 缺口/边界依据、可验证操作账号或null、各通道覆盖说明 |

| 最终事实 | 页面runResult | 说明 |
|---|---|---|
| 已确认完成 | COMPLETED | completionKind及文字区分正常/异常恢复后完成 |
| 已明确非故障提前退出 | INCOMPLETE | 如真实中断；不是“还在运行” |
| 已明确故障导致终止 | FAULT_TERMINATED | 急停只有实际归类及退出依据才映射；保留真实endReason |
| 仍进行或结果证据不足 | null | 分别返回state及原因，前端需支持，不造成功/失败 |

endReason可为COMPLETED/NEXT_SCHEDULE_REACHED/FAULT/MANUAL_STOP/EMERGENCY_STOP，只有真实来源才赋值，其余null并说明；当前PLC未给定时/下次计划等来源时不补写。内部已有结果枚举不因页面映射而改库。

max/min是8路单绳中一个最大和一个最小，可能来自不同通道/时刻；不是8路相加，不含伺服、双绳总力或正常DOWN。缺通道保留覆盖说明，当前图只选动力二号也不改变这两个数。次数/最终结果仍归开始日，todayCompletedCount口径不改为结束日。

### 4.3 GET C/history/detail

必填systemCode、recordCode；yearGrouping可省略或MONTH，仅兼容现请求，其他值400，不表示RUN改为月统计。目标可选resolution=OVERVIEW/RAW（默认OVERVIEW）、cursor、maxPointsPerSeries、eventCursor及成对fromAt/toAt或heightMin/heightMax；winchCode/来源若用于局部加载只裁曲线，不裁entry的系统范围。

返回RunDetailResponse：`{systemCode,entry,sensors,servos,heightMin,heightMax,heightLimits,asOf,dataRange,resolution,complete,nextCursor,loadedPointCount,aggregation,exceptionEvents,eventTotal,eventsComplete,eventNextCursor,notices}`。sensors正常配置8条、servos4条，每条有id/name、winchCode、groupNo、ropeNo（伺服null）、sensorCode或null、sourceRole、unit=t、segments/points、空态与质量说明。历史sourceRole=ROPE1/ROPE2/SERVO，ropeNo=1/2；不得从S1～S8后缀猜实体关系。

原始点与分段沿用第3.2节。**这里不返必需ropeTotals或旧统计双表**；右侧显示当前曲线全部已加载的真实点，RAW游标读完才称已读完整范围，保留重复高度、恢复/检修UP用途及缺口，不把正常DOWN补成连续曲线。概览缩减点不能用于冒充原始全点表或导出。系统RUN仍是生产UP单程，不因前端叫“一轮”扩大为已确认完整往返。

关联保护事件摘要字段为eventCode、alarmCode或null、occurredAt/timeBasis、outcome、outcomeText、returnedAt/resumedAt或null、reason。实际无逐次处理事实时attemptCount=null，不虚构回零/恢复时刻。默认10项有界摘要，详情通过events/{eventCode}查看；没有RUN关联的DOWN异常由独立events/history发现，不挂最近一条上行。

### 4.4 GET C/history/point

Query必填systemCode、recordCode、sampleKey，winchCode可选。返回`{systemCode,recordCode,sampleKey,sampleFrameId,time,winchReadings,quality,message}`，只追溯已返回的真实帧，最多四台有依据读数；不以客户端高度/时间重配，也不为平均桶伪造现场帧。没有RUN关联的事件点使用events详情，不硬填recordCode。

## 5. PLC告警：最新、历史与独立事件

规则见[PLC事件方案](plc-alarm-and-threshold-processing.md)。PLC判断错误等级，后端维护同一alarmCode持续/升级/结束，前端只显示；后端不得按每笔拉力比较阈值新增故障。新查询退出旧alarm-dashboard，不再强制activeCount统计卡、分类过滤或人工恢复字段。

### 5.1 GET C/alarms/latest

Query仅systemCode必填、deviceCode可选（公开winchCode值）。不接range、日期或页码。返回`{systemCode,asOf,recent,hasAttention,attentionScope,recentWindowHours,windowStart,windowEnd,complete,demo}`，real production demo=false。

recent为完整近期AlarmItem[]；目标为所有尚未明确结束事件＋最近24小时结束事件，配置和实际边界回显。跨日持续事件不丢，hasAttention固定SYSTEM_ALL，不受设备过滤影响。有未结束PLC错误或尚未恢复的已确认PLC连接异常即true；无事实源时503，不编造false。窗口不是采样过期或历史删除规则。complete成功固定true；超过1000项预算返回503 RECENT_SET_TOO_LARGE，不静默截断。

侧栏/告警页可共用可见时每15秒一次请求，查询不写事件。前端按开始时刻倒序及alarmCode稳定排序后本地分页10条；点击/关闭详情不清红点。响应失败保留上次视图，不生成PLC离线事件。

### 5.2 GET C/alarms/history

| Query | 约定 |
|---|---|
| systemCode | 必填 |
| deviceCode | 可选所属动力设备，映射公开winchCode |
| range | TODAY/7D/30D/CUSTOM，默认30D |
| startDate、endDate | CUSTOM必填，YYYY-MM-DD、上海日期包含首尾，1～366天；其他range由后端生成 |
| levels | 默认NORMAL,SERIOUS；逗号分隔，显式空字符串表示不选等级、返回空结果，不能@NotBlank后改全选 |
| includeUnclassified | 默认false；显式true可查询尚未确认级别的实际事件，不硬映射NORMAL |
| page、pageSize | 1/10，最大100 |

返回`{systemCode,asOf,historyRange:{startDate,endDate,endInclusive:true},effectiveRange:{startAt,endAt,endExclusive:true},list,total,page,pageSize,demo}`。先按事件显示起点、已确认等级、设备筛选再分页，开始倒序、alarmCode倒序；total去重事件数。同一过程持续/升级/结束仍一条。7D为今日前6天至今日，30D为前29天至今日；内部转次日零点右开，回显原日期包含约定。

旧响应不能回滚更大revision；历史页不拿recent补数，也不覆盖最新集合成员。等级筛选匹配当前已确认等级，不按旧状态文本推测；等级从SERIOUS降回NORMAL是否仍同一过程必须现场确认，未确认不加算法。

### 5.3 AlarmItem与事件更新

| 字段 | 类型/含义 |
|---|---|
| alarmCode、eventCode、systemCode | 稳定公开编号及所属系统；alarmCode映射真实eventCode用于详情，更新不变 |
| deviceCode、deviceName | 真实所属设备或null；系统级故障不能硬填某一台 |
| source、type、content | PLC/PLATFORM，明确错误类型/错误码解释、PLC原文；不由页面关键词重判 |
| runCode | 可靠关联的recordCode或null，跨RUN不据此拆事件 |
| level、levelConfirmed | NORMAL/SERIOUS或null；未知false，不输出旧MEDIUM |
| occurredAt、timeBasis、actualOccurredAt、firstObservedAt | 显示起点；ACTUAL/FIRST_OBSERVED；实际发生未知null；持续更新不改起点，后补真实起点按同号修订推进版本；均带时区 |
| updatedAt、revision | 实际更新时刻、正安全整数单调版本；采样更新时间不同于事件新发生 |
| eventState、endedAt、continuity | ACTIVE/ENDED或未确认null；明确过程结束时刻或null；CONFIRMED/UNCONFIRMED |
| triggerValue、peakValue、peakAt、unit | t或null、对应真实时刻或null、固定t；峰值不默认等于触发值 |
| peakSource、peakCoverage、hasDataGap | PLC/OBSERVED_SAMPLES或null；COMPLETE/PARTIAL/UNCONFIRMED；观测覆盖说明 |
| threshold、thresholdKind、configurationVersion | 发生时有效阈值及WARNING/PROTECTION或null，不能从SERIOUS猜阈值类别 |
| direction、occurredHeight | UP/DOWN/STOP或null，m或null |
| message | 未确认或缺失说明 |

正式目标不依赖旧state/handling/recoveredAt显示恢复。ENDED只是PLC过程结束，不能表示人工消警；页面没有确认/恢复/处理写接口。内部传感器通道、控制器会话和来源标识必须保留用于归并，页面无需展示这些部件。

峰值可由PLC提供，或后端在PLC已开启的事件窗口增量统计有对应通道依据的有效采样；断联/漏采使覆盖PARTIAL。DOWN只留事件和有限触发/峰值/结束快照，不保存完整下降过程。未确认等级或起止不能凭新数值猜。

### 5.4 独立异常入口

`GET C/events/history`：必填systemCode、startTime/endTime（带时区、右开）；可选recordKind=ALL/NON_ALARM/ALARM（默认ALL）、deviceCode、direction、page/pageSize。返回`{systemCode,list,total,page,pageSize,asOf,filters}`，支持无RUN关联、isAlarm=false的DOWN事件。list为Event摘要：eventCode、alarmCode或null、isAlarm、recordCode或null、对象、方向、真实/观测起点、outcome与说明、hasSnapshots。

`GET C/events/{eventCode}?systemCode=...`返回`{systemCode,event,alarm,snapshots,asOf}`，非告警alarm=null，snapshots仅有真实触发/峰值/恢复/终止/过程结束中的有限阶段点，无连续数组。告警详情也可按alarmCode对应的稳定eventCode读取；后端返回明确映射，不让前端猜。列表AlarmItem补eventCode用于该只读定位。

## 6. 数据归档：两图及后端比较

### GET C/archive

| Query | 类型/必填 |
|---|---|
| systemCode、winchCode | 实际系统、本机，均必填 |
| metric | TENSION/SPEED，必填；ALARM当前页面未开放，返回400 UNSUPPORTED_METRIC |
| tensionMetric | metric=TENSION时必填WINCH_TENSION/TRACTION_ROPE_TENSION，分别本机ropeNo=1/2；SPEED不适用 |
| period | WEEK/MONTH/QUARTER/YEAR，默认WEEK；不是删除底层日/半年报表 |
| comparisonType | SELECTED_PERIODS/PREVIOUS_RUN/HISTORY_AVERAGE/PREVIOUS_PERIOD/YEAR_ON_YEAR，默认PREVIOUS_RUN |
| recordCode | 两种运行比较必填；其余不适用 |
| changeStartDate/changeEndDate | 左图完整日期区间，必填；右开 |
| currentStartDate/currentEndDate | 周期比较当前区间，成对；右开 |
| referenceStartDate/referenceEndDate | SELECTED_PERIODS必填参考区间，其他由后端明确生成；右开 |
| maxPointsPerSeries、cursor | 第1.3节预算，续读原条件 |

本轮不接受附件当前含义混合的compareStart/compareEnd来猜两个区间，缺失独立边界400 RANGE_INCOMPLETE，前端须适配。period只决定图形分组（WEEK/MONTH按日，QUARTER/YEAR按月）；显式自选边界不因切period被暗改。左图与右图分别读取各自区间。

返回ArchiveResponse：`{systemCode,winchCode,metric,tensionMetric,period,comparisonType,comparisonTitle,comparisonAxis,historySampleCount,changePoints,comparisonCurrent,comparisonPrevious,requestedRanges,effectiveRanges,referenceRecordCodes,dataRange,asOf,summary,statisticsState,complete,nextCursor,aggregation,notices}`。statisticsState为READY/UPDATING/NOT_BUILT；各参考条件不满足时返回可比性和原因，不伪造0。

ArchivePoint为`{axisKey,label,time,progressPercent,tension,speed,normalAlarms,seriousAlarms}`，各不适用/未知字段null，数值t/m/min；后端准备axisKey和对齐位置，不按数组下标拼不等区间。normalAlarms/seriousAlarms仅有确证事件来源时给，不按拉力重新判警。

summary为`{average,maximum,minimum,unit,changeRate,comparable,comparisonReason,currentStatistics,referenceStatistics}`。前三项对应左图请求指标，使用完整范围事实而非缩减图点；changeRate是右图当前/参考均值百分比变化，10表示10%。分母0、样本不足或不具备同口径时null并说明，不用微小分母补比率。

PREVIOUS_PERIOD使用当前区间对应上一日历周期；YEAR_ON_YEAR使用同日历位置及明确截止的去年同期，闰日/月长回显实际边界。历史结束月份读取预计算，必要边界切片后台准备；NOT_BUILT不能伪装0，不能每次扫描全年原始。

PREVIOUS_RUN取当前记录之前最近一条已确认完成、同系统本机有参与且同生产UP/来源统计口径的记录；无候选则空态。HISTORY_AVERAGE取当前记录之前、明确参考范围内满足同一条件的集合，回显范围与实际historySampleCount，不默认30条。未传参考范围时默认从当前记录上海开始日零点至当前记录开始时刻，防无限扫描；前端需显示范围。COUNT未知开始/未确认结果不能自动当完整参考记录。

单次比较comparisonAxis=PROGRESS只有存在明确可信的归一化运动依据时可用；当前无已确认机械限位/统一进度来源，不用高度/30或样本序号凑0～100%。否则返回comparisonAxis=null、comparable=false与PROGRESS_MAPPING_NOT_READY，左图已知统计仍可返回。时间周期比较为TIME。未知进度不凭前端图形要求补造，具体归一化规则确认后再实施。

记录次数按开始日，测量按采集日，事件按明确显示起点，可靠运动区间跨日拆分；SYSTEM时长无独立依据null，不能用四机工时和代替。系统历史极值与本机归档指标不同，不混用。

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

GET必填systemCode，返回`{systemCode,startTime,intervalMinutes,speedMPerMinute,savedVersion,savedSettings,appliedVersion,appliedSettings,applyStatus,commandCode,message,asOf}`。settings均为`{startTime,intervalMinutes,speedMPerMinute}`；顶层三项是已确认生效值的页面投影，未知null，不用保存草稿填充。已保存与已生效不可冒充同一状态，编辑并发基准取savedVersion。

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

当前配置持久化/回读尚未闭环；D105～D108编码未确认，不强配D100～D103，不把D105当HH:mm或D107当速度。阈值不塞三参数，使用下面独立接口；中转时间控制仍不开放。

### 7.3 PUT C/winch/tension-thresholds

目标请求ThresholdUpdateRequest：systemCode、winchCode、requestId、expectedVersion、thresholds均必填。前两项选择真实本机；requestId规则同急停，同一次操作重试沿用；expectedVersion为detail.thresholdSettings.savedVersion，未准备好409，不以appliedVersion代替。后两技术字段是对新附件的必要补充，当前前端需同步发送。

thresholds恰为该设备两路真实sensorCode，每项alarmThreshold/safetyValue为固定t的有限正数且预警小于保护。归属、权限与必要规则由所属模块检查一次；没有PLC已确认物理上限时不拿1.4t/3t图轴限制输入，也不据新阈值修改事件状态。

```json
{
  "systemCode": "QJXT-01",
  "winchCode": "WJ-02",
  "requestId": "thresholds-20261025-001",
  "expectedVersion": "7",
  "thresholds": [
    { "sensorCode": "PS-03", "alarmThreshold": 0.8, "safetyValue": 1.2 },
    { "sensorCode": "PS-04", "alarmThreshold": 0.8, "safetyValue": 1.2 }
  ]
}
```

示例版本和值不表示现场已存在。HTTP202受理或200查询到已确认结果，业务code均200。响应ThresholdApplyResponse为`{systemCode,winchCode,requestId,commandCode,status,savedVersion,configurationVersion,effectiveThresholds,confirmationState,message,demo}`。status为PENDING/APPLIED；configurationVersion仅表示真实已确认生效版本，未知null；demo生产false。

PENDING的effectiveThresholds是上次已确认两路快照或null，confirmationState=LAST_CONFIRMED/UNCONFIRMED，不覆盖草稿为现场事实。APPLIED必须两路完成真实PLC生效确认，confirmationState=CONFIRMED，effectiveThresholds返回实际值（结构同thresholds），不能用写应答或请求值冒充。若部分写入/确认，要在命令结果与message说明，不能把旧快照描述成此刻已验证的完整现场配置。

```json
{
  "code": 200,
  "message": "申请已受理，尚未确认PLC生效",
  "data": {
    "systemCode": "QJXT-01",
    "winchCode": "WJ-02",
    "requestId": "thresholds-20261025-001",
    "commandCode": "CMD-EXAMPLE-001",
    "status": "PENDING",
    "savedVersion": "8",
    "configurationVersion": null,
    "effectiveThresholds": null,
    "confirmationState": "UNCONFIRMED",
    "message": "此示例没有旧确认快照，不能用申请值填充",
    "demo": false
  }
}
```

明确拒绝/失败用400/403/409/429/503及真实errorCode；已受理后失败通过commands回执与detail.thresholdSettings.applyStatus查询，不能永远假报等待成功。阈值具体地址、单位、倍率、两路回读协议未确认时503 THRESHOLD_MAPPING_NOT_READY，不预设寄存器、不虚构APPLIED。PENDING后重新读取detail或按commandCode查询，不在前端修改工况或告警。

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

| 方法 | 请求 | 目标返回 |
|---|---|---|
| POST /api/auth/login | JSON username、password，均必填 | {token,refreshToken,expiresIn,expiresInUnit:"SECONDS"} |
| POST /api/auth/refresh | JSON refreshToken，必填 | {token,refreshToken或null,expiresIn,expiresInUnit:"SECONDS"} |
| GET /api/auth/user-info | Bearer真实账号 | {id,username,nickname,avatar,email,phone,roles,roleNames,permissions,systems,currentSystemCode} |
| POST /api/auth/logout | Bearer当前身份，无业务body | data=null，message说明真实退出范围 |

目标expiresIn为新访问令牌的有效秒数，正整数；当前Java login返回毫秒、refresh只有token，需要实际适配，不能只在文档写秒。刷新时检查真实账号状态；只有实施轮换才返回新refreshToken，否则null让前端保留原有效刷新凭据。不宣称当前logout已撤销所有JWT，真实会话撤销机制仍待实施。会话到期与采集数据时间校验没有关系。

user-info.roles为实际编码数组、roleNames为{code,name}数组，systems为授权{systemCode,systemName}；未知资料null，用户ID公开string（当前前端number类型需兼容），实际currentSystemCode或null，不硬编码QJXT-01。未认证/禁用/无权分别401/403，不继续沿用当前fallback permitAll。

`GET /api/users`：keyword可选去空白；page默认1、pageSize默认10最大100。需user:read；后端按账号/姓名/角色名称或编码筛选，创建时间倒序、用户ID倒序稳定分页。返回`{list,total,page,pageSize,asOf}`，行：`{id,username,nickname,roles,roleNames,lastLoginAt,lastLoginState,createdAt}`。

lastLoginState=KNOWN/NEVER/UNKNOWN：只有确证从未登录才NEVER；历史未采集时UNKNOWN且lastLoginAt=null，不能显示“从未登录”。当前前端把null当从未登录，必须同步消费该状态；当前数据库没有该事实来源则如实UNKNOWN。用户资料不返回密码/哈希，不因页面有roles就宣称菜单授权已经完成。

页面只读，不新增创建/编辑/删除/重置密码入口。模型管理、历史巡检、数据分析仍待开放，视频范围外。旧管理员CRUD留在[当前接口参考](current-api-reference.md)，不作为本期用户页调用。

## 10. 错误与页面调用顺序

目标错误为`{code,message,data:{errorCode,...必要公开信息}}`；成功无业务对象时data=null。安全过滤器401现状仍可能无体，前端兼容。旧命令HTTP200＋REJECTED等以现有接口参考为准，此处不声称已修改。

| HTTP | 场景 |
|---|---|
| 200 | 查询成功；空态/真实缺值/统计准备情况用结构说明 |
| 202 | 命令或导出已受理，业务code仍为200，data给真实阶段，不代表已停止/生效/生成文件 |
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
| 告警 | latest共享快照/hasAttention，history独立条件分页，事件详情只读 |
| 阈值 | detail取得真实版本→提交两路阈值→PENDING保留旧确认值/APPLIED采用返回确认值 |
| 归档 | 提交完整三组区间→后端两图、对齐轴、汇总，不在前端算同比 |
| 操作 | 真实账号提交→显示阶段→命令读取/重新查询；设备态仍按采集 |
| 导出 | 创建任务→查询状态/列表→完整发布后下载 |

前端可做必填/数字/查询起止提示、日期单位格式化、固定样式和加载/网络/空态提醒。状态来自明确PLC事实，配对总力、统计、同比、历史结果、事件过程由后端整理返回；缺少说明时只用通用失败提示，不造业务原因。

本轮仅文档。当前Java、PLC模板和七表基线未迁移；完整规则见[业务总览](business-flow-and-page-data-overview.md)、[数据库设计](database/page-driven-database-design.md)、[职责对齐](frontend-backend-contract-alignment.md)。
