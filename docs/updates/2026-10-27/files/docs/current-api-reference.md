# 当前已实现接口参考

> 2026-10-27实施更新：当前代码已接通M106整轮、实时换算、19表保存、历史/五周期统计、严重告警、配置保存和导出。参见[当前实施与接口补充](updates/2026-10-27/backend-implementation.md)。下文原有“待实现/17表/10个已接通”等标注保留为此前设计或核对基线，不能作为当前完成度；参数PUT当前只保存平台版本，现场生效须有PLC依据。

核对日期：2026-10-26。新页面26个目标方法的路由已实现，逐项业务可用状态见[Controller实现与接入状态](page-controller-implementation.md)。本文第1节保留旧设备接口真实行为；账号接口已按新版适配。最终页面契约见[对外接口说明](external-api-contract.md)。

当前清洗前缀记为`B = /api/cleaning/devices/{deviceId}`。接口使用Bearer访问令牌；GET无请求体，POST/PUT使用JSON。Result为`{code,message,data}`，data=null也输出；旧VO仍沿用NON_NULL而新页面VO显式输出可空字段。安全过滤器401现返回JSON及data.errorCode。当前测量单位kg、m、m/min，Instant时间通常序列化为UTC的Z。

当前deviceId格式为`[A-Za-z0-9._-]{1,64}`。读取权限device:read，写入权限device:write；实际账号需启用，但尚无逐设备授权名单。格式合法的未知deviceId通常得到无实时快照、空PROCESS统计或未同步参数，而非统一404。

## 1. 现有清洗接口：以代码行为为准

### 1.1 GET B/realtime

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

当前五态名称为`STANDBY/RUNNING/MAINTENANCE/FAULT/OFFLINE`。`tensionAverage`是本机索均值，不是两索总拉力。明确有效性为false的通道不参与合计和默认统计。当前转换器尚未按新版D201/D203～D206完全适配；不能因字段存在就声称新点位已经接通。目标页面字段见[页面接口契约](external-api-contract.md)。

### 1.2 GET B/realtime/stream

输入同实时查询，另使用`Accept: text/event-stream`，仍需Bearer访问令牌。使用支持Authorization请求头的SSE客户端或fetch流。

```text
event: realtime
id: 123
data: {"code":200,"message":"success","data":{"deviceId":"plc-01","available":true,"message":"已取得设备实时数据","data":{"sequence":123}}}

:keepalive
```

上例仅缩略展示测量部分；实际`data:`内容与GET realtime完整包装一致。首次发送当前视图，无快照时无id；同序号发送保活注释。默认每1000ms检查新快照，不承诺推送每个PLC采样。

当前最多64个连接，每连接最长60秒，结束后客户端重新鉴权连接；持续检查账号/权限和令牌期限。没有实现Last-Event-ID历史重放、补发或业务error事件，连接结束应按HTTP/重连结果处理。断流不自动等同PLC离线。

### 1.3 GET B/statistics

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

当前metric为`ropeTension / winchSpeed / winchHeight / winchTorque / ringHeight`，分别按索、动力设备或设备标识关联。`acceptedSamples`是进程接受的帧数，包括部分/全部无效或离线帧；各指标的有效数量看`metrics[].validSamples`。指标均值/极值只用有效测量，重启清空，不区分已识别生产上行，也不表示数据库已归档。后续页面周期查询见[页面接口契约](external-api-contract.md)，不改变本接口PROCESS含义。

### 1.4 POST B/emergency-stop

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

返回公共Command对象，HTTP映射见第1.7节。设备写入尚未确认的示例：

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

### 1.5 GET B/settings 与 PUT B/settings

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

### 1.6 GET B/commands/{requestId}

输入：路径设备与原请求编号，无请求体。找到返回HTTP 200和Command；不存在或属于另一设备返回404。命令存于当前进程，重启后不能当作持久操作审计查询。

