# 对外接口说明：请求参数、返回字段与页面调用

日期：2026-10-24。本文面向前后端对接，依据当前Java接口、[业务流程总览](business-flow-and-page-data-overview.md)、最新PLC点位、页面字段说明及历史/导出设计编写。

**第2节为现有清洗接口的实际契约；第3～8节为待实现接口与字段设计；第9节记录认证和账号接口现状。** 本轮仅编写文档，没有创建新接口、调整序列化、执行DDL或适配新PLC点位。所有示例编号和数值仅用于解释格式。

## 1. 通用约定

### 1.1 地址、身份和返回包装

清洗业务统一前缀记为`B = /api/cleaning/devices/{deviceId}`。文中`GET B/history/runs`应展开为例如`GET /api/cleaning/devices/plc-01/history/runs`。

- `deviceId`为配置的PLC设备标识；`winchId`为实际动力设备标识；`recordCode`为运行公开编号；`sampleFrameId`和`eventCode`分别定位历史帧与事件。不恢复Cloud/box路径或盒子凭据。
- JSON请求使用`Content-Type: application/json`。业务请求携带`Authorization: Bearer <访问令牌>`；刷新令牌不作业务访问令牌，不把令牌放URL。
- 操作账号由登录身份取得。请求不接受`operatorId`代替真实操作者，也不接收PLC地址、任意SQL或物理表名。
- GET无请求体。本文列出的路径参数均必填；查询参数和JSON字段的必填性逐项说明。

现有`Result<T>`包装为：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "plc-01"
  }
}
```

| 字段 | 类型 | 含义 |
|---|---|---|
| `code` | integer | 包装状态码；普通成功200，命令受理可为202 |
| `message` | string | 本次响应说明；不能单凭中文文本判断命令结果 |
| `data` | object/array，可缺省 | 业务对象；错误或无返回对象时可省略 |

当前Jackson配置为`NON_NULL`，缺失值通常**省略字段**，不能假定所有空值都显式返回`null`。本文新契约同样将可空字段定义为可缺省；客户端统一把缺省或null当作无值，保留有效数值0。空列表返回`[]`，已知空集合数量返回0，尚未计算或无法确认的数量不能补0。

除特别给出完整包装外，第3～8节的返回示例仅展示`Result.data`。下载成功直接返回文件流，SSE返回事件流，两者不当普通JSON响应解析。

### 1.2 单位、时间和范围

| 项目 | 对外约定 |
|---|---|
| 拉力/电机等效力 | 普通数据接口统一kg；页面t＝kg÷1000；电机不是N·m |
| 高度/速度 | m、m/min；同步速度与本机速度注明来源 |
| 历时/运动时长 | 带`Seconds`字段以秒计，可含小数；`runningHours`以小时计 |
| 时刻 | 清洗接口ISO 8601带时区，例如`2026-10-24T08:00:00+08:00`；当前Instant通常序列化为UTC的`Z` |
| 日历日期 | `YYYY-MM-DD`，业务时区固定`Asia/Shanghai` |
| 日期筛选 | `fromDate`包含，`toDateExclusive`不包含；查10月24日用24日到25日 |
| 时刻筛选 | `fromAt`包含，`toAt`不包含；用于局部曲线查询 |
| 大整数标识 | 新接口公开ID和原始采样序号用字符串；现有实时`sequence`仍为JSON整数，SSE id为字符串 |

采集数据不校验时间早晚、年龄、当前时钟偏差或是否早于上一帧。查询范围只是用户选择的数据范围，不是采样拒收条件；离线依据实际通信失败。认证期限、命令等待和导出执行预算各按自身用途处理，不表示采集数据过期。

新接口输入只检查必要标识、权限、范围和资源预算，不在各层重复同一规则。普通分页不保证多次HTTP请求冻结在同一数据库版本；需要完整一致文件时使用后台导出。

### 1.3 接口目录

| 状态 | 方法和路径（B见上） | 页面/用途 |
|---|---|---|
| 已存在 | `GET B/realtime` | 首页最新快照 |
| 已存在 | `GET B/realtime/stream` | 首页SSE |
| 已存在 | `GET B/statistics` | 本进程观测统计，PROCESS |
| 已存在 | `GET B/settings` | 当前参数同步状态 |
| 已存在 | `PUT B/settings` | 现有三参数修改请求 |
| 已存在 | `POST B/emergency-stop` | 急停请求 |
| 已存在 | `GET B/commands/{requestId}` | 本地命令状态 |
| 待实现 | `GET B/winches/{winchId}` | 单机档案、当前值、记录与历史摘要 |
| 待实现 | `GET B/history/runs` | 上行运行列表 |
| 待实现 | `GET B/history/dates` | 日期选择器可用日期 |
| 待实现 | `GET B/history/runs/{recordCode}` | 单次概要和四机摘要 |
| 待实现 | `GET B/history/runs/{recordCode}/curves` | 一次记录某台的有界曲线 |
| 待实现 | `GET B/history/samples/{sampleFrameId}` | 精确历史帧/悬停读数 |
| 待实现 | `GET B/history/events` | 独立或关联记录的异常列表 |
| 待实现 | `GET B/history/events/{eventCode}` | 异常及有限快照 |
| 待实现 | `GET B/history/periods` | 日/月/季/半年/年报表 |
| 待实现 | `GET B/history/height-curve` | 某周期某台的高度平均 |
| 待实现 | `POST B/exports` | 创建导出任务 |
| 待实现 | `GET B/exports` | 导出记录列表 |
| 待实现 | `GET B/exports/{taskCode}` | 任务状态与文件清单 |
| 待实现 | `GET B/exports/{taskCode}/files/{fileCode}` | 下载完整文件 |

不提供远程启动、复位、解除急停、暂停/恢复或单轴升降接口。没有历史写入、手工补PLC采样或任意寄存器写入的外部接口。

## 2. 现有清洗接口：以代码行为为准

### 2.1 GET B/realtime

输入：路径`deviceId`，无其他参数。成功HTTP 200，即使尚无快照也不是404。

尚无快照的实际结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "plc-01",
    "available": false,
    "message": "尚未取得设备实时数据"
  }
}
```

有快照时`Result.data`为`{deviceId,available,message,data}`，内层`data`是实时对象。页面从`response.data.data`取测量，不能少取一层。`available=true`仅说明存在快照，不等同于设备在线或所有测量有效。

| 内层字段 | 类型/单位 | 含义 |
|---|---|---|
| `sequence / sampledAt` | integer / time | 采集序号和记录时刻 |
| `status` | Status，可缺省 | 系统状态对象，见下表 |
| `ropes / winches` | Rope[] / Winch[] | 按实际ID绑定，四机八索，不依赖数组下标 |
| `groupAverageTension / groupAverageSpeed / groupAverageHeight` | number，可缺省 | 有效索均值kg、有效本机速度均值m/min、均高m |
| `heightSpread` | number，可缺省，m | 已取得高度的差值，不是机械限位 |
| `ringHeightMeters / heightValid` | number可缺省 / boolean | 独立环体高度及有效性；均高不替代它 |
| `validTensionCount / validSpeedCount` | integer | 当前相应有效数量 |
| `groupSpeedMetersPerMinute` | number，可缺省 | 整组速度字段，不冒充四路独立实测 |
| `tensionUnit / speedUnit / heightUnit` | string | 当前分别为`kg / m/min / m` |

