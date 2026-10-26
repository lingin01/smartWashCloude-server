> 用户附件留档，2026-10-26。文内“当前前端”来自附件报告，不代表本仓库持有或验证了前端源码；原环境路径保留为文字。当前 Java 实现见[Controller 接入状态](../page-controller-implementation.md)。

# 页面接口与拉力图筛选总结

核对日期：2026-10-26。依据当前前端源码和后端目标接口文档。

本文区分两件事：**前端现在实际发送的请求**，以及**生产接入还需补齐的契约**。当前后端Java仍主要提供旧`/api/cleaning/devices/{deviceId}`入口，不能把下面的新版页面地址当作已经完成联调。2026-10-26后续已修复第7节归档日期筛选的前端问题；其他生产接入差异仍按各节说明。

## 1. 先区分两个拉力对比图

| 项目 | 单台设备详情 | 一次运行记录详情 |
|---|---|---|
| 查询对象 | 一台设备，在本日多次或指定统计周期之间比较 | 一次已选定的系统运行，在四台设备及不同拉力来源之间比较 |
| 定位参数 | `systemCode + winchCode` | `systemCode + recordCode` |
| 主接口 | `/api/command-center/winch/tension/trend` | `/api/command-center/history/detail` |
| 范围选择 | 本日次数、本日平均、本月平均、本年平均 | 已由记录编号锁定，不再选择本日/月/年平均 |
| 设备选择 | 当前设备固定 | 动力一号～四号，可多选 |
| 来源选择 | 钢绳1、钢绳2、伺服电机、钢绳总拉力 | 钢绳1、钢绳2、伺服电机，没有总拉力入口 |
| 曲线分组 | 一次运行或一个统计周期一组 | 一台设备的一个拉力来源一条，通常最多12条 |
| 横轴/纵轴 | 高度m / 拉力t或kg | 高度m / 拉力t或kg |

不是每点一次筛选都要请求后端。**改变查询范围才需要查新数据；在已取得的完整数据内选择曲线、切换吨/公斤，可以前端完成。**“本日次数”并不表示只返回一个次数数字，而是返回当日各次运行的曲线分组。

## 2. 单台设备详情：进入页面先查什么

```http
GET /api/command-center/winch/detail?systemCode=QJXT-01&winchCode=WJ-01
Authorization: Bearer <token>
```

| 返回数据 | 用途与来源 |
|---|---|
| `winch`、本机两路`sensors`、`currentHeight`、`torqueForce`、`updatedAt` | 详情当前快照，来自采集/后端换算，不是历史曲线最后一点 |
| `currentMode`、`setSpeedMPerMinute` | 实际模式、已经确认生效的设定速度；设定速度不能拿实际速度替代 |
| `todayCompletedCount`、`runningHours` | 后端按明确口径统计，不能由页面可见曲线数量代替 |
| `maintenanceAt`、阈值及版本信息 | 档案、配置和实际生效反馈 |

这个接口只负责上方设备详情。详情请求成功后，页面另调下面的趋势接口。当前详情页没有定时轮询图表；切设备、进入页面或重新加载时查询。

## 3. 单台拉力对比图：筛选条件如何发送

### 3.1 当前前端实际发送的四个参数

```http
GET /api/command-center/winch/tension/trend?systemCode=QJXT-01&winchCode=WJ-01&viewType=FORCE_COMPARISON&comparisonView=COUNT
```

| 参数 | 示例 | 含义 |
|---|---|---|
| `systemCode` | `QJXT-01` | 当前实际系统公开编号 |
| `winchCode` | `WJ-01` | 当前设备公开编号，不是“动力一号”显示文字 |
| `viewType` | `FORCE_COMPARISON` | 固定值，标明新拉力对比口径 |
| `comparisonView` | `COUNT / DAY / MONTH / YEAR` | 当前选中的范围按钮 |

GET参数放Query，不发JSON请求体。编号、日期和数值均为示例，实际请求取当前系统/设备上下文，不能把示例编号写死。

### 3.2 四个范围按钮分别要返回什么