| Command字段 | 类型 | 含义 |
|---|---|---|
| `requestId / commandId / deviceId` | string | 客户请求编号、后端命令编号、设备 |
| `type` | string | `emergency_stop`或`update_cleaning_parameters` |
| `state` | string | `CREATED/QUEUED/SUCCEEDED/REJECTED/FAILED/UNCONFIRMED/BLOCKED` |
| `code / message` | string | 具体业务原因与说明，需结合state解释 |
| `createdAt / expiresAt / updatedAt` | time | 命令时间，不是采集数据有效期限 |

当前同requestId且设备、操作人、类型或参数不同会400；相同请求在登记仍保留时返回原状态。登记为进程内数据，终态/未确认条目还可能被清理，不承诺跨重启或永久幂等；不可盲目重试物理操作。结果不确定时核对现场，不自动重发或回滚。

UNCONFIRMED不是终态，后续仍可能收到可关联结果并推进状态；页面不能直接把它改成FAILED或“已完成”。参数未持久化的未确认原因仍按第1.5节显示。

### 1.7 当前HTTP与业务状态

| 情况 | 当前HTTP及包装行为 |
|---|---|
| 普通GET成功、找到命令 | 200，Result.code=200；命令内部仍可能FAILED/REJECTED |
| POST/PUT新入队等非终态 | 202，Result.code=202 |
| POST/PUT的BLOCKED | 503，data仍包含Command |
| POST/PUT的SEND_QUEUE_FULL | 429，data仍包含Command |
| POST/PUT其他终态或UNCONFIRMED | 200；版本冲突、已有在途参数可能是200＋REJECTED，不是409 |
| 必要参数规则触发IllegalArgumentException | 400；同requestId内容冲突也在这里 |
| 未登录/无效访问令牌 | 401；安全过滤器返回JSON及data.errorCode=UNAUTHENTICATED |
| 当前账号无权限 | 通常403；进入业务异常处理时为Result错误 |
| 未找到命令/未实现路由 | 404 |
| 未分类错误 | 500；当前坏JSON/缺请求体没有统一400处理，不承诺所有格式错误已规范化 |

前端先处理HTTP失败及可能空响应体，再读取Result和业务状态。不要把HTTP 200、Result.code=200或`available=true`分别当成操作成功、设备运行正常或采集完整。

## 2. 认证与账号接口现状

### 2.1 认证和只读用户页

| 方法/地址 | 输入 | 当前成功data |
|---|---|---|
| POST `/api/auth/login` | username、password | token、refreshToken、expiresIn、expiresInUnit=SECONDS |
| POST `/api/auth/refresh` | refreshToken | 新token、refreshToken=null、expiresIn、expiresInUnit=SECONDS |
| GET `/api/auth/user-info` | Bearer访问令牌 | id为string、资料、roles/roleNames、permissions、授权systems和currentSystemCode |
| POST `/api/auth/logout` | Bearer访问令牌 | null；message说明清除本地令牌及当前退出范围 |
| GET `/api/users` | keyword、page=1、pageSize=10（最大100） | list、total、page、pageSize、asOf；须user:read |

login和refresh的expiresIn均为秒。刷新凭据无效401，账号缺失401、禁用403；没有实施刷新凭据轮换，返回null表示保留原有效刷新凭据。logout只清除本次认证上下文并提示客户端清理令牌，已签发JWT到期前仍有效，不宣称已服务器端撤销。

用户上下文和只读列表检查实际启用账号及当前权限。授权系统来自明确的外部页面映射，未配置时systems=[]，不生成示例系统。用户列表不返回密码；缺少登录历史时lastLoginAt=null且lastLoginState=UNKNOWN。用户创建时间按业务时区Asia/Shanghai输出，旧库原时间语义须在迁移时核实。

### 2.2 账号管理

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

新页面、认证私有入口、只读用户列表和旧清洗入口均已强制认证；旧`/api/admin/user/**`仍在fallback范围，UserController/Service没有完整管理员权限检查。因此这些路由“存在”不代表账号管理权限已经闭环，不将其当成已验收的全站管理接口。本轮新增只读页面入口与认证适配，没有完成旧管理员CRUD的授权迁移。
