# 当前已实现接口参考

核对日期：2026-10-24。本文单独保留当前Java实现的真实行为。新版页面目标以[对外接口说明](external-api-contract.md)和[前后端修订对齐说明](frontend-backend-contract-alignment.md)为准；本轮仅改文档，当前Controller、VO、序列化和权限配置尚未迁移。

2026-10-25再次核对：当前仍为7个清洗、4个认证和6个管理员方法。新页面Controller清单见[实施说明](controller-api-specification.md)。本页login的expiresIn仍按实际代码记毫秒、旧写回执body.code仍随HTTP；新契约要求秒及成功业务code=200，两者是待实施差异，不是本轮已修改。

当前清洗前缀记为`B = /api/cleaning/devices/{deviceId}`。接口使用Bearer访问令牌；GET无请求体，POST/PUT使用JSON。当前Result为`{code,message,data?}`，成功通常200；Jackson配置NON_NULL，可空字段通常省略。安全过滤器拒绝的401可没有JSON响应体。当前测量单位kg、m、m/min，Instant时间通常序列化为UTC的Z。

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
| 未登录/无效访问令牌 | 401；安全过滤器可返回空响应体，不保证Result包装 |
| 当前账号无权限 | 通常403；进入业务异常处理时为Result错误 |
| 未找到命令/未实现路由 | 404 |
| 未分类错误 | 500；当前坏JSON/缺请求体没有统一400处理，不承诺所有格式错误已规范化 |

前端先处理HTTP失败及可能空响应体，再读取Result和业务状态。不要把HTTP 200、Result.code=200或`available=true`分别当成操作成功、设备运行正常或采集完整。

## 2. 认证与账号接口现状

### 2.1 认证

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

当前安全配置仅对`/api/cleaning/**`强制认证，其他路径fallback为permitAll，UserController/Service也没有完整管理员权限检查。因此这些路由“存在”不代表账号管理权限已经闭环，不将其当成已验收的全站管理接口。本轮不更改账号代码。