| 页面按钮 | `comparisonView` | `kind` | 后端返回的分组 |
|---|---|---|---|
| 本日次数 | `COUNT` | `BATCH` | 当日已存在的生产上行记录，各次一组；包含有事实依据的进行中记录 |
| 本日平均 | `DAY` | `PERIOD` | `TODAY`今日、`YESTERDAY`昨日、`MONTH`本月、`YEAR`本年 |
| 本月平均 | `MONTH` | `PERIOD` | `MONTH`本月、`PREVIOUS_MONTH`上月、`PREVIOUS_YEAR`上一年 |
| 本年平均 | `YEAR` | `PERIOD` | `YEAR`本年、`PREVIOUS_YEAR`上一年 |

“本日平均”是预设对比组合，不是把月份日期当作横轴；横轴仍是高度。`PREVIOUS_YEAR`在这里指上一年平均分组，不应擅自当成另一接口里的“去年同期”。

日期由后端按业务日历Asia/Shanghai和实际查询截止时间确定，返回`asOf/dataRange/units[].startAt/endAt/dataThrough`说明范围。当前图表没有任意日期筛选器，因此**现在不要额外要求这个请求提交自选起止日期**。

正式响应应保留各个约定分组；没有去年数据时返回该分组`empty=true`及原因、无有效点，不造0线。当前前端正式响应校验会检查这些周期分组；开发演示省略去年分组不代表正式接口也可以省略。

### 3.3 哪些筛选会请求后端

| 操作 | 当前是否重新请求 | 原因 |
|---|---|---|
| 切换本日次数/本日平均/本月平均/本年平均 | 是 | 修改`comparisonView`，需要另一套运行/周期数据 |
| 切换到另一台设备 | 是 | 修改`winchCode`，先详情再趋势 |
| 在已加载的运行列表中选择某次或全部次数 | 否 | 当前按`unitId`本地过滤，不会发送`selectedRunId` |
| 勾选钢绳1、钢绳2、电机、总拉力 | 否 | 当前从已返回的来源中筛选；不是每选一个就重新查全部数据 |
| 切换吨/公斤、隐藏/显示图例、移动鼠标 | 否 | 仅格式化与显示交互 |
| 完整点表继续读取下一批 | 当前未接，生产需要 | 使用`resolution/cursor`续读，不能把首批当全量 |

因此，按当前加载方式，后端应返回请求范围内可用的各来源，不要只返用户初始勾选的钢绳1，导致后来切换钢绳2没有数据。

### 3.4 返回结构：分组和曲线如何对应

统一包装：`{code:200,message:"查询成功",data:{...}}`。下表是当前页面核心消费结构：

| `data`字段 | 含义 |
|---|---|
| `kind` | `BATCH`或`PERIOD` |
| `units[]` | 分组目录：`id/name/startAt/endAt/rangeLabel/empty`；单次还需`runState` |
| `series[]` | 曲线集合，每条带`unitId`关联分组，带`sourceRole`标明来源 |
| `series[].points[]` | 每个点的`height/value/speed/time/direction/quality`；平均点还需真实样本数及桶范围等 |
| `heightMin/heightMax` | 实际返回数据的高度范围，不等于设备机械限位 |
| `heightLimits` | 有明确来源时返回实际限位，不能固定写成0～30m |
| `dataRange/asOf/updatedAt` | 实际历史覆盖区间、查询/统计截止及内容时刻 |

当前三种实体来源为`SENSOR1/SENSOR2/SERVO`。总拉力是派生来源，生产目标为后端返回`ROPE_TOTAL`，不是第三个传感器。当前前端仍把它叫`SUM`并本地求和，**这部分需要适配，不能声称已经消费ROPE_TOTAL**。