| 对象 | 完整字段与类型 |
|---|---|
| Status | `systemStatus:string`、`systemStatusTextZh:string`、`online:boolean`、`statusValid:boolean`、`emergencyActive:boolean`、`faultCode?:string`、`direction?:string` |
| Rope | `ropeId:string`、`sensorId:string`、`winchId:string`、`tension?:number`、`valid:boolean` |
| Winch | `winchId:string`、`heightMeters?:number`、`speed?:number`、`tensionAverage?:number`、`online:boolean`、`running:boolean`、`faultCode?:string`、`torqueKg?:number`、`torqueValid:boolean`、`heightValid:boolean`、`speedValid:boolean`、`status?:string`、`statusTextZh:string`、`direction?:string`、`validTensionCount:integer` |

当前五态名称为`STANDBY/RUNNING/MAINTENANCE/FAULT/OFFLINE`。`tensionAverage`是本机索均值，不是两索总拉力。明确有效性为false的通道不参与合计和默认统计。当前转换器尚未按新版D201/D203～D206完全适配；不能因字段存在就声称新点位已经接通。目标扩展见第3.1节。

### 2.2 GET B/realtime/stream

输入同实时查询，另使用`Accept: text/event-stream`，仍需Bearer访问令牌。使用支持Authorization请求头的SSE客户端或fetch流。

```text
event: realtime
id: 123
data: {"code":200,"message":"success","data":{"deviceId":"plc-01","available":true,"message":"已取得设备实时数据","data":{"sequence":123}}}

:keepalive
```

上例仅缩略展示测量部分；实际`data:`内容与GET realtime完整包装一致。首次发送当前视图，无快照时无id；同序号发送保活注释。默认每1000ms检查新快照，不承诺推送每个PLC采样。

当前最多64个连接，每连接最长60秒，结束后客户端重新鉴权连接；持续检查账号/权限和令牌期限。没有实现Last-Event-ID历史重放、补发或业务error事件，连接结束应按HTTP/重连结果处理。断流不自动等同PLC离线。

### 2.3 GET B/statistics

输入仅`deviceId`。HTTP 200，返回`CleaningStatisticsVo`，不接受日期参数来变成历史报表。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "plc-01",
    "scope": "PROCESS",
    "acceptedSamples": 0,
    "metrics": []
  }
}
```

有样本时另有`firstSampleAt、lastSampleAt`。`metrics[]`字段为`metric:string、targetId:string、unit:string、validSamples:integer、minimum:number、maximum:number、sampleAverage:number`。

当前metric为`ropeTension / winchSpeed / winchHeight / winchTorque / ringHeight`，分别按索、动力设备或设备标识关联。`acceptedSamples`是进程接受的帧数，包括部分/全部无效或离线帧；各指标的有效数量看`metrics[].validSamples`。指标均值/极值只用有效测量，重启清空，不区分已识别生产上行，也不表示数据库已归档。日/月等使用第6节的新接口。

### 2.4 POST B/emergency-stop

| JSON输入 | 类型 | 必填/规则 |
|---|---|---|
| `requestId` | string | 是；1～128位`A-Za-z0-9._-`，一次操作重试沿用原值 |
| `reason` | string | 否；最大512个Java字符长度，空白默认“用户急停” |

```json
{
  "requestId": "stop-20261024-001",
  "reason": "操作员请求停止"
}
```

返回公共Command对象，HTTP映射见第2.7节。设备写入尚未确认的示例：

```json
{
  "code": 202,
  "message": "操作已进入设备队列，尚未确认写入。",
  "data": {
    "requestId": "stop-20261024-001",
    "commandId": "command-example-001",
    "deviceId": "plc-01",
    "type": "emergency_stop",
    "state": "QUEUED",
    "code": "QUEUED",
    "message": "操作已进入设备队列，尚未确认写入。",
    "createdAt": "2026-10-24T00:00:00Z",
    "expiresAt": "2026-10-24T00:00:30Z",
    "updatedAt": "2026-10-24T00:00:00Z"
  }
}
```

最新业务目标是M50写1，但当前模板未自动完成新点位适配。客户端只发急停意图，不发M50地址或值；不双写M15、不写0解除。`SUCCEEDED + PLC_EMERGENCY_WRITE_CONFIRMED`仅证明写应答，设备是否停下仍看实际事实，不承诺M2一定置1。

### 2.5 GET B/settings 与 PUT B/settings

GET输入仅`deviceId`。当前初始响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "plc-01",
    "state": "NOT_SYNCHRONIZED"
  }
}
```

| 返回字段 | 类型 | 含义 |
|---|---|---|
| `deviceId / state` | string | 当前状态为`NOT_SYNCHRONIZED`或`WRITTEN_NOT_PERSISTED` |
| `version` | integer，可缺省 | 当前尚无完整配置持久化闭环，不能自行填0 |
| `settings` | object，可缺省 | `startTime:string、intervalSeconds:integer、groupSpeedMetersPerMinute:number` |

PUT输入：

| JSON字段 | 类型 | 当前代码要求 |
|---|---|---|
| `requestId` | string | 同急停请求编号规则 |
| `expectedVersion` | integer | 必填、≥0；应来自已确认版本，前端不猜版本 |
| `settings.startTime` | string | 必填，推荐`HH:mm`；可解析秒但实际只允许秒/纳秒为0 |
| `settings.intervalSeconds` | integer | 必填、>0；当前没有额外业务上限 |
| `settings.groupSpeedMetersPerMinute` | number | 必填、>0且≤10；这是当前软件限制，不证明现场额定值 |

以下仅为字段格式示例，`expectedVersion=7`不代表现场存在此版本：

```json
{
  "requestId": "settings-20261024-001",
  "expectedVersion": 7,
  "settings": {
    "startTime": "08:00",
    "intervalSeconds": 300,
    "groupSpeedMetersPerMinute": 5
  }
}
```

返回Command。当前即使PLC参数写应答成功，仍是`UNCONFIRMED / PLC_SETTINGS_WRITTEN_STORAGE_PENDING`；设置视图为`WRITTEN_NOT_PERSISTED`，version仍缺省，在途修改尚未解除。前端应显示未完成并停止连续提交，不能显示“全部设置成功”。

新版D100～D103与D105～D108的编码、单位和读回关系未明确，不把D105当HH:mm、D107当速度；本轮不增加中转时间修改字段。

### 2.6 GET B/commands/{requestId}

输入：路径设备与原请求编号，无请求体。找到返回HTTP 200和Command；不存在或属于另一设备返回404。命令存于当前进程，重启后不能当作持久操作审计查询。

| Command字段 | 类型 | 含义 |
|---|---|---|
| `requestId / commandId / deviceId` | string | 客户请求编号、后端命令编号、设备 |
| `type` | string | `emergency_stop`或`update_cleaning_parameters` |
| `state` | string | `CREATED/QUEUED/SUCCEEDED/REJECTED/FAILED/UNCONFIRMED/BLOCKED` |
| `code / message` | string | 具体业务原因与说明，需结合state解释 |
| `createdAt / expiresAt / updatedAt` | time | 命令时间，不是采集数据有效期限 |

