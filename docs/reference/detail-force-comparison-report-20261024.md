# 分解槽抑制结疤智能体平台 当前项目总结与详情拉力对比图数据处理说明

整理日期：2026-10-24。

**当前已完成的是字段和旧内容清理；详情页拉力对比图中的总拉力、右侧均值和极值，仍有前端业务计算，尚未完成后端迁移。** 原有模拟曲线保持一致，只能证明展示数据没有变化，不能证明计算职责已经调整。

本文结合当前代码和提供的截图，说明每个区域的数据含义、现有处理方式，以及落实“后端计算、前端展示”还需要补充的接口和代码调整。本次整理说明文档，不改变模拟数据或图表效果。

## 目录

- [一 当前项目已经完成的调整](#一-当前项目已经完成的调整)
- [二 截图中的数据分别表示什么](#二-截图中的数据分别表示什么)
- [三 拉力对比接口与四种查看范围](#三-拉力对比接口与四种查看范围)
- [四 当前代码实际怎样处理数据](#四-当前代码实际怎样处理数据)
- [五 当前仍需解决的问题](#五-当前仍需解决的问题)
- [六 前后端应承担的职责](#六-前后端应承担的职责)
- [七 建议的后端返回字段](#七-建议的后端返回字段)
- [八 截图对应的真实数据与响应示例](#八-截图对应的真实数据与响应示例)
- [九 下一步代码调整与验收](#九-下一步代码调整与验收)

## 一 当前项目已经完成的调整

### 1.1 字段与旧内容清理结果

| 项目 | 当前结果 |
| --- | --- |
| 系统查询字段 | 已将系统含义的 `tankCode` 统一为 `systemCode`；当前模拟系统为 `QJXT-01` |
| 首页系统字段 | 已使用 `systemName`、`systemHeight`、`systemCurrentHeight`、`systemStatusText` |
| 状态字段 | 系统与设备支持待机、运行、检修、故障、离线；删除重复旧状态和旧兼容推断 |
| 传感器状态 | 首页和详情直接展示接口的 `status`、`statusText`，不在页面比较预警/保护阈值 |
| 详情基本字段 | 删除 `ratedLoad`、`ratedSpeed` 等未展示旧字段；`setSpeedMPerMinute` 改为必返 |
| 旧接口和旧结构 | 清理未使用的旧趋势、独立运行对比、独立伺服趋势和旧历史接口及相关类型 |
| 详情刷新 | 已取消每5秒刷新；进入、主动刷新、失败重试或切换查看范围时请求 |
| 首页告警跳转 | 已取消设备故障/离线状态、异常测点到告警页的跳转；保留设备详情入口及独立告警中心 |
| 模拟数据 | 四台设备的16组对比曲线、历史运行源及4台详情数值做过前后比对，数值保持一致 |
| 上轮验证 | 类型检查、生产构建及255项测试通过；该结果不代表后端图表计算已实现 |

系统仍按“一个系统、四台动力设备、每台两个传感器”组织。视频预览不纳入本次说明；历史巡检、数据分析仍是开发中占位页，模型管理仍是待开放展示。

### 1.2 当前页面接口总览

下表是当前前端生产分支声明的15种HTTP方法与路径组合，不代表后端已经实现或联调完成。普通响应采用 `code/message/data`，成功业务码为200；地址相对于API基地址。

| 方法与路径 | 当前输入 | 主要返回内容 |
| --- | --- | --- |
| `POST /auth/login` | JSON：`username`、`password` | `token`、`refreshToken`、`expiresIn` |
| `GET /auth/user-info` | 登录认证上下文 | `id`、`username`、`nickname`、`avatar`、`email`、`phone`、`roles`、`permissions` |
| `POST /auth/logout` | 登录认证上下文 | 退出结果，数据为空 |
| `POST /auth/refresh` | JSON：`refreshToken` | 令牌刷新结果；当前拦截器只消费新token |
| `GET /command-center/home` | Query：`systemCode` | 系统身份、状态、高度、平均值、四台设备、八个测点及更新时间 |
| `POST /command-center/emergency-stop` | JSON：`systemCode` | 急停请求结果，当前未定义独立执行阶段数据 |
| `GET /command-center/winch/detail` | Query：`systemCode`、`winchCode` | 设备工况、两路测点、独立伺服、设定速度、完成次数、累计时长、维护时间 |
| `GET /command-center/winch/tension/trend` | Query：`systemCode`、`winchCode`、`viewType`、`comparisonView` | 当前为分组及三来源曲线；总拉力和统计尚需扩展，见后文 |
| `GET /command-center/alarm-dashboard` | Query：`systemCode`、`range`、可选`deviceCode`，CUSTOM时带日期 | 告警数量、`recent`、`history`、统计日与查询范围等；当前分类/测点过滤及分页仍在页面 |
| `GET /command-center/archive` | Query：`systemCode`、设备、指标、周期、比较方式、运行编号及日期条件 | `changePoints`、`comparisonCurrent`、`comparisonPrevious`、`summary`等 |
| `GET /command-center/history/list` | Query：`systemCode`、`scope=RUN`、`startTime`、`endTime`、右开边界与分页等 | `entries`、`total`、`availableDates`、`asOf`、实际数据范围 |
| `GET /command-center/history/detail` | Query：`systemCode`、`recordCode`、`yearGrouping` | `entry`、`sensors`、`servos`、高度范围及兼容统计字段 |
| `GET /command-center/parameter-settings` | Query：`systemCode` | `startTime`、`intervalMinutes`、`speedMPerMinute` |
| `PUT /command-center/parameter-settings` | JSON：`systemCode`和上述三项参数 | 保存后的参数快照 |
| `GET /users` | Query：`keyword`、`page`、`pageSize` | `list`、`total`、`page`、`pageSize`；行含账号、姓名、角色及登录/创建时间 |

可见列表默认每页10条，历史导出内部查询另用自己的分页大小。当前历史导出仍由浏览器生成CSV，没有现成的后端导出文件接口。模型管理、历史巡检、数据分析不额外定义本期接口。

### 1.3 首页与设备详情的当前核心字段

| 数据层 | 当前字段 |
| --- | --- |
| 首页系统 | `systemCode`、`systemName`、`systemStatus`、`systemStatusText`、`online`、`systemHeight`、`systemCurrentHeight`、`isMoving`、`movementDirection`、`averageTension`、`averageSpeed`、`updatedAt` |
| 动力设备 | `winchCode`、`winchName`、`groupNo`、`operatingStatus`、`statusText`、`direction`、`directionText`、`speed`、`sensorCodes` |
| 传感器 | `sensorCode`、`sensorName`、`metric`、`businessRole`、`groupNo`、`winchCode`、`position`、`pressure`、`unit`、`safetyValue`、`alarmThreshold`、`status`、`statusText` |
| 设备详情外层 | `systemCode`、`updatedAt`、`winch`、`sensors`、`torqueForce`、`runningHours`、`maintenanceAt`、`currentHeight`、`currentMode`、`setSpeedMPerMinute`、`todayCompletedCount` |
| 独立伺服对象 | `value`、`unit`、可选`sourceName` |

首页 `systemCurrentHeight` 是系统高度；详情 `currentHeight` 是详情快照字段，图表鼠标查看的高度是另外的历史采样上下文。传感器的 `pressure` 沿用当前字段名，实际含义为拉力，基础单位t。

首页生产代码目前仍是首次HTTP快照，尚未接入真实的每秒后端推送；开发演示继续使用原0.5秒节奏保持运动效果。此项与图表业务计算后移一样，不能因字段已经改好就写成真实后端能力已经完成。

**拉力图尚未完成的部分：** 后端总拉力序列、后端统计字段、统一采样关联键，以及页面对这些结果的直接消费。以下各节专门展开这些内容。

## 二 截图中的数据分别表示什么

### 2.1 左图和右侧两张表有不同范围

| 区域 | 截图内容 | 实际数据范围 |
| --- | --- | --- |
| 查看范围 | 本日次数 | 查询该日每次运行的曲线 |
| 运行记录选择 | 全部次数 | 左图叠加展示全部20次运行，不是计算20次的总平均 |
| 光标与悬浮提示 | 第9次运行，22.5m，12:22:15 | 选中第9次运行中的一个实际采样位置 |
| 右上表 | 该高度实际数据 | 第9次运行在该采样位置的各来源读数与速度 |
| 右下表 | 本次运行统计 | 第9次运行整条曲线的平均、最大和最小；不是只统计22.5m |
| 右侧钢绳整体最大/最小 | 0.66 / 0.37t | 第9次运行两条单绳曲线合在一起的极值，不含伺服和总拉力 |

“全部次数”是左图的显示范围；右侧查看的是鼠标当前命中的那一次运行。截图里选中第9次，因此右侧两张表都围绕第9次，不能把它们解释成全日20次的汇总。

尚未悬停时，当前代码优先选择返回集合中的运行中记录，否则取最后一条已完成记录，再回退到首个分组。此时上表读数为空，下表可以显示默认那一组的统计。

### 2.2 截图右上表的真实读数

已从当前模拟生成器重新读取同一个采样：动力一号设备、第9次运行、2026-10-22 12:22:15、高度22.5m、上行。

| 来源 | 原始拉力 t | 页面显示 t | 速度 m/min |
| --- | --- | --- | --- |
| 钢绳1-1 | 0.640 | 0.64 | 10 |
| 钢绳1-2 | 0.568 | 0.57 | 10 |
| 钢绳总拉力 | 1.208 | 1.21 | 10 |
| 独立伺服电机 | 1.199 | 1.20 | 10 |

该点钢绳总拉力为未舍入的 `0.640 + 0.568 = 1.208t`，格式化后显示1.21t。伺服1.199t是独立反馈，不能直接用钢绳总拉力代替。

速度是该来源采样或派生结果对应的运动速度，不是把两根钢绳的速度相加。两路都是10m/min时，总拉力行也不应显示20m/min。

### 2.3 两处最大最小为什么不同

截图中的“钢绳整体最大/最小0.66/0.37t”和“钢绳总拉力行最大1.23、最小0.76t”是不同指标：

| 指标 | 纳入的数据 | 截图显示 |
| --- | --- | --- |
| 钢绳整体最大/最小 | 两条单绳各自的有效读数集合 | 0.66 / 0.37t |
| 总拉力最大/最小 | 每个有效配对采样的两绳合计序列 | 1.23 / 0.76t |
| 伺服最大/最小 | 独立伺服反馈序列 | 1.24 / 0.74t |

总拉力最大值应取“逐点合计之后的最大值”，不能直接把两根单绳各自的最大值相加，因为两根绳的峰值可能发生在不同时刻。最小值同理。

### 2.4 顶部详情与历史光标不混用

页面上方的设备运行信息、拉力数据和伺服电机卡片来自设备详情快照。下方光标查看的是某次历史运行或统计周期。两者时间范围不同：光标移到22.5m，不应把上方设备当前高度改成22.5m，也不能用上方当前拉力填充历史曲线。

## 三 拉力对比接口与四种查看范围

### 3.1 当前实际请求

`GET /command-center/winch/tension/trend`

| Query字段 | 类型 | 内容 |
| --- | --- | --- |
| `systemCode` | string | 系统编号，例如 `QJXT-01` |
| `winchCode` | string | 单台动力设备编号，例如 `WJ-01` |
| `viewType` | string | 固定 `FORCE_COMPARISON` |
| `comparisonView` | string | `COUNT`、`DAY`、`MONTH`、`YEAR` |

```text
GET /api/command-center/winch/tension/trend?systemCode=QJXT-01&winchCode=WJ-01&viewType=FORCE_COMPARISON&comparisonView=COUNT
```

进入页面和切换查看范围时请求接口。选择已加载的运行、选择拉力来源、吨与公斤切换、鼠标取点，当前都使用已加载数据，不另发请求，也没有后台周期刷新。

### 3.2 四种范围的含义

| 页面选项 | 参数 | 分组类型 | 正式接口应有的分组 |
| --- | --- | --- | --- |
| 本日次数 | `COUNT` | `BATCH` 单次运行分组 | 每次实际运行一组，提供运行状态与采样 |
| 本日平均 | `DAY` | `PERIOD` 统计周期分组 | 今日、昨日、本月、本年平均 |
| 本月平均 | `MONTH` | `PERIOD` | 本月、上月、去年同期平均 |
| 本年平均 | `YEAR` | `PERIOD` | 本年、去年同期平均 |

当前模拟只覆盖2026-04-01至2026-10-22，所以模拟本月视图只有本月/上月，本年视图只有本年，没有伪造去年同期。正式接口无同期数据时应保留相应分组并明确 `empty:true`。

这里的月、年平均都是“不同高度上的平均拉力曲线”，横轴仍是高度，不是把横轴改成月份或日期。

### 3.3 当前响应的实际内容

统一外层仍为 `code`、`message`、`data`。当前 `data` 主要包含：

| 字段 | 当前用途 |
| --- | --- |
| `kind` | `BATCH` 或 `PERIOD` |
| `units` | 运行/周期的编号、名称、范围、状态、空数据标识等 |
| `series` | 关联 `unitId` 的来源曲线及采样点 |
| `heightMin`、`heightMax` | 实际采样高度范围 |
| `heightLimits` | 设备实际限位，独立于图表显示边界 |
| `dataRange`、`asOf`、`updatedAt` | 实际数据覆盖范围与截至时刻 |
| `demo` | 是否模拟数据 |

当前 `sourceRole` 只有 `SENSOR1`、`SENSOR2`、`SERVO`。**当前类型没有后端提供的总拉力来源，也没有右侧所需的统计对象。** 因而即使接入正式接口，页面仍会执行后文所列计算。

## 四 当前代码实际怎样处理数据

### 4.1 数据链路

```text
拉力对比接口或DEV模拟接口
  → units与三类来源series
  → 页面筛选运行、来源
  → 前端配对两绳并生成SUM曲线
  → 图表展示并产生悬停选择
  → 前端查找同高度采样、计算整组统计
  → 右上采样表与右下统计表
```

这条链路中的总拉力计算和统计不是开发环境专用逻辑，正式环境也会执行。

### 4.2 总拉力仍由前端配对相加

当前 `sensorSumSeries` 在同一个分组内找到钢绳1和钢绳2，要求以下字段完全一致后进行配对：`height`、`time`、`direction`、`sampleCount`。

两侧值有效时相加并保留6位小数；有一侧无效或找不到匹配点，合计值为 `null`。合计质量也由前端推导：有一侧为 `PARTIAL` 时合计为部分有效，否则为有效或无效。

当前前端用 `SUM` 表示来源选择，用 `SENSOR_SUM` 表示本地生成的合计曲线。这些不是已经由后端返回的第四来源。

### 4.3 右上表怎样取点

当前 `winchForceInspection` 从悬停曲线标识解析出分组，再在该组各来源中查找选中高度。

1. 优先取高度相同、时间也相同的点。
2. 如果该来源在此高度只有一个点，即使时间不同，也会回退取这个点。
3. 如果同高度有多个点而无同时间匹配，则显示“—”。

单次运行出现保护回退时，同一个高度会多次经过，所以“高度相同”不代表“同一个采样”。当前规则仍有跨时间回退，后续应改为使用后端统一采样键。

### 4.4 右下表怎样计算

当前前端先筛选当前分组中有效的曲线点，再计算：

```text
平均值 = Σ(value × weight) / Σ(weight)
weight = sampleCount；字段缺失时当前代码按1处理
最大值 = 当前来源有效点value的最大值
最小值 = 当前来源有效点value的最小值
```

`GOOD` 和 `PARTIAL` 当前可参与；`INVALID`、`OFFLINE`、空值、非有限值及非正 `sampleCount` 不参与右下统计。

“钢绳整体最大/最小”另行把当前分组两条单绳的有效点合并求极值。这个指标不包含总拉力或伺服，即使只显示一根单绳，它的计算范围仍是两条单绳集合。

### 4.5 平均视图有两层计算

| 层次 | 内容 | 当前在哪里完成 |
| --- | --- | --- |
| 某高度周期平均点 | 汇集该周期相同高度的原始有效采样，按真实样本计算平均值与速度 | DEV模拟接口中生成；正式接口应由后端提供 |
| 右下加权均值与最高/最低平均 | 再按高度平均点的 `sampleCount` 计算加权均值，并取这些平均点的最高、最低 | 当前仍由页面计算 |

平均视图里的“最高平均/最低平均”是平均曲线各高度点的极值，不是周期内全部原始瞬时采样的峰值。当前右下表没有使用点内的 `min/max` 字段替代这个指标。

当前模拟聚合会计入实际上行、下行及回退过程中的有效采样，并非只统计上行。一个高度被重复经过时会产生额外样本；因此不能只按“运行次数”代替有效采样数。

还需区分：当前 `units[].sampleCount` 表示周期内运行记录数量，而 `points[].sampleCount` 表示该高度点纳入的原始有效样本数量，两者不是同一个指标。正式接口建议把前者明确命名为 `includedRunCount`，避免误用作平均权重。

### 4.6 显示单位和坐标范围

| 项目 | 当前规则 |
| --- | --- |
| 基础力值单位 | t |
| 公斤显示 | 原始吨值乘1000；不改变接口原始数据 |
| 表格力值 | 吨保留2位，公斤显示整数 |
| 速度 | 始终为m/min，不随吨/公斤切换 |
| 横轴 | 固定0至30m；当前模拟实际采样多为0至25m |
| 仅选择单绳 | 纵轴0至1.4t，对应0至1400kg |
| 选择总拉力或伺服 | 纵轴0至3t，对应0至3000kg |

这些纵轴边界是显示配置，**不是预警值或安全保护值**，不能据此判断告警。

同时选择两根单绳时，右表会自动附加总拉力行；左图只有显式选中“钢绳总拉力”才绘制合计曲线。所以右表行数可以多于图上已选来源数。截图四来源都选中，因此显示四类曲线和四行数据。

## 五 当前仍需解决的问题

| 问题 | 当前情况 | 应调整为 |
| --- | --- | --- |
| 业务计算尚未后移 | 总拉力、加权均值和极值仍在页面计算 | 后端返回四来源序列与统计，前端直接展示 |
| 图表标记与右表查点不一致 | 图表标记在同高度点中按最近时间选；右表按同时间或单点回退取值 | 都依据同一个后端 `sampleKey` |
| 同高度跨时间回退 | 只有一个同高度点时可能拿其他时间的读数 | 没有同采样键就显示缺失，不替换为其他时刻 |
| 有效样本规则不统一 | 右下统计排除非正样本数；当前曲线/取点/合计没有完全采用同一规则 | 后端统一有效性与空值，返回完整质量说明 |
| 总拉力速度口径未明确 | 当前SUM点沿用钢绳1的 `speed`，没有验证两源速度 | 后端返回派生来源的实际速度或明确缺失 |
| 默认查看哪次仍靠前端选择 | 依赖运行状态与数组先后顺序 | 后端明确 `defaultUnitId` |
| 日期和平均口径易混淆 | 平均点的 `time` 当前可使用统计日期段字符串 | 单次展示采样时间，平均展示统计区间和截止时间 |
| 舍入策略可能改变显示 | 原始值与显示值精度不同 | 先按原始精度计算，最后统一格式化，不相加已显示字符串 |

例如，截图钢绳2的原始最大值是0.575t，当前JavaScript格式化显示为0.57t；不能从截图两位数字反推完整原始数据。后端若提前按另一种舍入策略保留两位，会改变当前效果，必须先约定。

此前测试证明了模拟数值和曲线一致，没有证明上表这些职责问题已经解决。它们应作为后续图表改造的明确范围。

## 六 前后端应承担的职责

### 6.1 后端返回完整业务结果

- 按系统、设备、运行或周期确定数据范围。
- 统一数据质量和有效样本规则。
- 对原始钢绳采样先配对、再求和，返回总拉力序列。
- 生成日/月/年各高度平均曲线及平均速度。
- 按分组与来源返回平均、最大、最小及有效样本数。
- 单独返回两条单绳集合的整体极值。
- 提供统一采样键、实际时间、高度、方向和缺失值，使各来源能够直接联动。
- 保留独立伺服反馈，不以两绳合计冒充伺服。

周期总拉力应基于共同有效配对样本聚合。只有两路均值来自相同配对样本、相同权重时，才可利用均值的线性关系相加；未经核对就相加两路独立周期均值，会得到错误结果。

### 6.2 前端保留展示与交互

- 发起四档查看范围查询，展示后端返回的分组和来源。
- 选择运行、显隐来源，并保留至少一个来源的交互约束。
- 图表命中一个后端采样键后，直接展示同键的各来源值与速度。
- 从当前组读取已返回的统计，不再用曲线重新求平均或极值。
- 进行单位、小数位、日期和颜色格式化，以及加载、失败、无数据等提示。

前端建立 `unitId/sourceRole/sampleKey` 索引用于快速查找属于展示处理；根据时间、高度、方向重新匹配样本并相加，属于应迁移的业务处理。

## 七 建议的后端返回字段

**本节是后续修订方案，尚未在当前接口类型和页面中实现。** 沿用当前GET地址，不新增独立总拉力、独立统计接口；一次返回当前范围需要的曲线和统计。

沿用code/message/data以及data.kind、units、series、heightMin、heightMax、heightLimits、dataRange、asOf、updatedAt。建议补充如下字段，名称需双方最终确认。

| 位置 | 字段 | 类型与含义 |
| --- | --- | --- |
| data | systemCode、winchCode、comparisonView | 回传实际查询身份与视图，防止页面混用响应 |
| data | defaultUnitId | string，首次未取点时默认查看的运行/周期；减少前端猜默认对象 |
| units[] | id、name | string，分组唯一键与实际显示名 |
| units[] | recordCode | string，COUNT下对应实际运行编号 |
| units[] | startAt、endAt、dataThrough | string，实际范围与数据截止时间，明确时区 |
| units[] | runState | COUNT为RUNNING或COMPLETED；只说明记录状态，不代替运行结果 |
| units[] | empty | boolean，无数据标识 |
| units[] | ropeExtrema | object，两个单绳来源的整体极值；maxTension、minTension为number或null |
| series[] | unitId | string，关联units[].id |
| series[] | sourceRole | SENSOR1钢绳1、SENSOR2钢绳2、ROPE_TOTAL钢绳总拉力、SERVO独立伺服 |
| series[] | name、sourceCode、unit | 后端显示名称、来源编号、基础单位t；总拉力不是实体传感器，可用明确的派生来源编号 |
| series[] | points | 已整理的采样或周期平均点，含统一采样键 |
| series[] | statistics | 后端计算的averageTension、maxTension、minTension、validSampleCount；无样本时力值null、数量0 |
| points[] | sampleKey | string，同一分组内唯一；不同来源共享同一采样键，直接支持跨来源联动 |
| points[] | height | number，高度m |
| points[] | time | COUNT为真实采样时刻；PERIOD可为null并使用分组范围，不能把平均值伪装为某一真实采样时刻 |
| points[] | value | number或null，拉力t；无有效值用null |
| points[] | speed | number或null，该点的实际速度或已计算平均速度m/min |
| points[] | direction | COUNT的实际UP/DOWN/STOP；周期统计方向口径由后端明确，不从图线名称猜 |
| points[] | quality | GOOD、PARTIAL、INVALID、OFFLINE；后端负责有效性处理 |
| points[] | sampleCount | 原始有效样本数；COUNT原始点通常为1，PERIOD表示实际聚合样本数量 |

`sourceRole=ROPE_TOTAL`、`statistics`、`ropeExtrema`、`sampleKey`、`defaultUnitId`均属于建议新增或修订字段。当前sourceRole类型只有三类，前端仍用SUM和SENSOR_SUM标识本地合计，需要同步适配，不能只改文档。

单位换算与小数位仍可由前端完成。前端按unitId选择一组，按sourceRole显隐曲线，鼠标命中一个sampleKey后直接读取该组各来源相同键；不重新配对、相加、补值或计算统计。

为避免同一概念出现多套名称，本说明使用 `sampleKey` 代表后端统一采样键；此前说明中的 `pointKey` 也是这一用途，不应同时维护两套键。

分组与曲线的对应规则：

1. `unitId` 对应一次运行或一个统计周期。
2. 每个非空分组按实际数据提供四类来源，缺失来源也需明确为空及原因。
3. 同一分组内四个来源通过后端分配的 `sampleKey` 对应；相同高度的不同经过时刻必须是不同键。
4. 没有对应有效值的来源返回 `null`，前端不插值、不用最近时间补齐。
5. 当前界面中的 `SUM` 选择值及 `SENSOR_SUM` 本地识别方式，应适配统一的 `ROPE_TOTAL` 来源。
6. `statistics` 属于其所在曲线对应的完整分组；`ropeExtrema` 只覆盖两条单绳，均不能误用其他分组数据。

平均模式使用 `PERIOD` 时，`statistics.maxTension/minTension` 表示最高/最低平均点；如果后续还要显示周期原始峰值，应新增明确字段，不能混用。

## 八 截图对应的真实数据与响应示例

本例从当前真实模拟生成器读取第9次运行，不构造采样来凑统计。该次每个来源有51个有效点，下表统计来自完整51点；JSON的points仅摘录截图命中的22.5m、12:22:15一帧，不是完整可直接绘图的响应。正式接口需返回完整曲线，其他运行分组也需返回。统计均值在示例中保留6位，页面显示精度为吨2位。

| 来源 | 22.5m原始值t | 整次平均t | 整次最大t | 整次最小t | 有效点 |
| --- | --- | --- | --- | --- | --- |
| 钢绳1 | 0.640 | 0.548255 | 0.660 | 0.388 | 51 |
| 钢绳2 | 0.568 | 0.494824 | 0.575 | 0.372 | 51 |
| 钢绳总拉力 | 1.208 | 1.043078 | 1.230 | 0.760 | 51 |
| 独立伺服 | 1.199 | 1.039961 | 1.240 | 0.738 | 51 |

截图显示0.64、0.57、1.21、1.20，是原始值按当前页面精度格式化后的结果。总拉力按未舍入的0.640+0.568=1.208计算后再显示1.21，不能先相加已经显示的字符串。单绳整体极值为0.660与0.372，显示0.66/0.37。S2原始最大0.575在当前JavaScript Number.toFixed(2)下显示0.57，说明不能从截图两位显示反推完整原始数据；正式舍入策略需要前后端一致约定。

下面把真实采样与统计放入建议的后端成品响应结构。sampleKey、statistics、ROPE_TOTAL等仍是建议字段，不是当前生产接口已返回的能力。

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "systemCode": "QJXT-01",
    "winchCode": "WJ-01",
    "comparisonView": "COUNT",
    "kind": "BATCH",
    "defaultUnitId": "B-20",
    "dataRange": {
      "startDate": "2026-04-01",
      "endDate": "2026-10-22"
    },
    "asOf": "2026-10-22T18:00:00+08:00",
    "updatedAt": "2026-10-22T18:00:00+08:00",
    "heightMin": 0,
    "heightMax": 25,
    "heightLimits": {
      "min": 0,
      "max": 25
    },
    "units": [
      {
        "id": "B-9",
        "name": "第9次运行",
        "recordCode": "RUN-20261022-9",
        "startAt": "2026-10-22T12:20:00+08:00",
        "endAt": "2026-10-22T12:22:30+08:00",
        "dataThrough": "2026-10-22T12:22:30+08:00",
        "runState": "COMPLETED",
        "empty": false,
        "ropeExtrema": {
          "maxTension": 0.66,
          "minTension": 0.372
        }
      }
    ],
    "series": [
      {
        "unitId": "B-9",
        "sourceRole": "SENSOR1",
        "name": "钢绳1-1拉力",
        "sourceCode": "WJ-S1",
        "unit": "t",
        "statistics": {
          "averageTension": 0.548255,
          "maxTension": 0.66,
          "minTension": 0.388,
          "validSampleCount": 51
        },
        "points": [
          {
            "sampleKey": "B-9-UP-122215",
            "time": "2026-10-22T12:22:15+08:00",
            "height": 22.5,
            "value": 0.64,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD",
            "sampleCount": 1
          }
        ]
      },
      {
        "unitId": "B-9",
        "sourceRole": "SENSOR2",
        "name": "钢绳1-2拉力",
        "sourceCode": "WJ-S2",
        "unit": "t",
        "statistics": {
          "averageTension": 0.494824,
          "maxTension": 0.575,
          "minTension": 0.372,
          "validSampleCount": 51
        },
        "points": [
          {
            "sampleKey": "B-9-UP-122215",
            "time": "2026-10-22T12:22:15+08:00",
            "height": 22.5,
            "value": 0.568,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD",
            "sampleCount": 1
          }
        ]
      },
      {
        "unitId": "B-9",
        "sourceRole": "ROPE_TOTAL",
        "name": "钢绳总拉力",
        "sourceCode": "SENSOR_SUM",
        "unit": "t",
        "statistics": {
          "averageTension": 1.043078,
          "maxTension": 1.23,
          "minTension": 0.76,
          "validSampleCount": 51
        },
        "points": [
          {
            "sampleKey": "B-9-UP-122215",
            "time": "2026-10-22T12:22:15+08:00",
            "height": 22.5,
            "value": 1.208,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD",
            "sampleCount": 1
          }
        ]
      },
      {
        "unitId": "B-9",
        "sourceRole": "SERVO",
        "name": "伺服电机拉力",
        "sourceCode": "SERVO",
        "unit": "t",
        "statistics": {
          "averageTension": 1.039961,
          "maxTension": 1.24,
          "minTension": 0.738,
          "validSampleCount": 51
        },
        "points": [
          {
            "sampleKey": "B-9-UP-122215",
            "time": "2026-10-22T12:22:15+08:00",
            "height": 22.5,
            "value": 1.199,
            "speed": 10,
            "direction": "UP",
            "quality": "GOOD",
            "sampleCount": 1
          }
        ]
      }
    ]
  }
}
```

前端命中unitId=B-9、sampleKey=B-9-UP-122215后，右上四行直接读取各来源该帧value及speed；右下读取完整运行统计statistics；钢绳整体最大/最小读取本分组ropeExtrema，不在浏览器里配对、相加或计算统计。

完整模拟COUNT默认查看最新已完成的第20次，因此此处 `defaultUnitId` 为 `B-20`。为聚焦截图，JSON只摘录了 `B-9` 分组和一个采样帧，省略其余分组（含 `B-20`）及曲线点；该片段不能直接作为完整绘图响应。鼠标选中第9次后，再用第9次覆盖当前检视对象，不改变原有默认选择效果。

## 九 下一步代码调整与验收

### 9.1 需要调整的文件和内容

| 位置 | 当前内容 | 下一步调整 |
| --- | --- | --- |
| `src/types/index.ts` | 三种来源，采样和分组结构 | 增加 `ROPE_TOTAL`、`sampleKey`、`statistics`、`ropeExtrema` 等目标字段 |
| `src/api/commandCenterPages.ts` | DEV模拟与生产透传，当前未返回全部成品字段 | DEV按相同原始数据生成成品结果；生产要求后端按新契约返回并验证 |
| `src/utils/powerDetailTrend.ts` | `sensorSumSeries`、`matchingPoint`、`winchForceInspection` 中仍有配对与统计 | 页面链路移除配对相加、加权均值和极值计算，只保留查询索引与格式化 |
| `src/utils/powerDetailPresentation.ts` | 单次按时间、平均按高度排序 | 正式曲线顺序由后端提供，页面不再修复业务采样顺序 |
| `src/views/power-detail/index.vue` | 页面临时生成SUM，逐点拼装展示模型 | 直接选择后端四来源，消费后端统计与统一采样键 |
| `src/components/TrendChart/index.vue` | 同高度最近时间标记 | 命中真实点后传递 `sampleKey`，不为其他来源选替代时刻 |
| `src/components/WinchForceComparison/index.vue` | 调用前端计算函数生成两张表 | 按当前分组、来源和采样键直接读取后端结果 |

`matchingPoint` 等工具还被历史图表使用，调整时必须区分调用范围，不能只为了详情页直接删除共享函数而破坏历史页面。

### 9.2 保持当前模拟效果的方法

不更换模拟数据源，不修改205天、每天20次、四台设备和原有拉力/速度公式。先用现有规则生成同一套总拉力和统计，把结果放入DEV模拟接口响应，再让页面只读取这些结果。

第13次运行的601个回退采样、正常轮次的51个采样、单次真实轨迹、原有日期范围和独立伺服曲线均应保留。不能为了减少前端代码就把曲线改成少量点、平滑曲线或一条平均线。

**将计算移入DEV模拟接口，只代表前端对接形式已准备好，不等于真实后端已经完成。** 正式后端仍需实现同一业务口径并返回相同结构。

### 9.3 验收场景

| 场景 | 需要验证的结果 |
| --- | --- |
| 截图第9次、22.5m取点 | 显示0.64、0.57、1.21、1.20t与速度10；同一采样键联动 |
| 第9次整轮统计 | 四来源结果与当前截图一致；统计范围不变成全日20次 |
| 单绳整体与总拉力极值 | 分别保持0.66/0.37与1.23/0.76t，不相互覆盖 |
| 全部次数与单次选择 | 全部仍叠画20轮；选择第9次后只查看该次的曲线与数据 |
| 单根/双根/伺服/总拉力多选 | 行、曲线和统计来自所选来源；不由页面新增业务值 |
| 本日/月/年平均 | 返回正确周期分组、平均曲线及统计；没有去年数据不造值 |
| 同高度重复经过或来源缺点 | 不取其他时刻替代，缺失显示空值 |
| 无效或0有效样本 | 曲线、取点、合计和统计采用同一后端质量口径 |
| 仅切换吨/公斤 | 力值换算正确，速度及原始统计不变 |
| 页面刷新与范围切换 | 按用户操作加载，不恢复后台5秒刷新 |
| 职责检查 | 正式页面不再调用配对求和、加权平均和极值计算来生成业务结果 |

### 9.4 本次说明的核对依据

本文核对了当前详情页、统一拉力对比接口、图表组件、右侧数据组件及真实模拟生成器；已重新提取截图第9次运行的采样和完整统计。以下源码位置用于后续实施定位：

- `src/api/commandCenterPages.ts`：`getWinchForceComparisonApi`、`localSystemHistoryCurves`。
- `src/utils/powerDetailTrend.ts`：`sensorSumSeries`、`matchingPoint`、`winchForceInspection`。
- `src/utils/powerDetailPresentation.ts`：`orderedDetailTrendPoints`。
- `src/views/power-detail/index.vue`：`comparisonSet`、`trendModel`、`loadTrend`。
- `src/components/TrendChart/index.vue`：`showHeightProbe`。
- `src/components/WinchForceComparison/index.vue`：两张数据表及选择联动。

本次输出的是核查与对接说明，没有将尚未完成的后端计算迁移描述为已完成。