下面只展示一组、一条曲线的核心字段，不代表正式接口只返回一条；时间、身份和读数为说明性示例：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "kind": "BATCH",
    "units": [
      {
        "id": "RUN-20261026-001",
        "name": "第1次运行",
        "startAt": "2026-10-26T08:30:00+08:00",
        "endAt": "2026-10-26T08:33:00+08:00",
        "runState": "COMPLETED",
        "empty": false
      }
    ],
    "series": [
      {
        "unitId": "RUN-20261026-001",
        "sourceRole": "SENSOR1",
        "metric": "WINCH_TENSION",
        "name": "钢绳1-1拉力",
        "sourceSensorCode": "PS-01",
        "sourceSensorName": "钢绳1-1",
        "periodRole": "CURRENT",
        "points": [
          {
            "time": "2026-10-26T08:30:03+08:00",
            "height": 0.52,
            "value": 0.88,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD",
            "sampleCount": 1
          },
          {
            "time": "2026-10-26T08:30:06+08:00",
            "height": 1.02,
            "value": 0.91,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD",
            "sampleCount": 1
          }
        ]
      }
    ],
    "heightMin": 0.52,
    "heightMax": 1.02,
    "asOf": "2026-10-26T09:00:00+08:00",
    "updatedAt": "2026-10-26T08:33:00+08:00"
  }
}
```

上例仅说明字段关联，实际响应应返回整个请求范围的数据及元信息。高度范围取整个查询结果，不能用当前鼠标选中的单点代替。

`unitId`连接“第几次/哪个周期”，`sourceRole`连接“哪种拉力”，`height`画横轴、`value`画纵轴。`runState=COMPLETED`只表示图表运行组已经结束，是否正常完成必须另看最终运行结果。

周期平均不能把一天/一月的原始时间点全部堆给前端：后端按明确高度桶聚合，返回真实`sampleCount`等依据；均值用sum/count合并。总拉力须同批两索先求和再聚合，不能直接相加两个样本集合不同的单索平均曲线。

## 4. 一次运行记录详情：筛选条件如何发送

### 4.1 当前实际请求

从系统历史列表取得`entry.id`，作为下一次请求的`recordCode`。这个编号定位一次系统生产上行，不是某台设备的一次随意拼接过程。

```http
GET /api/command-center/history/detail?systemCode=QJXT-01&recordCode=RUN-20261026-001&yearGrouping=MONTH
```

| 参数 | 含义 |
|---|---|
| `systemCode` | 当前系统公开编号 |
| `recordCode` | 点击的那条运行记录的公开编号，不能用“第1次”显示文字替代 |
| `yearGrouping=MONTH` | 当前公共调用函数带出的兼容参数；对单次RUN没有“按月平均”的含义 |

后端目标允许单次请求省略`yearGrouping`，或兼容`MONTH`。单次详情不需要发送日期范围来重新找一条记录，也不需要调用单机趋势的`comparisonView`。

### 4.2 页面按钮与请求的关系

| 页面筛选 | 当前行为 |
|---|---|
| 动力一号/二号/三号/四号 | 本地选择`groupNo`，可多选；不重新请求整次详情 |
| 钢绳1号/钢绳2号/伺服电机 | 本地选择`ROPE1/ROPE2/SERVO`；可多选，不重新请求 |
| 吨/公斤 | 本地单位格式化 |
| 切到另一条运行记录 | 修改`recordCode`，重新请求 |
| 完整点表续读 | 当前未接，生产需要`resolution=RAW + cursor` |

当前初始选择动力一号＋钢绳1号。全选四台设备和三种来源时，通常显示8条钢绳＋4条电机曲线，共12条。这里没有“钢绳总拉力”筛选入口，也不需要为了图表返回一套必需的总力曲线或旧统计双表。

### 4.3 后端需要返回什么

| `data`字段 | 内容 |
|---|---|
| `entry` | 本次系统运行概要：公开ID、`kind=RUN`、起止时间、最终结果、启动方式、当次设定速度/运行间隔、时长、异常次数、单绳极值等 |
| `sensors[]` | 正常配置下8条钢绳曲线，每条明确`groupNo`、`ropeNo=1/2`、名称和点数组 |
| `servos[]` | 正常配置下4条独立电机拉力曲线，每条明确所属设备 |
| `heightMin/heightMax` | 本次数据高度范围 |
| `heightLimits` | 有事实来源时返回实际限位 |
| 生产续读扩展 | `resolution/complete/nextCursor/loadedPointCount`以及分段信息 |

**缺数据的配置曲线要有明确空态；不能编造8＋4条有效读数。**设备分组和钢绳序号要由后端明确提供，不能让前端按数组位置猜对应关系。

核心结构示意如下，仅展示一台设备的两条来源及部分运行头字段：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "entry": {
      "id": "RUN-20261026-001",
      "kind": "RUN",
      "startedAt": "2026-10-26T08:30:00+08:00",
      "endedAt": "2026-10-26T08:33:00+08:00",
      "runResult": "COMPLETED",
      "statusText": "正常完成",
      "startMethod": "SCHEDULED",
      "setSpeedMPerMinute": 10,
      "intervalMinutes": 60,
      "durationSeconds": 180,
      "abnormalCount": 0,
      "maxTension": 1.05,
      "minTension": 0.81
    },
    "sensors": [
      {
        "id": "PS-01",
        "name": "动力一号·钢绳1",
        "groupNo": 1,
        "ropeNo": 1,
        "displayMin": 0,
        "displayMax": 1.4,
        "points": [
          {
            "time": "2026-10-26T08:30:03+08:00",
            "height": 0.52,
            "value": 0.88,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD"
          }
        ]
      }
    ],
    "servos": [
      {
        "id": "SERVO-WJ-01",
        "name": "动力一号·伺服电机拉力",
        "groupNo": 1,
        "displayMin": 0,
        "displayMax": 3,
        "points": [
          {
            "time": "2026-10-26T08:30:03+08:00",
            "height": 0.52,
            "value": 1.82,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD"
          }
        ]
      }
    ],
    "heightMin": 0.52,
    "heightMax": 26.8
  }
}
```