当前同requestId且设备、操作人、类型或参数不同会400；相同请求在登记仍保留时返回原状态。登记为进程内数据，终态/未确认条目还可能被清理，不承诺跨重启或永久幂等；不可盲目重试物理操作。结果不确定时核对现场，不自动重发或回滚。

UNCONFIRMED不是终态，后续仍可能收到可关联结果并推进状态；页面不能直接把它改成FAILED或“已完成”。参数未持久化的未确认原因仍按第2.5节显示。

### 2.7 当前HTTP与业务状态

| 情况 | 当前HTTP及包装行为 |
|---|---|
| 普通GET成功、找到命令 | 200，Result.code=200；命令内部仍可能FAILED/REJECTED |
| POST/PUT新入队等非终态 | 202，Result.code=202 |
| POST/PUT的BLOCKED | 503，data仍包含Command |
| POST/PUT的SEND_QUEUE_FULL | 429，data仍包含Command |
| POST/PUT其他终态或UNCONFIRMED | 200；版本冲突、已有在途参数可能是200＋REJECTED，不是409 |
| 必要参数规则触发IllegalArgumentException | 400；同requestId内容冲突也在这里 |
| 未登录/无效访问令牌 | 401；安全过滤器可返回空响应体，不保证Result包装 |
| 当前账号无权限 | 通常403；进入业务异常处理时为Result错误 |
| 未找到命令/未实现路由 | 404 |
| 未分类错误 | 500；当前坏JSON/缺请求体没有统一400处理，不承诺所有格式错误已规范化 |

前端先处理HTTP失败及可能空响应体，再读取Result和业务状态。不要把HTTP 200、Result.code=200或`available=true`分别当成操作成功、设备运行正常或采集完整。

## 3. 新页面数据契约：待实现

以下仅定义目标，不扩充已存在接口的实际返回。新查询继续走大厅/安全→清洗服务→快照或统计查询，不让Controller直接读DAO。

### 3.1 实时对象的目标补充

保留第2.1节结构，在同一对象中补页面确实需要的事实：

| 位置/新增字段 | 类型 | 来源与说明 |
|---|---|---|
| 内层`captureId` | string | 采集实例；与sequence共同定位，不能只跨重启比较序号 |
| `status.rawStateCode` | integer，可缺省 | D201原码；缺码/未定义时保留statusValid=false，不冒充OFFLINE |
| `status.controlPowerOn / runPermitted / autoSafe / manualSafe / autoStarted` | boolean，可缺省 | M0/M4/M11/M12/M13，缺失不是false |
| `status.mode` | AUTO/MANUAL，可缺省 | M10，和启动方式分开 |
| `status.reasonCodes` | string[] | 有依据的故障/共享急停说明；不因普通系统故障声称四台电机都坏 |
| `winches[].rawStateCode / statusValid` | integer可缺省 / boolean | D203～D206本机原码和有效性 |
| `winches[].motionRole / segmentCode` | string，可缺省 | 已明确的本机用途/连续段，不猜PLC阶段 |
| `winches[].speedSource` | AXIS/SYNCHRONOUS/UNCONFIRMED | 适用速度来源；同步值保留独立字段 |
| `winches[].pairedTensionKg / pairedTensionValid` | number可缺省 / boolean | 明确采集口径下本机两索配对总力 |

合法五态仍为原五态；无法确认时业务状态可缺省并用statusValid=false表达，不新增虚假的“正常”或以时间判离线。M2/M1/M3明确安全事实优先；详细规则见统一方案。正常上下行实时都推送。

### 3.2 公共记录对象 RunEntry

历史列表、单次详情和单机当前/上一条记录复用以下字段，不各自命名一套：

| 字段 | 类型 | 口径 |
|---|---|---|
| `recordCode / deviceId / systemCode` | string | 稳定公开记录编号/设备/内部系统键，不用显示编号充当内部系统键 |
| `displayCode` | string，可缺省 | 实际系统显示编号，例如配置中的QJXT-01 |
| `kind / recordScope / directionScope` | 固定RUN/LEG/UP | 一次生产上行，不表示完整往返 |
| `winchIds` | string[] | 已确认参与设备；四台同步仍一条系统记录 |
| `startedAt / endedAt` | time，可缺省 | 实际已确认边界，进行中无未来结束 |
| `firstObservedAt / lastObservedAt / businessDate` | time/time/date，可缺省 | 观测与开始日分开；未知开始不造businessDate |
| `state` | IN_PROGRESS/ENDED/UNCONFIRMED | 记录过程状态，与设备五态分开 |
| `runResult` | COMPLETED/TERMINATED/UNCONFIRMED，可缺省 | 进行中无最终结果；COMPLETED表示确认上行完成 |
| `completionKind` | NORMAL/RECOVERED/UNCONFIRMED，可缺省 | 仅COMPLETED使用；无足够异常覆盖证据不强写NORMAL |
| `terminationReason / startMethod / mode` | string，可缺省 | 真实原因、已知启动来源、AUTO/MANUAL；AUTO不等于定时 |
| `durationSeconds / motionDurationSeconds` | number，可缺省 | 起止历时与有独立依据的系统生产运动时长；不拿四机之和替代 |
| `abnormalCount` | integer | 本次可靠关联事件数，不是尝试数 |
| `ropeMaxKg / ropeMinKg` | number，可缺省 | 本次PRODUCTION_UP有效钢绳极值，不含电机/配对总力/下行快照 |
| `hasDataGap / boundaryConfirmed` | boolean | 已知缺口及实际边界确认情况；不是采样过期 |

例：

```json
{
  "recordCode": "RUN-example-001",
  "deviceId": "plc-01",
  "systemCode": "system-01",
  "displayCode": "QJXT-01",
  "kind": "RUN",
  "recordScope": "LEG",
  "directionScope": "UP",
  "winchIds": [
    "w1",
    "w2",
    "w3",
    "w4"
  ],
  "startedAt": "2026-10-23T23:58:00+08:00",
  "endedAt": "2026-10-24T00:03:00+08:00",
  "businessDate": "2026-10-23",
  "state": "ENDED",
  "runResult": "COMPLETED",
  "completionKind": "RECOVERED",
  "durationSeconds": 300,
  "abnormalCount": 1,
  "ropeMaxKg": 520,
  "ropeMinKg": 310,
  "hasDataGap": false,
  "boundaryConfirmed": true
}
```

本例系统运动时长、启动来源等无可靠值而省略。NORMAL要求确认完成且有依据证明无相关异常；RECOVERED要求相关异常已明确恢复。终止原因保留真实急停/中断/故障，不要求五次失败。未知结果保留UNCONFIRMED，不凑成正常完成。

### 3.3 GET B/winches/{winchId}

输入只有路径设备和本机ID；当日按服务端Asia/Shanghai日历。返回以下有界组合对象，无历史曲线数组：

| 字段 | 类型 | 内容 |
|---|---|---|
| `deviceId / winchId / businessDate` | string/string/date | 本次查询范围 |
| `profile` | object | `name?、displayCode?、groupNo、ratedLoadKg?、ratedSpeedMetersPerMinute?、maintenanceAt?`；无配置不编值 |
| `sensors` | object[]，两项 | `ropeId、sensorId、slotNo、name?、businessRole?、warningThresholdKg?、protectionThresholdKg?` |
| `realtime` | object，可缺省 | `available、sequence?、sampledAt?、winch?、ropes`；winch/ropes复用实时字段，仅该台 |
| `currentRecord / previousRecord` | RunEntry，可缺省 | 本机参与的真实当前/上一条生产记录；无记录不造默认对象 |
| `todayStatistics` | object | `buildState、runCount?、completedCount?、terminatedCount?、unconfirmedCount?、dataFrom?、dataThrough?`；从周期统计读取 |
| `lifetime` | object | `motionCoverage、runningHours?、dataFrom?、dataThrough?、hasDataGap`；只累计不重叠周期的已确认本机生产运动 |

`buildState=READY/UPDATING/NOT_BUILT`；未构建时统计值缺省，不报0次。`motionCoverage=COMPLETE/PARTIAL/UNCONFIRMED`按证据表达，PARTIAL只能显示“已确认部分”，不能称完整累计。系统已确认全部时长不等于本机已确认全部时长。

## 4. 历史列表与单次详情：待实现

### 4.1 查询资源预算

以下为初版接口预算建议，落地配置须保持前后端一致；它们限制一次响应，不限制历史保存年限，也不是PLC阈值。

| 请求 | 默认值与最大值 |
|---|---|
| 运行列表 | page=1、pageSize=10；pageSize最大100 |
| 事件、周期、导出列表 | page=1、pageSize=20；pageSize最大100 |
| 可用日期 | 必须给日期窗口，一次最多366个自然日，可分窗口请求多年 |
| 单次曲线 | 一次1条记录、1台设备、最多4个来源；每来源默认1000/最多2000个返回点，总计最多8000点，含断段标记 |
| 高度平均 | 一次1个自然周期、1台设备、最多4来源；每来源默认1000/最多2000个高度桶，总计最多8000桶 |
| 导出选中编号 | 一次最多100个recordCodes或eventCodes；更大集合使用范围导出 |

页大小超上限返回400说明范围要求，不悄悄返回一半再说完整。图表若缩减，返回实际粒度和是否完整；不能为了点数上限跨缺口连线。普通列表可能随补入/结果变化更新，前端刷新后采用新total，不拿普通多页查询拼一致导出文件。

### 4.2 GET B/history/runs

| Query | 类型 | 必填/默认 | 含义 |
|---|---|---|---|
| `fromDate / toDateExclusive` | date | 是 | 北京时间日期范围 |
| `dateBasis` | STARTED/FIRST_OBSERVED | 否，STARTED | 按实际开始或首次观测筛选；明确回显，后者不改变统计开始日 |
| `winchId` | string | 否 | 只选本机参与记录；entry仍是系统上行记录 |
| `state / runResult / completionKind` | 第3.2节枚举 | 否 | 状态/结果筛选，非逗号拼接SQL |
| `page / pageSize` | integer | 否，1/10 | 分页，最大100 |

```text
GET /api/cleaning/devices/plc-01/history/runs?fromDate=2026-10-23&toDateExclusive=2026-10-24&dateBasis=STARTED&page=1&pageSize=10
```

返回：`{deviceId,filters,page,pageSize,total,entries:RunEntry[]}`。`filters`回显实际生效的输入，不回传未提交页面草稿。按所选日期依据对应时刻倒序、recordCode作稳定第二排序键。STARTED不包含无实际开始时刻的记录，FIRST_OBSERVED可查询这些记录，原startedAt仍缺省。

空结果示例：

```json
{
  "deviceId": "plc-01",
  "filters": {
    "fromDate": "2026-10-23",
    "toDateExclusive": "2026-10-24",
    "dateBasis": "STARTED"
  },
  "page": 1,
  "pageSize": 10,
  "total": 0,
  "entries": []
}
```

`total`是筛选后记录总数，不是当前页长度，不是完整清洗次数。列表不返回原始采样或全部异常数组。

### 4.3 GET B/history/dates

输入：`fromDate、toDateExclusive`必填；`kind=RUN/EXCEPTION`必填；`winchId`可选。kind=RUN时可选`dateBasis=STARTED/FIRST_OBSERVED`，默认STARTED；kind=EXCEPTION固定按已确认发生/首次观测日期，返回`dateBasis=EVENT`，不使用记录开始日。

返回：

```json
{
  "kind": "RUN",
  "dateBasis": "STARTED",
  "fromDate": "2026-10-01",
  "toDateExclusive": "2026-11-01",
  "timezone": "Asia/Shanghai",
  "dates": [
    "2026-10-23",
    "2026-10-24"
  ]
}
```

`dates`升序去重，仅该窗口有实际记录/事件的日期。读取轻量日期目录，不从当前十条猜日期，不扫描全量原始曲线。只有下行异常的日期在EXCEPTION中可见，不制造RUN记录。

### 4.4 GET B/history/runs/{recordCode}

输入仅路径编号。返回单次概要，曲线和事件另查：

| 字段 | 类型 | 说明 |
|---|---|---|
| `entry` | RunEntry | 与列表一致的系统上行概况 |
| `operator` | object，可缺省 | 可验证后台动作才有`userId:string、displayName:string`，现场启动不套用登录者 |
| `parametersKnown / parameters` | boolean / object[] | 参数项`key:string、value:number或string、unit?:string、source:string`；仅已确认当次参数，未知时false/[] |
| `winches` | object[] | 最多4台，固定结构`{winchId,roleSummaries:RunRoleSummary[]}` |
| `eventTotal` | integer | 本次关联事件总数，与entry.abnormalCount一致，不等于事件页长度 |

RunRoleSummary：`motionRole:string、buildState:string、motionCoverage:string、motionDurationSeconds?:number、heightMinMeters?:number、heightMaxMeters?:number、hasDataGap:boolean、metrics:object[]`。每台roleSummaries最多4种用途，最多4台×4用途，不生成四条运行。

`motionRole`取`PRODUCTION_UP/RECOVERY_UP/MAINTENANCE_UP/UNCLASSIFIED_UP`。`metrics[]`每项为`{source,validSampleCount,sumKg?,averageKg?,minKg?,maxKg?}`，source取`SENSOR1/SENSOR2/SERVO/PAIRED_SUM`。默认页面选择PRODUCTION_UP，其余用途明确单列；mean=sum/count，count=0时数值缺省。已知无有效样本与摘要未构建需分别说明，摘要另带`buildState`。

概要不返回未界定的五次尝试明细，也不把当前PLC参数替代当次快照。不存在或不属于路径设备的记录返回404。

### 4.5 GET B/history/runs/{recordCode}/curves

| Query | 类型 | 必填/默认 |
|---|---|---|
| `winchId` | string | 是，一次一台 |
| `sources` | 逗号分隔枚举 | 否，SENSOR1,SENSOR2,SERVO；可另选PAIRED_SUM |
| `motionRole` | 四种用途或ALL_UP | 否，PRODUCTION_UP；ALL_UP仅查询选项，不是数据库用途 |
| `fromAt / toAt` | time | 否，成对提供，限定记录内局部时间 |
| `heightMinMeters / heightMaxMeters` | number | 否，成对提供；局部显示筛选，不证明机械限位 |
| `maxPointsPerSeries` | integer | 否，1000；最大2000，预算覆盖该来源全部分段 |
| `cursor` | string | 否；仅沿用后端nextCursor和原筛选继续读取 |