同一点的`speed`可以直接供图表右侧逐点表展示。运行头的`entry.maxTension/minTension`来自本次生产上行全系统8根单索的有效样本极值，不随下方只选择动力一号而改变，也不能从抽样后的可见曲线重新求。

当前单次详情并没有调用一个独立的“拉力对比接口”：`history/detail`同时提供概要和本次曲线。后续可以在同一路径增加局部曲线条件来减少传输，但必须明确参数契约；当前页面的设备/来源选择还没有发到后端，不能当作已存在的服务端筛选。

## 5. 两个图的生产续读与逐点表

当前页面接收平铺`points[]`，没有完成后端目标的分段/游标协议。生产建议沿现有目标路径补齐：

```http
GET /api/command-center/winch/tension/trend?systemCode=QJXT-01&winchCode=WJ-01&viewType=FORCE_COMPARISON&comparisonView=COUNT&resolution=RAW&unitPageSize=5&maxPointsPerSeries=500

GET /api/command-center/history/detail?systemCode=QJXT-01&recordCode=RUN-20261026-001&resolution=RAW&maxPointsPerSeries=500
```

后端返回`complete=false/nextCursor`时，保持原筛选条件，用返回的cursor继续；前端按分组、来源、分段和稳定点键合并，直到`complete=true`。不能只加载第一批却显示“本次全部点”。

- `OVERVIEW`：有界概览，必须说明缩减方式，不能用于冒充完整逐点表。
- `RAW`：单次为实际已保存的点；周期为完整基础高度桶，仍不是所有原始帧。
- 每点补`sampleKey/sampleFrameId/sequence`；分段补`segmentCode/motionRole/gapBefore`。有回退/恢复时保留实际过程和重复高度，不能按高度去重、按时间邻近配对，或跨缺口连线。
- 平均桶没有真实现场帧，`time/sampleFrameId`不适用时按目标返回null；当前前端`time:string`等类型也要同步适配。
- 后端提供速度、身份、来源和真实空值。前端负责选曲线、单位格式化、坐标与样式，不生成业务测量。
- 保存策略是每台独立0.5m高度点及必要边界。正常下降没有连续归档时，不能因为示例曲线画了下降就要求后端补出完整下降历史。

后端目标对`COUNT`一次默认返回5个运行组、每序列默认500点，都是一次响应预算，不是只允许保存5次或500点。

## 6. 其他页面接口总览

下表为页面对接目录，不表示所有后端方法已实现；写入的请求编号/版本、SSE、游标与后台导出等属于生产接入待补项。

| 页面/功能 | 方法与路径 | 发送内容 | 返回核心数据 |
|---|---|---|---|
| 登录 | `POST /api/auth/login` | `username,password` | token、refreshToken、有效期及单位 |
| 用户上下文 | `GET /api/auth/user-info` | 登录令牌 | 用户、角色、权限、授权系统；实际systemCode来源 |
| 首页初始数据 | `GET /api/command-center/home` | `systemCode` | 系统状态、当前高度、八路传感器、四台设备、实时均值与时间 |
| 首页实时更新（目标） | `GET /api/command-center/home/stream` | `systemCode`＋鉴权 | 完整首页快照SSE；当前前端生产订阅还需接入 |
| 单机详情 | `GET /api/command-center/winch/detail` | `systemCode,winchCode` | 设备当前快照、配置、统计；见第2节 |
| 单机拉力对比 | `GET /api/command-center/winch/tension/trend` | 四个主参数＋目标续读参数 | 运行/周期分组、来源曲线；见第3节 |
| 系统历史列表 | `GET /api/command-center/history/list` | `systemCode,scope=RUN,startTime,endTime,endExclusive=true,page,pageSize`；当前还带`includeAvailableDates=true` | entries、total、页码及实际范围；日期目录大时另做有界续读 |
| 一次运行记录详情 | `GET /api/command-center/history/detail` | `systemCode,recordCode`＋当前兼容`yearGrouping=MONTH` | entry、八索/四机曲线；见第4节 |
| 最新告警 | `GET /api/command-center/alarms/latest` | `systemCode`，可选`deviceCode` | recent、hasAttention、实际窗口和完整性 |
| 历史告警 | `GET /api/command-center/alarms/history` | systemCode、设备、range、levels、日期、page/pageSize | list、total、historyRange、查询条件回显 |
| 参数读取 | `GET /api/command-center/parameter-settings` | `systemCode` | 三参数及目标saved/applied版本和状态 |
| 参数保存 | `PUT /api/command-center/parameter-settings` | systemCode、startTime、intervalMinutes、speedMPerMinute；生产需requestId、expectedVersion | 保存/下发阶段与已确认生效值，不把受理当生效 |
| 单机阈值保存 | `PUT /api/command-center/winch/tension-thresholds` | systemCode、winchCode、两项`{sensorCode,alarmThreshold,safetyValue}`；生产需requestId、expectedVersion | PENDING/APPLIED、版本和真实effectiveThresholds |
| 急停 | `POST /api/command-center/emergency-stop` | systemCode；生产需requestId | 受理与命令编号；实际执行确认另读命令结果 |
| 只读用户管理 | `GET /api/users` | keyword、page、pageSize | list、total、角色及最近登录事实 |
| 数据归档 | `GET /api/command-center/archive` | 系统/设备、指标、周期、比较方式、记录或日期范围 | 左右图、各自范围、汇总、可比性及原因 |
| 后台导出（目标） | `POST /api/command-center/exports` | systemCode、exportType、unit、filters、requestId | taskCode及处理状态；再查任务、下载已发布文件 |
| 视频、模型、巡检、新分析模块 | 本轮没有已实现的统一生产接口 | 另定来源与范围 | 当前视频是本地示例，其他部分待开放，不能当作已接通 |

历史列表的时间边界是`startTime <= startedAt < endTime`；告警CUSTOM输入日期包含首尾，后端再转换右开。两者不能机械共用同一个endDate解释。

## 7. 归档日期筛选：前端问题已修复