返回对象：`{recordCode,winchId,directionScope:"UP",motionRole,sources,tensionUnit:"kg",heightUnit:"m",speedUnit:"m/min",reduction,complete,returnedPointCount,series,nextCursor?}`。

- `reduction=NONE/MIN_MAX`：返回全部范围内点，或为显示预算挑选真实点保留局部峰谷；不生成假的平均采样。
- `series[]`每项：`source、segments`。每个segment为`{segmentCode,motionRole,points,gapBefore:boolean}`；段间不连线。points按真实采集顺序，不按高度重新排列。
- 原始点：`{sampleFrameId,sequence:string,sampledAt,heightMeters?,valueKg?,valid:boolean,speedMetersPerMinute?,speedValid:boolean}`。无效值缺省并保留标记；高度缺失的点不能绘成0高度，仍能在原始帧/导出追溯。
- 已知换向、回退、通信/归档缺口保留断段。若单次预算容不下必要分段/断点，`complete=false`并给nextCursor；不能静默丢段。`complete=true`表示已覆盖请求范围，**不表示没有降采样**。

各来源返回点数和总数都受预算限制。精确极值读取概要中的原始统计，不从缩减后的曲线重算。当一次查询已完整且无点时`series=[]、complete=true`，不补零线。

### 4.6 GET B/history/samples/{sampleFrameId}

输入仅帧编号；服务器核验帧属于路径设备。返回一帧实际保存的UP本机数据，最多4台，不把正常下行未归档通道补回来：

```json
{
  "sampleFrameId": "FRAME-example-001",
  "captureId": "CAPTURE-example",
  "sequence": "123",
  "recordCode": "RUN-example-001",
  "sampledAt": "2026-10-24T00:01:00+08:00",
  "systemStateCode": 1,
  "winches": [
    {
      "winchId": "w1",
      "archived": true,
      "stateCode": 1,
      "direction": "UP",
      "motionRole": "PRODUCTION_UP",
      "segmentCode": "SEG-example-02",
      "heightMeters": 12.34,
      "heightValid": true,
      "axisSpeedMetersPerMinute": 3,
      "speedSource": "AXIS",
      "speedValid": true,
      "measurements": [
        {
          "source": "SENSOR1",
          "ropeId": "r1",
          "sensorId": "s1",
          "valueKg": 400,
          "valid": true
        },
        {
          "source": "SENSOR2",
          "ropeId": "r2",
          "sensorId": "s2",
          "valueKg": 420,
          "valid": true
        },
        {
          "source": "PAIRED_SUM",
          "valueKg": 820,
          "valid": true
        }
      ]
    }
  ]
}
```

本例只示意一台归档；实际返回已存本机集合。未知recordCode可缺省；`groupSpeedMetersPerMinute`独立可缺省。每台可另有`motorRaw、motorRuleVersion`和SERVO测量，按真实来源提供。frame标识表示采集关联，本身不证明多次Modbus读取在物理同一时刻；只有已明确的一致性/配对事实允许给出PAIRED_SUM，不能设猜测的时间容差。

悬停按真实帧定位，与曲线选中点对应。周期高度桶不具有原始sampleFrameId，不调用此接口伪造精确现场帧。

## 5. 异常历史：待实现

### 5.1 GET B/history/events

| Query | 类型 | 必填/默认 |
|---|---|---|
| `recordCode` | string | 可选；查单次事件时传入 |
| `fromDate / toDateExclusive` | date | 无recordCode时必填；有recordCode可成对提供进一步筛选 |
| `winchId` | string | 可选，按真实事件关联，不复制系统故障成四台本机故障 |
| `direction` | UP/DOWN/STOP/UNKNOWN | 可选；不填返回全部已存事件方向，已知停止不当成未知 |
| `outcome` | ACTIVE/RECOVERED/TERMINATED/UNCONFIRMED | 可选，按明确事件结果 |
| `page / pageSize` | integer | 默认1/20，最大100 |

返回`{filters,page,pageSize,total,entries:EventSummary[]}`，默认按事件日期所用时刻倒序、eventCode第二排序。日期使用明确实际发生时间，无该事实用首次观测时间，并在对象里注明来源。

| EventSummary字段 | 类型/口径 |
|---|---|
| `eventCode / deviceId / winchIds` | string/string/string[]，设备关联可为空数组表示系统事件 |
| `recordCode` | string，可缺省；没有关联也必须能查询 |
| `direction / faultCode / reason` | 方向、原始故障码可缺省、已知原因文本 |
| `occurredAt / firstObservedAt` | 已确认发生时刻可缺省、首次观测时刻 |
| `timeBasis` | OCCURRED/FIRST_OBSERVED，明确日期筛选依据 |
| `recoveredAt / terminatedAt / outcome` | 时刻可缺省、事件结果枚举 |
| `hasTriggerSnapshot / hasRecoverySnapshot / hasTerminationSnapshot` | boolean，是否有相应事件点 |

不返回`attemptCount`，D3=50不生成五次过程；D3=13设备归属仍按原提供资料未确认处理，不静默改成电机4。持续同一异常是一个事件，分页entries长度不等于整个记录abnormalCount。

### 5.2 GET B/history/events/{eventCode}

输入仅路径编号。返回`{event:EventSummary,snapshots:[EventSnapshot]}`。最多触发、恢复、终止三个已确认事件点，每个快照最多四台相关设备；没有快照则[]。

EventSnapshot为`{phase:TRIGGER/RECOVERY/TERMINATION,sampledAt,frameRef?,winches}`。winches复用第4.6节测量、身份和有效性结构，但方向按事实为UP/DOWN/STOP/UNKNOWN，不沿用原始UP帧的archived标识；`frameRef`仅用于关联，只有该帧确实归档为UP原始时才另给可查询的`sampleFrameId`。下行快照读数来自事件本身，不要求原始采样表存在对应帧。

正常下行没有全程曲线；快照数值不当作整个下行峰值。单事件不存在或不属于路径设备返回404。

## 6. 历史周期与高度平均：待实现

### 6.1 GET B/history/periods

| Query | 类型 | 必填/默认 |
|---|---|---|
| `scope` | SYSTEM/WINCH | 否，SYSTEM |
| `winchId` | string | scope=WINCH时必填；SYSTEM不传 |
| `periodType` | DAY/MONTH/QUARTER/HALF_YEAR/YEAR | 是 |
| `fromDate / toDateExclusive` | date | 是，分别为自然周期边界；例如月报1月1日至次年1月1日 |
| `page / pageSize` | integer | 默认1/20，最大100 |

一次响应只包含一种periodType和一个scope，不叠加年/月/日来算累计。边界按北京时间自然日历，季度从1/4/7/10月开始、半年从1/7月开始。取每个周期的已保存结果；空的完成统计与尚未构建分别表达，不触发请求内全量原始重算。

返回：`{deviceId,scope,winchId?,periodType,timezone,filters,page,pageSize,total,entries:PeriodSummary[]}`。entries按periodStart倒序，total表示查询范围内选定自然周期的数量；尚无构建结果的周期也可作为NOT_BUILT项目返回，不能伪装0次。

| PeriodSummary字段 | 类型/含义 |
|---|---|
| `periodStart / periodEndExclusive` | date，自然周期边界 |
| `buildState / periodClosed` | READY/UPDATING/NOT_BUILT；boolean表示日历周期已结束，不表示以后不会补数 |
| `computedVersion / requestedVersion / builtAt` | 版本字符串及计算时刻，可缺省；NOT_BUILT无已成功版本 |
| `countBasis / measurementBasis / directionScope / motionRole` | 固定STARTED_DATE / SAMPLED_DATE / UP / PRODUCTION_UP |
| `runCount / completedCount / normalCompletedCount / recoveredCount` | 该开始周期记录总数、完成数、正常完成数、异常恢复完成数；未构建时缺省 |
| `completedUnclassifiedCount` | 已确认完成但正常/恢复分类未确认数，等于completed-normal-recovered |
| `terminatedCount / ongoingCount / unconfirmedCount` | 同一开始周期记录中的终止、进行中、结果未确认，和完成数互斥 |
| `upEventCount / downEventCount / stoppedEventCount / unknownDirectionEventCount` | 按事件日期统计；已知停止和未知方向分别单列，不塞入UP/DOWN |
| `motionDurationSeconds / motionCoverage` | 本周期可靠生产运动时长可缺省；SYSTEM无独立持久区间依据时不填 |
| `ropeMinKg / ropeMaxKg` | 本周期采集日内生产有效钢绳原始极值，可缺省 |
| `curveRecordCount / sourceRecordCounts` | integer / `{source,recordCount:integer}[]`，本机全部四来源曲线组并集及各来源去重数；SYSTEM可省略 |
| `dataFrom / dataThrough / hasDataGap` | 实际测量范围可缺省、已知缺口；不表示时间校验 |

已构建时`runCount=completedCount+terminatedCount+ongoingCount+unconfirmedCount`；进行中的runResult不参与最终结果。未知实际开始的数据不混进该开始日总数。曲线参与记录数按采集日另算，因此不能要求它等于runCount。

例：月统计尚未构建时返回的单条entry：

```json
{
  "periodStart": "2026-10-01",
  "periodEndExclusive": "2026-11-01",
  "buildState": "NOT_BUILT",
  "periodClosed": false,
  "countBasis": "STARTED_DATE",
  "measurementBasis": "SAMPLED_DATE",
  "directionScope": "UP",
  "motionRole": "PRODUCTION_UP",
  "motionCoverage": "UNCONFIRMED"
}
```

已结束月份由后台预先计算；补入或结果修正后为UPDATING，可读上一成功版本并显示待更新。若先前没有成功版本，统计数值仍缺省。跨日记录结果回填开始日；采样按采集日，时长按可靠区间拆日，不重新增加一次运行。

### 6.2 GET B/history/height-curve

输入：`winchId、periodType、periodStart`必填，periodStart为该自然周期起点；`sources`可选，默认SENSOR1,SENSOR2,SERVO；`heightMinMeters/heightMaxMeters`可选且成对；`maxPointsPerSeries`默认1000、最大2000。方向及用途固定UP/PRODUCTION_UP，不接受DOWN/ALL或回退用途混进生产平均。

返回以下对象，只有一个period，前端比较去年/上月时按真实周期分别查询，不生成不存在的线：

| 字段 | 类型/含义 |
|---|---|
| `winchId / periodType / periodStart / periodEndExclusive` | 请求本机及实际自然周期 |
| `directionScope / motionRole / tensionUnit / heightUnit` | UP / PRODUCTION_UP / kg / m |
| `buildState / computedVersion / ruleVersion` | 已保存结果状态和版本；未构建版本可缺省 |
| `dataFrom / dataThrough / hasDataGap` | 实际参与测量范围及已知缺口 |
| `curveRecordCount` | 本次所选来源并集参与记录数，不相加source的次数 |
| `series` | 每来源统计及高度桶，见下 |
| `aggregation` | `kind=HEIGHT_BUCKET、requestedMaxPointsPerSeries、returnedPointCount、displayMerged:boolean` |

series项：`{source,recordCount,points,rawMinKg?,rawMaxKg?,minAverageKg?,maxAverageKg?}`。recordCount为该来源参与曲线的去重记录数，原始测量极值和曲线最高/最低平均分别命名；这里rawMin/rawMax限定本请求高度曲线参与集合，完整周期测量极值读取周期/来源摘要，不能漏掉无高度但拉力有效的事实。minAverage/maxAverage按实际查询高度范围内已存精细桶的均值取极值，响应中标明`statisticsGranularity=STORED_BUCKET`，不因显示合并桶改变统计值。

points项：`{heightFromMeters,heightToMeters,sampleCount,sumKg,averageKg,minKg,maxKg,speedSampleCount,speedSum?,averageSpeedMetersPerMinute?}`；高度桶下边界包含、上边界不包含。sampleCount是桶内该来源有效样本数，和recordCount不同；平均=sum/sampleCount，速度用自己的数量。

前端旧模型的`units[].sampleCount`若继续保留，应适配本响应curveRecordCount；`series[].recordCount`是各来源数量；`points[].sampleCount`才是样本数。三者不能混用。两个recordCount都指完整自然周期、所选来源的曲线参与数，局部高度缩放不改变此口径；它们不是“当前视窗参与数”。所选来源变化后的并集数量需由轻量分日摘要/已保存来源参与信息去重取得，不扫描原始帧，不能直接复用包含未选择来源的总数。

高度筛选选择与请求范围相交的已保存桶，返回桶的完整真实边界，不将半桶改名成请求边界或伪造部分桶平均。响应补`requestedHeightRange`和`actualHeightRange`（均为`{minMeters,maxMeters}`；无点时actual可缺省）说明实际范围。

桶太多时仅为响应合并相邻已有桶的sum/count/min/max，返回实际桶边界及displayMerged=true，不覆盖已存精细桶、不平均平均值。空高度区间保留断点或分段，不跨空洞合并成有数据区间；预算不足以保留这些边界时400提示缩小高度范围。默认无数据series=[]，无去年线不画0线；原始极值由完整统计字段表达，合并显示桶不改变其含义。

NOT_BUILT时series=[]且统计数值缺省；READY且整个周期所选来源均无有效高度数据时series=[]、curveRecordCount=0。仅当前高度窗口无点时series=[]，周期参与记录数仍保留真实值，可大于0。高度无效但拉力有效仍保留原始与生产测量摘要。高度桶不伪造sampleFrameId或现场采样时刻。

## 7. 导出任务与下载：待实现

### 7.1 POST B/exports