原问题属于**前端请求拼装错误**：界面有左图变化区间、右图当前区间、右图参考区间，但旧请求的`compareStart/compareEnd`混用了两个区间，漏掉右图当前区间的终点和参考区间的起点。演示数据中右图当前曲线还直接复用了左图数据，导致两图筛选不独立。

现在页面和API层已改为使用后端目标契约中的六个明确边界，停止发送旧别名：

- `changeStartDate/changeEndDate`：左图。
- `currentStartDate/currentEndDate`：右图当前区间。
- `referenceStartDate/referenceEndDate`：右图参考区间。

共同条件为`systemCode`、`winchCode`、`metric=TENSION/SPEED`、`period=WEEK/MONTH/QUARTER/YEAR`、`comparisonType`。`tensionMetric`仅拉力时发送。

| 比较方式 | 实际发送的范围/记录条件 |
|---|---|
| SELECTED_PERIODS：自选区间 | 三组日期共六个边界；不带recordCode |
| PREVIOUS_PERIOD / YEAR_ON_YEAR：环比/同比 | 左图和右图当前区间共四个边界；不带隐藏的参考日期和recordCode，参考范围由后端生成 |
| PREVIOUS_RUN / HISTORY_AVERAGE：运行记录比较 | 左图两个边界＋recordCode；右图按记录比较，不带日期框残留值 |

日期框选中的首日、末日都包含在查询内；请求统一转为左闭右开。例如界面选`2026-10-01～2026-10-07`，发送`startDate=2026-10-01,endDate=2026-10-08`，后端读取`开始日零点 <= 时间 < 结束日零点`，使用Asia/Shanghai业务时区，避免漏掉10月7日。此转换在页面构造请求时只做一次，API层不再重复增加一天。

清空、倒置或无效日期会显示提示并阻止请求；切换统计周期只改变分组，不重置手动日期。演示模式中左图、右图当前及参考曲线分别按各自范围取数，环比参考日期按所选周期推算；生产参考边界仍由后端生成并回显。

修复位置：页面筛选（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/views/data-archive/index.vue`）、日期与参数校验（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/utils/archiveQuery.ts`）、归档API与演示取数（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/api/commandCenterPages.ts`）。本次修复不代表新版Java归档接口已实现或完成真实数据联调，后端仍需按上述字段接收、查询并返回实际范围。

## 8. 对接时要共同确认的四件事

1. **读数与业务结果由谁生成**：实时从PLC快照来，历史从已保存事实来，周期平均和同批总力由后端算；前端不根据显示图重算业务结论。
2. **筛选是否已传输**：两个重点图的来源/设备按钮大多是本地选择；未实现的服务端局部筛选不能只在接口文档中加名字而不改前端请求。
3. **返回是否完整**：分辨率、实际范围、已读点数、游标和完成标记要明确；首批/概览不冒充全量或可导出的完整事实。
4. **来源代码保持各自契约**：单机图为SENSOR1/SENSOR2/ROPE_TOTAL/SERVO目标口径；单次系统图为ROPE1/ROPE2/SERVO。首页固定“动力一号”等名称不替代请求里的真实公开编号。

源码与目标文档依据：

- 单机页面筛选与加载（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/views/power-detail/index.vue:88`）
- 单机趋势实际请求（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/api/commandCenterPages.ts:372`）
- 单次详情实际请求（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/api/systemHistory.ts:109`）
- 单次详情设备/来源选择（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/views/history-detail/index.vue:95`）
- 归档实际请求（原环境路径：`D:/javaweb/all/SmartWashCloud-Web/src/views/data-archive/index.vue:93`）
- 后端目标：单机趋势（原环境路径：`D:/javaweb/all/SmartWashCloud-Server/docs/external-api-contract.md:145`）
- 后端目标：单次详情（原环境路径：`D:/javaweb/all/SmartWashCloud-Server/docs/external-api-contract.md:226`）

部分旧前端说明仍使用tankCode、旧默认选择或旧方向平均规则，与当前源码不完全一致。本文的“当前请求”按源码核实，“生产目标”按后端目标文档标注，没有把两者混成已经实现的接口。