| JSON字段 | 类型 | 必填/规则 |
|---|---|---|
| `type` | RUN_LIST/UP_SAMPLES/EXCEPTIONS/PERIOD_STATISTICS | 是，对应固定业务模板 |
| `unit` | kg/t | 否，kg；仅改变拉力列显示单位，时间/高度/速度保持约定 |
| `filters` | object | 按筛选范围导出时提供；结构见下表，不含page/pageSize |
| `recordCodes` | string[] | 明确选中记录时使用；仅RUN_LIST/UP_SAMPLES，1～100项 |
| `eventCodes` | string[] | 明确选中事件时使用；仅EXCEPTIONS，1～100项 |
| `motionRole` | 四用途或ALL_UP | 仅UP_SAMPLES，默认ALL_UP；不默认丢掉回退/检修原始 |

`filters、recordCodes、eventCodes`三种范围选法恰选一种。单次“导出本次数据”传一个recordCode；不传前端隐藏设备/来源作为默认删除条件。文件模板版本由服务端固定并写入任务，不接受用户自定义SQL列。

| type | filters允许内容及日期含义 |
|---|---|
| RUN_LIST | 第4.2节日期、dateBasis、winchId、state、runResult、completionKind，日期必填；去掉分页字段 |
| UP_SAMPLES | `fromDate、toDateExclusive`必填，`winchId`可选；按采集日筛选，允许包含无运行关联的已存UP帧；用途用顶层motionRole |
| EXCEPTIONS | 第5.1节recordCode或日期范围、winchId、direction、outcome；去掉分页字段 |
| PERIOD_STATISTICS | 第6.1节scope、条件必填winchId、periodType和自然周期范围；去掉分页字段；输出对应报表及WINCH范围高度表，SYSTEM不生成混合四机平均线 |

示例：导出当前已查询范围全部运行摘要，而非当前10条。

```json
{
  "type": "RUN_LIST",
  "unit": "kg",
  "filters": {
    "fromDate": "2026-10-01",
    "toDateExclusive": "2026-11-01",
    "dateBasis": "STARTED"
  }
}
```

单次原始明细示例：

```json
{
  "type": "UP_SAMPLES",
  "unit": "kg",
  "recordCodes": [
    "RUN-example-001"
  ],
  "motionRole": "ALL_UP"
}
```

成功受理HTTP 202，完整包装示例：

```json
{
  "code": 202,
  "message": "导出任务已创建",
  "data": {
    "taskCode": "EXP-example-001",
    "type": "UP_SAMPLES",
    "state": "QUEUED",
    "unit": "kg",
    "templateVersion": "up-samples-v1",
    "createdAt": "2026-10-24T08:30:00+08:00",
    "recordCodes": [
      "RUN-example-001"
    ],
    "motionRole": "ALL_UP",
    "files": []
  }
}
```

前端只显示已受理，查询状态后再下载。服务端冻结已提交条件/单位，后台实际开始读取时建立一致快照；排队期间新出现且符合范围的数据可进入该快照。选中ID只固定成员，不冻结可变字段。不是复刻用户此前屏幕瞬间的全部值。

同一次任务不会因后来补数悄悄改变已发布文件。重新生成仍POST同一范围创建新任务；提交响应丢失时先查任务列表，不假设该POST已有持久幂等保障。

### 7.2 GET B/exports

Query全部可选：`type`、`state=QUEUED/RUNNING/SUCCESS/FAILED`、`fromDate/toDateExclusive`（成对、按任务创建日期）、`page/pageSize`（1/20，最大100）。默认仅查询当前登录账号在路径设备下创建的任务，按createdAt倒序、taskCode第二排序。

返回`{page,pageSize,total,entries:ExportTask[]}`。列表不返回文件正文，文件清单可只给已发布下载项；仍受现有设备读取权限和创建者身份约束。

### 7.3 GET B/exports/{taskCode}

返回同一ExportTask的完整状态与发布文件清单：

| 字段 | 类型 | 含义 |
|---|---|---|
| `taskCode / type / state` | string | 任务身份、四类模板、四个任务状态 |
| `filters / recordCodes / eventCodes / motionRole` | 请求中对应类型，可缺省 | 已冻结的实际范围，不含页面草稿 |
| `unit / templateVersion` | string | 固定单位和服务端模板版本 |
| `phase` | string，可缺省 | 如READING/WRITING/PACKAGING；阶段显示，不猜进度百分比 |
| `processedRows / outputRows` | integer，可缺省 | 实际已处理和最终输出行数，不含表头，不等同运行次数 |
| `createdAt / startedAt / finishedAt / snapshotAt` | time，后三项可缺省 | 创建、执行、结束、一致读取快照；不作采样年龄判断 |
| `files` | ExportFile[] | 已发布可下载文件；未完整发布为[] |
| `errorCode / errorMessage` | string，可缺省 | 失败原因，不暴露数据库堆栈或服务器路径 |

ExportFile：`{fileCode:string,fileName:string,mediaType:string,sizeBytes:integer,dataRows:integer,rowKind:string,downloadable:boolean}`。文件为CSV或ZIP；ZIP的dataRows是内部业务文件数据行合计，rowKind=MIXED，不称为运行条数。详情另有`tableCounts:[{name,rowKind,dataRows}]`描述包内不同业务粒度，0行文件也明确记0。

SUCCESS仅表示所有文件已完整发布；文件缺失/损坏时downloadable=false并说明需要重新生成，不能返回不存在的成功下载链接。普通账号不得通过猜taskCode下载他人任务；这些创建者检查是新接口实施要求，不宣称现有系统已有逐设备租户隔离。

### 7.4 GET B/exports/{taskCode}/files/{fileCode}

输入路径任务与公开文件编号，加Bearer访问令牌。文件编号映射后端清单，客户端不传存储路径。下载前重新核验登录、读取权限与任务归属。

成功HTTP 200：`Content-Type: text/csv; charset=UTF-8`或`application/zip`，`Content-Disposition: attachment`使用清单文件名，直接输出文件流，不套Result。CSV使用UTF-8 BOM；单文件CSV，多文件/分片ZIP，详细列见[导出方案](data-export-plan.md)。

任务未完成返回409说明尚不可下载；任务/文件不存在404；无权限403。文件流开始前错误可返回Result，开始传输后的网络中断不能改成另一份JSON，客户端应显示下载失败，不把残缺文件当成功。

服务端读写/打包与下载均采用有界缓冲；浏览器大文件要边接收边保存，不能用`fetch.blob()`把半年数据全缓存在内存。文件下载授权仍用受保护请求，不把令牌放URL。具体浏览器下载适配需在前端落地验证。

## 8. 新接口错误、缺值与页面调用顺序

### 8.1 目标错误契约

以下是新接口实施要求，不将第2节现有命令HTTP行为自动改成这一套。错误包装采用`{code,message,data?:{errorCode}}`；无可公开详情可省略data。401在安全层仍需兼容空响应体。

| HTTP | 新接口含义 | 示例errorCode |
|---|---|---|
| 400 | 缺少必要参数、无效查询组合、分页或图表预算不满足 | INVALID_QUERY、QUERY_BUDGET_EXCEEDED |
| 401 | 未登录或访问令牌不可用 | UNAUTHENTICATED（若有JSON体） |
| 403 | 缺少读取权限或任务归属不符 | FORBIDDEN |
| 404 | 设备/本机/记录/帧/事件/任务/文件不存在，或不属于路径设备 | RESOURCE_NOT_FOUND |
| 409 | 已知任务当前不可下载 | EXPORT_NOT_READY、EXPORT_FILE_UNAVAILABLE |
| 429 | 服务端任务受理资源已满 | EXPORT_QUEUE_FULL |
| 503 | 查询依赖暂不可用，不能正常读取 | HISTORY_UNAVAILABLE |
| 500 | 未分类内部错误 | INTERNAL_ERROR |

无记录、无有效测量、历史缺口、尚未构建统计属于正常业务数据，用200加空集合/有效性/NOT_BUILT表达。数据库失败不能伪装为200空列表；采集时间早晚或延迟入库不会产生DATA_EXPIRED之类错误。

### 8.2 页面怎么调用

| 页面动作 | 调用顺序 |
|---|---|
| 首页打开 | 登录后订阅realtime/stream，首次事件即当前视图；需要单次读取时GET realtime |
| 打开单机 | GET winches/{winchId}取档案/历史摘要；实时变化仍复用SSE按winchId选取 |
| 查看历史 | GET history/dates取日历窗口；提交筛选后GET history/runs，翻页只更新列表 |
| 打开一次记录 | GET history/runs/{recordCode}；再按选中设备读curves和分页events |
| 精确悬停 | 对真实点用sampleFrameId查询；周期平均展示桶统计，不查询假帧 |
| 看今日/月/季度等 | GET history/periods取报表，GET history/height-curve取所选本机和周期平均 |
| 看下行异常 | GET history/events?direction=DOWN；点开事件取有限快照 |
| 导出 | POST exports→GET任务状态/列表→成功后GET文件流；不用浏览器分页拼数据库 |
| 急停/参数修改 | 提交原requestId→查询commands→显示命令状态；设备状态仍由实时采集决定 |

历史曲线单位转换由前端按响应单位执行一次；切换kg/t不重查同一曲线。勾选已加载来源可在前端隐藏；若改变周期曲线“所选来源参与记录并集”范围，按第6.2节查询轻量统计，不在前端把来源次数相加。

## 9. 认证与账号接口现状

### 9.1 认证

以下现有接口沿用Result包装；只说明真实行为，不引入新的登录机制。

| 方法/地址 | 输入 | 成功data |
|---|---|---|
| `POST /api/auth/login` | JSON：`username:string、password:string` | `{token:string,refreshToken:string,expiresIn:integer}` |
| `POST /api/auth/refresh` | JSON：`refreshToken:string` | `{token:string}`；不返回新刷新令牌或expiresIn |
| `GET /api/auth/user-info` | 无请求体，客户端携带访问令牌 | `id:number、username、nickname?、avatar?、email?、phone?、roles:string[]、permissions:string[]` |
| `POST /api/auth/logout` | 无请求体 | 无data，成功code/message |

`expiresIn`当前直接返回配置的有效期，**单位为毫秒**，不是秒。nickname来自账号realName。示例请求只用占位值，不在文档放真实账号或令牌：

```json
{
  "username": "example-user",
  "password": "example-password"
}
```

登录凭据错误通常HTTP 401；refresh无效或用户不存在400。当前refresh没有完成禁用账号检查/轮换机制，logout只清除本请求SecurityContext，没有服务器端撤销已发JWT；客户端仍应清理本地令牌。user-info未完整统一未登录处理，不能承诺其所有未登录情况都返回401。

### 9.2 账号管理

这部分列出现有接口便于区分；它不属于四个清洗业务服务的新增设计。

| 方法/地址 | 输入 | 成功data |
|---|---|---|
| `GET /api/admin/user/page` | Query：page=1、size=10、keyword可选（用户名/姓名） | `{total,page,pageSize,records:User[]}` |
| `GET /api/admin/user/{id}` | Long路径ID | User；当前不存在仍成功但无data，不是404 |
| `POST /api/admin/user` | JSON User，创建使用username/password及实际资料 | 无data |
| `PUT /api/admin/user/{id}` | JSON User，路径ID覆盖body ID | 无data；此路径不修改密码 |
| `DELETE /api/admin/user/{id}` | 路径ID | 无data，逻辑删除 |
| `PUT /api/admin/user/{id}/password` | JSON：`oldPassword:string、newPassword:string` | 无data；用户不存在/旧密码错误400 |

User对外资料字段：`id:number、username:string、realName?:string、phone?:string、email?:string、avatar?:string、status?:integer、roleCode?:string、createTime?:string、updateTime?:string、deleted?:integer`。账号时间目前为LocalDateTime字符串，不自动套用清洗Instant的带时区契约。分页/按ID查询代码会清空password，响应不输出密码或密码哈希。

创建时实际需要提供`username`和明文`password`，服务端加密后保存；可编辑资料为realName、phone、email、avatar、status、roleCode。status约定0禁用/1启用，角色约定ADMIN/OPERATOR/VIEWER，标准新库默认启用和VIEWER。id、创建/更新时间、deleted由服务端管理，前端不用于创建输入；当前实体直收尚未形成完整字段校验，不能承诺所有缺失/非法值都规范返回400。

当前安全配置仅对`/api/cleaning/**`强制认证，其他路径fallback为permitAll，UserController/Service也没有完整管理员权限检查。因此这些路由“存在”不代表账号管理权限已经闭环，不将其当成已验收的全站管理接口。本轮不更改账号代码。

## 10. 实施与对接边界

现有设备查询校验deviceId格式`[A-Za-z0-9._-]{1,64}`，合法但不存在的ID通常得到无快照/空PROCESS统计/未同步参数，不能统一承诺404。读取使用`device:read`、写入使用`device:write`；当前没有逐设备授权名单。新历史资源和导出应校验实际设备关系及任务归属，补齐对应实现后再开放。

新接口中的state、completionKind、motionRole、buildState等为后端业务契约，**不是给未知D200/D202编PLC枚举**。无来源的起止、启动方式、环体高度、工时、限位和处理次数继续缺省。记录详情、页面统计及文件必须复用同一业务口径。

目标数据库仍为10业务表＋1导出任务表。本文部分响应值为派生值，例如completedUnclassifiedCount和所选来源并集数；停止/未知方向事件计数需要在统计实现中明确累计，可按设计补统计字段，但本轮未改DDL。现有7表不因接口文档而改变。

前端附件旧Cloud/box路径、RUN/CYCLE混用、整包原始曲线和前端分页导出均需适配本契约。每项待实现接口完成后再补控制器、DTO/VO、权限和对应页面联调，不先放返回假数据的占位接口。

代码依据：[CleaningController](../src/main/java/com/company/project/controller/CleaningController.java)、[Web转换与返回](../src/main/java/com/company/project/presentation/web/CleaningWebPresentation.java)、[实时VO](../src/main/java/com/company/project/vo/cleaning/CleaningRealtimeVo.java)、[SSE](../src/main/java/com/company/project/presentation/web/RealtimePushPresentation.java)、[认证控制器](../src/main/java/com/company/project/controller/AuthController.java)、[安全配置](../src/main/java/com/company/project/config/SecurityConfig.java)。业务依据：[总览](business-flow-and-page-data-overview.md)、[数据库设计](database/page-driven-database-design.md)、[导出方案](data-export-plan.md)。
