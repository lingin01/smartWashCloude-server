> 归档说明：来自用户本次上传附件，仅统一换行、库内交叉链接及未随附图片/资料的引用说明；正文中的实现与测试结论属于前端报告，不代表本Java仓库已实现或验证。

# 分解槽抑制结疤智能体平台 当前项目总结与详情拉力对比图数据处理说明

**全部页面与PLC告警红字统一修订版 · 2026-10-25**

本版在原文件中更新，文件名中的20261024保留以沿用已有链接；正文现状以本次修订日期为准。

## 目录

- [阅读范围与当前结论](#page-01)
- [页面目录与导航](#page-02)
- [公共接口、时间和数据约定](#page-03)
- [登录、身份与公共导航](#page-04)
- [首页：系统信息、系统状态和设备状态](#page-05)
- [动力设备运行详情](#page-06)
- [系统历史记录页面](#page-07)
- [单次历史记录详情与新版拉力对比图](#page-08)
- [两种新版拉力对比图的关系与数据处理](#page-09)
- [新截图的逐项核对与实际数据示例](#page-10)
- [历史记录导出与单次采样导出](#page-11)
- [数据归档页面](#page-12)
- [告警中心](#page-13)
- [设置参数](#page-14)
- [用户管理](#page-15)
- [模型管理](#page-16)
- [历史巡检与数据分析占位页](#page-17)
- [历史运行头的完整字段补充](#page-18)
- [字体、颜色和表格呈现](#page-19)
- [前后端职责与下一步对接清单](#page-20)
- [本次文档核验与适用限制](#page-21)

<a id="page-01"></a>

## 阅读范围与当前结论

本版修订于2026-10-25，替换2026-10-24版本中已经过时的页面现状。依据是当前`src`源码和本次提供的新截图；旧文档中的设计建议不能作为“已经实现”的证明。

**本次覆盖当前12个页面，包括正式业务页和占位页；视频预览仍按此前要求排除。** 公共导航、登录会话、字体、列表分页、异常提示和导出也纳入说明。本次先优化前端，再同步说明：告警事件、阈值编辑、认证和操作提示已经调整；原始模拟曲线及采样值保持。

最需要纠正的现状如下：

| 项目 | 当前事实 |
| --- | --- |
| 新截图对应页面 | 系统历史记录中的“单次运行详情”，组件为`RunForceComparison` |
| 新截图的右侧 | 所选曲线的完整采样表：高度、拉力、速度；不是光标当前点的四来源表 |
| 新截图的控制项 | 四台动力设备多选、钢绳1号/钢绳2号/伺服来源多选、t/kg、查看曲线、导出本次数据 |
| 动力设备详情图 | 同样已使用逐点表，但还保留本日次数/本日平均/本月平均/本年平均，以及单台钢绳总拉力选项 |
| 旧右侧双表 | 旧“该高度实际数据”“本次运行统计”双表已从两种当前图组件中移除；不能继续描述为当前显示内容 |
| 业务计算是否全部迁出前端 | 尚未全部迁出。动力页总拉力、单次CSV中的钢绳配对合计等仍在前端；DEV平均曲线由前端演示数据层生成 |
| 统计函数与可见页面 | 源码中留存部分旧统计辅助函数，不等于当前页面仍显示对应统计；本文只把实际调用链算作当前行为 |
| 后端实现状态 | 本仓库为前端，以下HTTP路径是生产请求声明；没有据此验证真实后端、真实设备采集或MySQL落库 |

<a id="page-02"></a>

## 页面目录与导航

| 页面 | 当前路由 | 入口及状态 |
| --- | --- | --- |
| 登录 | `/login` | 无需登录；账号登录表单 |
| 首页 | `/dashboard` | 登录后默认页面，三栏监控总览 |
| 告警中心 | `/cloud/alarms` | 一级导航；默认最新，普通/严重筛选，独立历史查询 |
| 设置参数 | `/cloud/parameter-settings` | 一级导航；有查询、保存 |
| 用户管理 | `/cloud/user-management` | 一级导航；只读查询 |
| 模型管理 | `/cloud/model-management` | 一级导航；当前展示及待开放功能 |
| 历史巡检 | `/cloud/history-inspection` | 一级导航；开发中占位页 |
| 数据分析 | `/cloud/data-analysis` | 一级导航；开发中占位页 |
| 动力设备详情 | `/command-center/winch/:winchCode` | 首页设备入口，query传`systemCode`；独立详情页 |
| 数据归档 | `/command-center/archive/:systemCode` | 独立路由保留，当前可见页面未接归档入口，可直接访问 |
| 系统历史记录 | `/command-center/history/:systemCode` | 动力详情“更多历史记录”入口，独立页面 |
| 历史记录详情 | `/command-center/history/:systemCode/detail/:recordCode` | 当前正常入口是单次RUN记录 |

`/`仍是首页入口，保留。旧`/command-center/alarms/:systemCode`已移除，所有告警导航统一使用`/cloud/alarms?systemCode=...`。旧DAY/MONTH/QUARTER/HALF_YEAR/YEAR记录详情链接会在路由进入时返回历史列表，不再当成当前可访问的周期详情页面。源码保留相应分支，只能标记为遗留能力。

公共侧栏依次显示：首页、告警中心、设置参数、用户管理、模型管理、历史巡检、数据分析。顶部可展开/收起导航、展示当前昵称和退出登录；告警菜单红点仅使用后端hasAttention，进入告警页不清除。独立详情页使用返回按钮和各自操作入口，不应描述为所有页面都有同一侧栏。页面标题由当前路由标题与平台名组成。

导航收起宽度64px，展开宽度`clamp(180px,14vw,220px)`；窄屏历史巡检导航采用抽屉处理。布局变化属于前端呈现，不新增业务接口。

<a id="page-03"></a>

## 公共接口、时间和数据约定

### 接口响应和认证

API基础地址为`VITE_API_BASE_URL`，未配置时是`/api`。下面接口路径均相对于基础地址，不能和浏览器页面路由混为一谈。

```json
{
  "code": 200,
  "message": "数据获取成功",
  "data": {}
}
```

`data`的具体结构见各页。成功业务码是200；非200按`message`提示并抛出失败。请求默认超时15秒，JSON提交，存在登录令牌时添加`Authorization: Bearer <token>`。没有令牌的业务路由跳转登录页。

认证相关接口为登录、读取资料、退出和刷新令牌。HTTP 401并发请求共享一次刷新，最多重试一次；成功消费新token、可选轮换refreshToken和有效expiresIn；刷新失败时所有等待请求结束，不悬挂。没有刷新令牌、刷新失败或重试仍401会清理当前会话并跳`/login`，与history路由一致。过期会话结果不会覆盖新登录。

相同GET在途重复仍取消旧请求，支持调用方取消信号，合法分页和后台查询不受写保护影响。相同写操作仍在途时保留原请求、阻止重复发送，第一次提示“请勿频繁操作”；持续点击只延长静默期，不重复弹出，停止3秒后可再提醒。已完成后的合法再次提交不被长期封锁，不同参数也不被误判为同一操作。

重复网络/业务错误与429采用相同的防提示刷屏机制。15秒超时不变；真正的账号频率上限及设备授权仍由后端执行。前端取消或重复提交不被告警页解释成PLC故障。

### 当前生产HTTP入口总览

| 方法与路径 | 输入位置与主要字段 | 返回主体/消费页面 |
| --- | --- | --- |
| `POST /auth/login` | JSON：username、password | token、refreshToken、expiresIn；登录 |
| `GET /auth/user-info` | 登录认证上下文 | 用户资料、roles、permissions；用户昵称/状态 |
| `POST /auth/logout` | 登录认证上下文 | 退出结果；无业务数据 |
| `POST /auth/refresh` | JSON：refreshToken | token及可选refreshToken、expiresIn；更新轮换令牌与有效期 |
| `GET /command-center/home` | Query：systemCode | 首页静态信息及实时快照 |
| `POST /command-center/emergency-stop` | JSON：systemCode | 指令请求结果；未定义独立执行进度对象 |
| `GET /command-center/winch/detail` | Query：systemCode、winchCode | 单台设备当前快照 |
| `GET /command-center/winch/tension/trend` | Query：systemCode、winchCode、viewType=FORCE_COMPARISON、comparisonView | 单台设备COUNT/DAY/MONTH/YEAR分组曲线 |
| `GET /command-center/alarms/latest` | Query：systemCode，可选deviceCode | 独立recent、asOf、hasAttention；页面与侧栏共用 |
| `GET /command-center/alarms/history` | Query：systemCode、range、日期、levels、deviceCode、page、pageSize | list、total、page、pageSize、historyRange、asOf |
| `PUT /command-center/winch/tension-thresholds` | JSON：systemCode、winchCode、两路thresholds | APPLIED/PENDING及真实effectiveThresholds |
| `GET /command-center/archive` | Query：系统、设备、指标、周期、比较方式、记录和日期参数 | 变化序列、对比序列、summary |
| `GET /command-center/history/list` | Query：systemCode、scope=RUN、startTime、endTime、endExclusive、includeAvailableDates、page、pageSize | 单次运行列表、total、availableDates等 |
| `GET /command-center/history/detail` | Query：systemCode、recordCode、yearGrouping | 一轮运行头、八路钢绳、四路伺服及高度信息 |
| `GET /command-center/parameter-settings` | Query：systemCode | 启动时间、运行间隔、运行速度 |
| `PUT /command-center/parameter-settings` | JSON：systemCode、startTime、intervalMinutes、speedMPerMinute | 保存后的三项设置 |
| `GET /users` | Query：可选keyword、page、pageSize | list、total、page、pageSize；用户管理 |

以上为当前页面使用的17种HTTP方法与路径组合，其中新告警查询及阈值保存为新增前端契约，尚需真实后端实现。旧alarm-dashboard保留用于DEV固定源和未启用的历史统计代码，不再作为告警页正式入口。视频相关接口不计入；占位页、图表下拉、图例和单位切换不单独增加接口。旧周期历史函数即使保留，也不能作为当前页面额外接口需求。

### 系统、设备和来源字段

当前示例系统为`QJXT-01`，设备为`WJ-01`～`WJ-04`，首页传感器为`PS-01`～`PS-08`；历史曲线使用`S1`～`S8`及`SERVO-1`～`SERVO-4`。这些是不同数据上下文的标识，不应仅凭数组下标互相替换。正式后端需给出明确来源关系。

系统字段统一使用`systemCode/systemName/systemHeight/systemCurrentHeight`等。首页系统高度、设备详情当前高度、历史光标高度分别属于不同时间上下文，不能混写。首页/当前传感器字段`pressure`的业务含义是拉力，基础单位为t；不把它解释为Pa或压力值。

### 公共枚举与单位

| 枚举/单位 | 当前含义 |
| --- | --- |
| 系统/设备状态 | IDLE待机、RUNNING运行、MAINTENANCE检修、FAULT故障、OFFLINE离线；使用后端状态文字 |
| 运动方向 | UP上行、DOWN下行、STOP停止 |
| 传感器状态 | NORMAL正常、WARNING拉力提示、ALARM拉力异常、FAULT故障、OFFLINE离线；前端不比较阈值重判 |
| 采样质量 | GOOD有效、PARTIAL部分有效、INVALID无效、OFFLINE离线；实际可用规则见图表与导出章节 |
| 运行结果 | COMPLETED完成、INCOMPLETE未完成、FAULT_TERMINATED故障终止；与实时状态独立 |
| 启动方式 | SCHEDULED定时启动、MANUAL人工启动 |
| 拉力 | 基础t；kg=t×1000；图表/表格显示精度不作为原始入库精度 |
| 高度 | m；0～30m是当前图表显示范围，不是生产设备安全限位 |
| 速度 | m/min；设定速度与实际采样速度分别记录 |

设备状态中的旧EMERGENCY_STOP已删除；历史`endReason=EMERGENCY_STOP`和急停操作仍保留，不矛盾。状态颜色由枚举决定，不能仅凭颜色判断业务状态。

### 时间、演示与分页

DEV演示历史固定在2026-04-01～2026-10-22，截止快照为2026-10-22 18:00:00；不代表真实系统当前数据日期。首页DEV动态演示会自行更新。生产页面应以接口实际数据时间、asOf、dataRange为准。

历史范围按上海时区、开始时间左闭右开查询：`startTime <= startedAt < endTime`。告警自定义日期按发生日期包含首尾，最多366天。两种输入口径不能合并成同一规则。

普通可见列表固定每页10条；历史列表、用户列表和历史告警由后端分页；最新告警在独立完整近期集合上按10条展示。导出使用自己的200条查询分页和批次限制，不等于导出当前10条。

<a id="page-04"></a>

## 登录、身份与公共导航

### 页面与可见内容

- 登录路由：`/login`，是唯一声明`noAuth: true`的页面。平台名为“分解槽抑制结疤智能体平台”，介绍文案为“连接设备、传感器与工艺数据，为单台分解槽提供可追溯的运行分析。”，能力标签为“设备数据接入”“工艺趋势分析”。
- 登录卡片显示“账号登录”“请输入账号和密码进入平台”，用户名、密码、显示密码开关和“登 录”按钮。按回车与点击按钮执行同一提交动作。
- 公共侧栏包含：首页、告警中心、设置参数、用户管理、模型管理、历史巡检、数据分析。数据归档、运行历史、设备详情为独立业务路由，不等于拥有独立侧栏菜单。
- 顶部显示当前昵称及“退出登录”，导航可收起/展开；点击平台Logo返回首页。当前昵称缺失时显示“管理员”；因此出现“管理员”文字不代表服务端已确认管理员权限。

### 请求与返回字段

| 接口 | 输入 | 返回及页面用途 |
| --- | --- | --- |
| `POST /auth/login` | JSON：`username: string`、`password: string` | `token`、`refreshToken`、`expiresIn`。前两项写入本地存储；页面随后获取用户信息再跳转首页。`expiresIn`当前没有用于显示倒计时 |
| `GET /auth/user-info` | 登录令牌认证上下文 | `id`、`username`、`nickname`、`avatar`、`email`、`phone`、`roles[]`、`permissions[]`；当前顶部消费昵称，角色/权限进入用户状态仓库，头像/邮箱/电话未在现有页面另设编辑入口 |
| `POST /auth/logout` | 登录认证上下文，无页面输入体 | `data: null`；无论接口成功或失败，页面最终清理本地令牌和用户信息并返回登录页 |
| `POST /auth/refresh` | JSON：`refreshToken` | 返回新token、可选refreshToken和expiresIn；自动刷新会保存实际返回的轮换令牌和有效秒数，未返回refreshToken时保留原值 |

前端只校验用户名、密码必填。密码正确性、账号是否可用、角色与资源授权由后端确认。当前DEV登录只要求账号、密码去空白后非空，返回固定演示令牌；演示用户为`demo / 管理员 / roles=[ADMIN] / permissions=[*]`，不是实际账号系统。

### 刷新、失败与职责边界

登录中按钮显示加载状态；表单空值分别提示“请输入用户名”“请输入密码”。真实请求使用统一业务响应`code/message/data`，业务码200成功；请求携带`Authorization: Bearer ...`。登录页不另拼接业务失败信息，主要依靠公共请求提示。

应用启动后先呈现路由内容，再异步补齐已登录用户信息；资料获取失败会执行退出。启动路由加载失败时显示“页面暂时无法打开。”“重新加载”。路由守卫当前只判断本地是否存在token，没有按菜单或按钮逐项执行权限过滤；有用户角色字段不等于所有页面已实现权限控制。后端仍须对每次查询和控制动作做授权。

公共请求超时为15秒，网络错误提示“网络异常，请检查连接”，一般业务失败展示后端message。失效跳转已统一为`/login`，并发刷新及失败/取消已验证；真实认证服务仍需联调。

代码定位：`src/views/login.vue`、`src/api/login.ts`、`src/store/modules/user.ts`、`src/utils/request.ts`、`src/utils/auth.ts`、`src/App.vue`、`src/router/index.ts`、`src/layouts/MainLayout.vue`。

<a id="page-05"></a>

## 首页：系统信息、系统状态和设备状态

### 页面结构与数据入口

路由为`/dashboard`，所属系统读取query的`systemCode`，缺省使用`QJXT-01`。三个主区域依次是“◆ 系统信息”“◆ 系统状态”“◆ 设备状态”。真实数据入口为`GET /command-center/home?systemCode=...`，返回一个系统快照，包含4台动力设备和8个钢绳传感器。生产环境不调用DEV生成器，不在页面比较阈值后重算状态或平均值。

### 系统级字段

| 字段 | 含义/类型/单位 | 现有页面使用方式 |
| --- | --- | --- |
| `systemCode` | 系统编号，string | 查询和操作的系统上下文；当前不是系统信息区的一行可见数值 |
| `systemName` | 系统名称，string | 数据身份字段；侧栏平台名实际来自前端`PLATFORM_NAME`常量，不由该字段动态改写 |
| `systemStatus` | IDLE / RUNNING / MAINTENANCE / FAULT / OFFLINE | 系统配色、状态图例高亮，不能根据某一钢绳预警替代系统工况 |
| `systemStatusText` | 后端中文状态 | “当前状态”的直接展示值 |
| `online` | 在线布尔值 | 快照状态数据；当前没有独立“在线/离线”文本行 |
| `systemHeight` | 系统总高度，m | 中部标尺范围；当前演示为30m |
| `systemCurrentHeight` | 当前高度，m | 左侧“实时高度”及中部高度标记，各保留1位小数；清洗盘位置随之变化 |
| `isMoving`、`movementDirection` | 是否运动及UP / DOWN / STOP | 左侧上下箭头高亮；`isMoving=false`时方向呈停止 |
| `averageTension` | 八个传感器平均拉力，t | 左侧“平均拉力”，2位小数；正式值由后端返回 |
| `averageSpeed` | 四台设备平均速度，m/min | 左侧“当前速度”，1位小数；不是页面自行平均设备速度 |
| `updatedAt` | 数据更新时间 | 当前快照/推送携带，首页模板没有独立展示更新时间栏 |
| `winches[]`、`sensors[]` | 设备、传感器数组 | 右侧卡片、中部机械示意及测点弹窗 |

五状态的中文图例是“待机、运行、检修、故障、离线”。图例提供配色和高亮映射，实际工况与中文说明来自接口。删除的旧急停状态枚举不恢复；急停仍作为操作保留。

### 动力设备卡片字段

| `winches[]`字段 | 中文含义/用途 | 展示规则 |
| --- | --- | --- |
| `winchCode` | 设备编号 | 卡片标识、传感器关联与详情跳转参数 |
| `winchName` | 设备实际名称 | 卡片标题和中部设备标签；不根据groupNo重新生成生产设备名称 |
| `groupNo` | 设备分组序号 | 中部4组机械布局、测点选中联动 |
| `operatingStatus` | 设备五状态枚举 | 卡片和状态标签配色；系统故障不自动覆盖每台设备状态 |
| `statusText` | 设备中文状态 | 状态标签直接显示 |
| `direction`、`directionText` | UP / DOWN / STOP及中文说明 | 上下箭头高亮和悬浮说明 |
| `speed` | 运行速度，m/min | “运行速度”，1位小数 |
| `sensorCodes` | 两个关联测点编号 | 身份关系字段；卡片当前通过`sensors[].winchCode`取本设备传感器 |

每台卡片包含设备名/状态、并排上下方向箭头、两条钢绳读数、运行速度和“查看详情”。点击“查看详情”跳转`/command-center/winch/{winchCode}?systemCode={systemCode}`。点击钢绳读数只选中中部对应设备分组，不跳往告警中心；独立告警菜单仍保留。

### 钢绳传感器字段

| `sensors[]`字段 | 中文含义/单位 | 页面用途 |
| --- | --- | --- |
| `sensorCode`、`sensorName` | 测点编号、实际名称 | 唯一标识、读数标题、悬浮说明和弹窗 |
| `metric` | WINCH_TENSION / TRACTION_ROPE_TENSION | 本设备的第1/第2钢绳来源角色；不是由数组下标猜测 |
| `businessRole` | 业务角色文字 | 合同字段，首页当前未设独立可见行 |
| `groupNo`、`winchCode` | 分组及所属设备 | 卡片关联与机械示意定位 |
| `position` | 原始测点位置文字 | 数据身份字段；中部弹窗标题的位置现由固定机械布局产生，不能宣称全由此字段驱动 |
| `pressure`、`unit` | 当前拉力，基础单位t | 两位小数；字段名pressure沿用现状，含义为拉力 |
| `alarmThreshold` | 预警值，t | 卡片悬浮提示“预警值 ... 吨”，不在首页重新比较阈值决定状态 |
| `safetyValue` | 安全保护值，t | 悬浮提示“安全保护值 ... 吨”；联锁和保护处理由后端/控制系统执行 |
| `status`、`statusText` | NORMAL / WARNING / ALARM / FAULT / OFFLINE及中文 | 读数和测点颜色、异常提示、悬浮说明；FAULT/OFFLINE读数显示“—”，其他状态显示接口数值 |

演示初始测点编号PS-01～PS-08，按WJ-01～WJ-04各关联两个；演示预警值1.1t、安全值1.3t仅属于当前DEV模型，不能写成生产数据库已确认的真实阈值。

### 中部机械示意及交互

示意图为二维圆柱透视，4台设备顺时针排列、每台两根钢丝绳连接清洗盘、共8个测点。当前布局的一号在前下方、二号左侧、三号后上方、四号右侧；直径14m等布局常量服务于当前绘制，不是接口动态提供的全部机械尺寸。

标尺按systemHeight生成，0.1m小格、0.5m中格、1m主刻度；实时高度标记和清洗盘共用同一纵坐标。为了图形稳定，几何层把高度限制在0到systemHeight范围，非法systemHeight回退绘制高度30m；左侧数值仍直接读取原快照，因此这只是展示防护，不能替代后端数据校验或把越界值改写成合法业务值。

点击钢绳、测点、设备标签可开关分组弹窗，点击空白或“×”关闭。弹窗显示“设备名 · 布局位置”、该设备两路传感器名称与拉力；未配置测点显示“测点未配置”，故障/离线读数显示“—”。检修配色属于机械示意呈现，不把传感器NORMAL状态伪装成WARNING。

### 急停操作

按钮初始文案“长按急停”，说明“持续按住 3 秒下发急停指令”；按下时显示“继续按住…”。连续按住3秒才调用`POST /command-center/emergency-stop`，JSON输入`{ systemCode }`。松开、移出按钮、触摸结束或离开组件时取消未触发计时。

成功后提示“停止指令已下发”。当前接口数据类型无执行阶段字段；请求成功不等于已收到设备停止确认。按钮不在本地直接改写系统/设备状态，真实执行、回执、权限与最终状态由后端/控制系统负责。DEV仅返回演示成功，不停止页面模拟流。

### 加载、刷新、空值与当前未完项

- 进入首页或query中的systemCode改变时加载；切换系统时旧响应不会覆盖新系统，离开时关闭DEV订阅。
- DEV初次快照后每500ms推送模拟快照，仅页面可见时推进，含运行、整套检修、单台故障及全离线等阶段。高度和读数变化是模拟机制，不是生产业务逻辑已实现。
- 生产当前只发初始GET；尚无真实WebSocket/SSE订阅、每秒推送或自动轮询。实时推送类型已定义，不等于传输协议与服务地址已经接通。
- 无home时显示“正在加载实时数据…”。首页当前未提供独立失败状态、重新加载按钮或“无系统数据”面板；请求失败不能在说明中写成已有完整重试流程。
- 后端必须返回权威系统状态、设备状态、测点质量、阈值、平均拉力和平均速度。前端只处理颜色、方向箭头、格式化、机械投影及选中联动。

代码定位：`src/views/home/index.vue`、`src/views/home/components/{SystemInfoPanel,SystemStatusPanel,DeviceStatusPanel,PowerDeviceCard,TankOverviewSvg}.vue`、`src/views/home/composables/{useHomeDashboard,useEmergencyHold,useTankGeometry}.ts`、`src/api/commandCenter.ts`、`src/types/index.ts`。

<a id="page-06"></a>

## 动力设备运行详情

路由：`/command-center/winch/:winchCode?systemCode=...`。`winchCode`来自路径，`systemCode`来自查询参数，缺失时当前页面默认QJXT-01。标题直接采用接口`winch.winchName + 详情`，返回首页，“更多历史记录”进入该系统历史列表并携带设备上下文。

本页包含设备当前快照、两根钢绳当前测量、独立伺服反馈和一台设备的多运行/多周期拉力对比。它与截图中的“单次运行详情”不是同一个页面。

### 设备当前快照与测量字段

| 页面位置/文案 | 接口字段 | 含义、单位、当前展示处理 |
| --- | --- | --- |
| 页面标题 | `winch.winchName` | 使用设备实际名称；不由状态推断名称 |
| 系统编号 | `systemCode` | 当前详情所属系统 |
| 运行状态 | `winch.operatingStatus`、`winch.statusText` | 状态码决定颜色，优先显示后端文字；IDLE待机、RUNNING运行、MAINTENANCE检修、FAULT故障、OFFLINE离线 |
| 运行模式 | `currentMode` | AUTO自动、MANUAL手动；设备离线或未知值显示“暂无数据” |
| 运动方向 | `winch.direction` | UP上升、DOWN下降、STOP停止；离线/未知显示“暂无数据” |
| 当前高度 | `currentHeight` | 米，显示1位小数；非有限、负数或离线显示“暂无数据” |
| 实际速度 | `winch.speed` | 米/分钟，最多2位小数；无效或离线显示“暂无数据” |
| 设定速度 | `setSpeedMPerMinute` | 当前生效设定值，米/分钟；正数才显示；不由实际速度补齐 |
| 今日完成次数 | `todayCompletedCount` | 后端实际完成数，含恢复后完成、不含未完成和故障终止；非负安全整数显示“n 次”，未知显示“暂无数据” |
| 累计运行时长 | `runningHours` | 小时，最多1位小数；不以当前这一轮时长代替 |
| 最近维护 | `maintenanceAt` | 返回文字/时间；无值显示“暂无数据” |
| 两个钢绳卡片名称 | `sensors[].groupNo`、`metric` | 当前前端用groupNo及WINCH_TENSION/TRACTION_ROPE_TENSION生成“钢绳n-1/钢绳n-2拉力”；按metric排先后，不按数组猜测来源 |
| 钢绳状态 | `sensors[].status`、`statusText` | NORMAL、WARNING、ALARM、FAULT、OFFLINE；状态文字直接取接口，不比较阈值自行判定 |
| 钢绳读数 | `sensors[].pressure` | 原始单位t；FAULT/OFFLINE显示“—”；吨显示2位，公斤显示整数 |
| 预警 | `sensors[].alarmThreshold` | 后端配置值，随当前显示单位换算 |
| 安全保护 | `sensors[].safetyValue` | 后端当前生效配置值，随显示单位换算；标题旁“修改阈值”可编辑两路预警/保护值，判断仍由PLC执行 |
| 伺服电机拉力 | `torqueForce.value`、`torqueForce.unit` | 独立反馈；unit为t或kg，按显示单位转换；离线或缺失显示“暂无数据” |
| 数据来源 | `torqueForce.sourceName` | 缺失时用“伺服电机反馈数据” |

用于关联而非直接显示的字段还包括`winch.winchCode/groupNo/sensorCodes/directionText`、`sensors[].sensorCode/sensorName/winchCode/businessRole/position/unit`。`updatedAt`由接口提供，但本页模板没有独立“更新时间”文字。传感器`pressure`沿用既有字段名，业务含义是拉力。

图上的吨/公斤切换同时影响顶部钢绳读数、阈值、伺服读数和图表；高度和速度单位不变。顶部当前快照与历史曲线的时间上下文独立，光标移动不能改写顶部当前高度。

### 详情接口与加载过程

| 方法与路径 | 输入 | 当前主要输出 |
| --- | --- | --- |
| `GET /command-center/winch/detail` | Query：`systemCode`、`winchCode`，两者不能为空 | `CommandCenterWinchDetailData`，包含上表字段 |
| `GET /command-center/winch/tension/trend` | Query：`systemCode`、`winchCode`、`viewType=FORCE_COMPARISON`、`comparisonView=COUNT/DAY/MONTH/YEAR` | `WinchTrendUnitSetData`：分组units、带unitId的三来源series、范围和数据时间 |

进入页面、系统编号变化、设备编号变化时，先请求详情成功后再请求曲线。切换查看范围只重新请求曲线；来源、吨/公斤、运行记录选择都在已返回数据上操作，不新增查询。当前没有5秒轮询，也没有页面内通用“刷新”按钮；失败时可重新加载详情或重试曲线，浏览器刷新也会重新请求。

详情未返回时显示“正在加载动力设备数据…”。详情失败显示“动力设备数据加载失败，请重新加载。”和“重新加载”。曲线独立显示加载中、失败文字和“重试”；范围切换会清空旧曲线，避免将旧口径的数据留作新口径结果。请求序号阻止迟到旧响应覆盖新选择，离开页面后结果不再回写。

DEV详情保持固定演示快照：设备待机、停止、实际速度0、当前高度0、设定速度10m/min；两绳使用模拟末次采样，伺服独立生成。未保存自定义配置时模拟预警0.8t、安全保护1.2t；这些不是已确认生产阈值。DEV保存后只更新阈值字段，不重算模拟状态。2026-10-22演示每天20轮，其中第13未完成、第14故障终止，所以今日完成次数为18；这与显示“第20次运行”是不同概念。累计运行时长来自全部演示运行时长求和，正式接口须由后端提供真实累计值。

### 本页拉力对比筛选与表格

| 操作 | 当前行为 |
| --- | --- |
| 查看范围 | 本日次数COUNT、本日平均DAY、本月平均MONTH、本年平均YEAR |
| 拉力来源 | 两根单绳、独立伺服、钢绳总拉力；可多选，最后一个不能取消 |
| 默认值 | COUNT、SENSOR1、吨；首次成功加载COUNT时选units的第一条运行，不默认全部20次 |
| 运行记录下拉 | 仅COUNT显示；可选“全部次数”或某次运行；单选某次时图和右表均限定该分组 |
| 查看曲线下拉 | 多条曲线时出现在右侧；决定右表显示哪条曲线；只有一条时显示曲线名称 |
| 右侧上下文 | COUNT为“查看次数”，其他为“查看时段”；显示分组名称/实时运行以及rangeLabel |
| 右表列 | COUNT为“高度、拉力、速度”；平均视图为“高度、平均拉力、平均速度”；提供可滚动的完整点表，不是光标单点四来源表 |
| 表格行 | 来自对应曲线的`height/value/speed/time/quality`；行高度文字的title保留采样时间；不是前端生成等间距高度行 |
| 表格滚动 | 切曲线、单位或查看范围后回到顶部；没有每行独立接口/分页 |
| 数值格式 | 拉力t保留2位、kg整数；速度最多2位；缺失/INVALID/OFFLINE读数为“—”，不填0 |

本日次数的分组名为运行次数，允许`runState=RUNNING`显示“实时运行”，或COMPLETED显示既有名称。这里的COMPLETED是图表分组状态，不能覆盖系统历史的最终结果COMPLETED/INCOMPLETE/FAULT_TERMINATED。当前DEV将20个图表分组都标记为COMPLETED，但历史第13/14轮仍保留未完成/故障结果。

本页当前没有“导出本次数据”按钮；该按钮属于历史单次运行详情。

### 曲线接口返回字段

| 对象 | 字段 | 说明 |
| --- | --- | --- |
| 响应外层 | `code/message/data` | 统一业务响应；本文只核实前端声明，不表示真实服务已联调 |
| data | `kind` | COUNT使用BATCH，平均视图使用PERIOD |
| data | `units[]`、`series[]` | 分组与分组内各来源曲线 |
| data | `heightMin/heightMax` | 实际采样高度范围m，不直接等于设备限位或固定画布范围 |
| data | `heightLimits.min/max` | 设备实际限位m，可选，与采样范围独立 |
| data | `updatedAt`、`asOf` | 更新时间/数据快照日期时刻；不自动用浏览器当前日期替换 |
| data | `dataRange.startDate/endDate`、`demo` | 真实数据覆盖范围、是否模拟；生产范围可显示，DEV隐藏范围提示 |
| units[] | `id/name` | 分组稳定编号、名称 |
| units[] | `startAt/endAt/rangeLabel/dataThrough` | 实际范围、显示范围及数据截止时间 |
| units[] | `runState/abnormal/empty` | 图表运行状态、异常标记、是否无数据 |
| units[] | `sampleCount` | 当前定义为周期纳入的运行数，不能与点sampleCount混用；建议后续改名includedRunCount |
| series[] | `unitId`、`sourceRole` | 归属分组；sourceRole当前只有SENSOR1、SENSOR2、SERVO |
| series[] | `metric/name/sourceSensorCode/sourceSensorName/periodRole` | 业务指标角色、名称、来源身份、周期角色；伺服必须有独立来源 |
| points[] | `height/time/value/speed/direction/quality` | 高度m、采样时间/统计范围、拉力t或null、速度m/min、方向、质量 |
| points[] | `elapsedSeconds/progressPercent` | 可选经过秒数/运行进度；用于顺序或其他显示，不能凭此猜实际状态 |
| points[] | `sampleCount/min/max` | 高度聚合有效样本数、可选原始极值；当前逐点表没有独立显示min/max |

生产响应校验要求有限且递增的heightMin/heightMax、数组结构和匹配kind。COUNT要求各unit显式提供COMPLETED/RUNNING；平均视图要求包含预期周期id。校验失败会提示“正式接口尚未支持所选拉力对比口径，请接入分组及平均曲线数据”。这不是对所有点字段或真实平均计算结果的完整校验。

| 平均视图 | 正式接口所需分组 | 当前DEV演示 |
| --- | --- | --- |
| DAY | TODAY今日、YESTERDAY昨日、MONTH本月、YEAR本年 | 四组均返回 |
| MONTH | MONTH本月、PREVIOUS_MONTH上月、PREVIOUS_YEAR上年同期 | 只返回本月和上月，不补造上一年 |
| YEAR | YEAR本年、PREVIOUS_YEAR上年 | 只返回本年，不补造上一年 |

DEV数据覆盖2026-04-01至2026-10-22，205天，每天20轮；运行图普通轮每来源51点，第13轮保护回退每来源601点。COUNT共20组、每组三个独立来源。全选四种展示来源并选“全部次数”时，前三来源60条加上前端生成的20条合计，共80条；默认第一轮单来源只有1条。

### 动力详情阈值修改

动力详情的“拉力数据”标题旁新增“修改阈值”。弹窗按该设备两路实际sensorCode展示钢绳名称，分别编辑预警值alarmThreshold、安全保护值safetyValue。输入明确使用吨（t），不随外部图表切到kg而改变提交单位；页面原有读数、曲线及阈值显示仍遵循现有吨/公斤切换。

前端仅进行配置输入校验：两路来源编号不空且唯一、数值有限且大于0、各路预警值小于安全保护值。没有把图表1.4t/3t坐标范围当作设备安全上限。取消不发送请求；保存中禁止重复提交及关闭弹窗；失败保留草稿并显示错误，重新打开以当前生效快照初始化。

#### 新增接口契约

`PUT /command-center/winch/tension-thresholds`，这是前端新增声明，后端实际配置持久化、授权、PLC下发及生效确认尚需接入。

输入JSON：

```json
{
  "systemCode": "QJXT-01",
  "winchCode": "WJ-01",
  "thresholds": [
    { "sensorCode": "PS-01", "alarmThreshold": 0.8, "safetyValue": 1.2 },
    { "sensorCode": "PS-02", "alarmThreshold": 0.8, "safetyValue": 1.2 }
  ]
}
```

返回采用code/message/data，data字段：

| 字段 | 含义 |
| --- | --- |
| systemCode、winchCode | 保存回执对应的系统和动力设备；身份不匹配时前端拒绝覆盖 |
| status | APPLIED表示后端已得到PLC生效确认；PENDING表示只接受修改申请、仍待PLC生效 |
| effectiveThresholds | PLC已确认的两路生效快照，每项sensorCode/alarmThreshold/safetyValue；APPLIED必须完整返回且来源对应本次设备 |
| configurationVersion | 可选真实配置版本；前端不生成虚假的版本号或设备确认 |
| demo | DEV演示标记，真实设备响应不应使用演示成功冒充PLC确认 |

APPLIED只使用后端effectiveThresholds更新显示，不能把本次提交值直接当作最终生效值；后端实际应用值可能与申请值不同。PENDING显示“修改已提交，等待PLC生效；当前仍展示已生效阈值”，保留原快照；之后重新读取设备详情才能获得真实生效配置。保存成功不自行改变NORMAL/WARNING/ALARM等传感器状态，也不创建、结束或合并告警事件。

#### DEV范围及限制

DEV将阈值独立保存在localStorage，键包含systemCode和winchCode，不跨系统/设备串用。详情重新加载、首页初次加载及每次模拟快照会按来源编号读取保存值，仅替换alarmThreshold/safetyValue；拉力、方向、高度、平均值、设备工况及测点状态文字全部保留。未保存时原始演示值不变，损坏存储不覆盖原快照。

演示保存明确提示“演示阈值已保存”，没有向真实PLC下发。当前演示状态仍由既有场景生成，不能把改阈值后的演示状态视为现场保护逻辑验证。生产首页与详情直接采用后端当前快照，不应用浏览器演示配置；PLC负责依据生效阈值判定状态，后端负责配置流转与回执，前端负责编辑与展示。

代码：src/api/tensionThresholds.ts、src/composables/useTensionThresholdEditor.ts、src/components/TensionThresholdEditor/index.vue、src/views/power-detail/index.vue、src/views/home/composables/useHomeDashboard.ts。

<a id="page-07"></a>

## 系统历史记录页面

路由：`/command-center/history/:systemCode`。实际页面为“系统历史记录”；当前入口列出单次运行，没有日/月/年统计列表切换。

本页一条RUN代表整个系统的一轮运行，关联四台动力设备。不能将四台设备计为四次系统运行。`winchCode`查询参数只保留从动力详情返回的上下文，不用于把系统历史筛成单台运行。

### 展示区块与字段

| 页面文字/区块 | 来源字段或处理 | 含义与格式 |
| --- | --- | --- |
| 日期范围、开始/结束日期、查询 | `draftStart/draftEnd`；确认后形成`startDate/endDate` | 界面包含结束当天；请求中的endTime是结束当天次日零点，右开区间 |
| 单次运行记录，共N次 | `data.total` | 全部查询结果数，不是当前页条数 |
| 序号 | `(page-1)*10+index+1` | 查询结果的跨页显示序号，不是当日第几轮，也不是记录主键 |
| 开始时间 | `entries[].startedAt` | 按上海时区显示年月日时分秒；缺失/不合法显示— |
| 运行时间 | `durationSeconds` | 转为小时、分、秒；缺失显示暂无数据 |
| 运行结果 | `runResult/endReason/exceptionEvents/abnormalCount` | 当前前端解释为正常完成、异常完成、异常结束、结果未记录等 |
| 拉力异常次数 | `abnormalCount` | 次；当前模拟为保护事件条数，不是处理尝试次数或告警条数 |
| 启动方式 | `startMethod` | SCHEDULED→定时启动，MANUAL→人工启动，缺失→未记录 |
| 单绳最大/最小拉力（t） | `maxTension/minTension` | 本轮全系统8根单绳有效采样中的单个最大/最小值，显示三位小数；不是8根相加，不含伺服/双绳合计；表头和可见说明均已明确 |
| 查看详情 | `entries[].id` | 作为recordCode进入单次详情，保留日期、分页及来源设备上下文 |
| 导出表格 | 独立导出对话框 | 导出运行摘要，不是当前图的逐点采样；见后文 |
| 分页 | `page/total`，固定每页10条 | 翻页查询后端；加载/错误时禁用 |

日期选择只开放接口返回的`availableDates`中的真实数据日期，当前允许2026年起且不晚于上海今日的日期。默认先查询当天；拿到完整日期索引后，若未通过链接指定日期，则定位到最新有记录日。开始/结束都必须是有数据的日期，中间日期不要求每天有数据。可用日期必须是全历史索引，不能从当前10条推断。

### 接口输入输出

`GET /command-center/history/list`，由`getSystemHistoryRunsApi`调用。

| Query字段 | 当前传值/约束 |
| --- | --- |
| `systemCode` | 路由系统编号，非空 |
| `scope` | 固定RUN |
| `startTime` | 开始日期的`T00:00:00+08:00` |
| `endTime` | 界面结束日期次日的`T00:00:00+08:00` |
| `endExclusive` | true，规则为`startTime <= startedAt < endTime` |
| `includeAvailableDates` | true |
| `page/pageSize` | 正整数；列表pageSize=10；导出使用独立大小 |

响应数据：`entries:SystemHistoryEntry[]`、`total:number`、`availableDates:string[]`、可选`asOf/enabledFrom/dataRange{startDate,endDate}`。当前类型把availableDates列为可选，但页面依赖它开放日期选择，正式接口应保证返回完整索引。

生产分支直接请求接口；DEV按固定历史源筛选、按开始时间倒序及ID倒序排序后分页，并临时读取八路曲线计算GOOD采样极值。正式后端应返回摘要极值，列表不应逐条取完整曲线计算。

### 加载、错误与刷新

进入页面、点击查询、翻页、重试时请求；无定时轮询。加载文案“正在加载历史记录…”。失败显示“历史记录加载失败，请重试。”；日期索引缺失显示“可选日期加载失败，请重试。”并不开放未知日期；完全无可用日期显示“暂无可查询的历史数据”；当前范围无记录显示“所选日期范围暂无运行记录”。请求序号阻止较旧响应覆盖新查询，离开页面后忽略旧响应。

<a id="page-08"></a>

## 单次历史记录详情与新版拉力对比图

路由：`/command-center/history/:systemCode/detail/:recordCode`，页面标题“单次运行详情”。**本次截图对应此页RUN分支的`RunForceComparison`，不是动力设备详情中的`WinchForceComparison`。**

旧`DAY-/MONTH-/QUARTER-/HALF_YEAR-/YEAR-`开头的汇总详情地址在路由`beforeEnter`被重定向到单次记录列表。源码仍留有非RUN统计模板、旧模式和聚合函数，不能把它们列为当前可正常访问的页面功能；源码中“仅上行”的旧提示也位于非RUN分支，不代表新版RUN图的实际采样规则。

### 运行概况

| 页面内容 | 响应来源 | 单位和处理 |
| --- | --- | --- |
| 运行时段 | `entry.startedAt/endedAt` | 上海时间；同日结束只显示时分秒，跨日显示完整日期；缺失— |
| 运行结果 | `runResult/endReason/exceptionEvents/abnormalCount` | 当前前端映射结果中文及颜色，非法组合显示结果未记录 |
| 启动方式 | `startMethod` | 定时启动/人工启动/未记录 |
| 设定速度 | `setSpeedMPerMinute` | 米/分钟；必须是本轮启动参数，正有限数；缺失未记录 |
| 运行间隔 | `intervalMinutes` | 分钟；整小时可显示小时；缺失未记录 |
| 运行时长 | `durationSeconds` | 小时、分、秒，不用当前时钟回算旧记录 |
| 拉力异常次数 | `abnormalCount` | 次，保护事件数 |
| 单绳最大/最小拉力 | `entry.maxTension/minTension` | 本轮全系统8根单绳有效采样中的单个极值，t两位、kg整数；不是合计，不随当前设备/来源选择变化；概况已增加可见说明 |

结果字段保留COMPLETED、INCOMPLETE、FAULT_TERMINATED；结束原因保留COMPLETED、NEXT_SCHEDULE_REACHED、FAULT、MANUAL_STOP、EMERGENCY_STOP。异常恢复后完成属于异常完成；故障终止和未完成统一显示异常结束。历史急停结束原因仍合法，与已删除的旧设备急停状态枚举无关。

### 新版拉力对比图的控件与数据含义

| 区域 | 当前行为 |
| --- | --- |
| 动力设备 | 动力一号、二号、三号、四号，组号1～4；可独立多选；至少保留一台；默认动力一号 |
| 拉力来源 | 钢绳1号拉力ROPE1、钢绳2号拉力ROPE2、伺服电机拉力SERVO；可独立多选；至少保留一项；默认ROPE1 |
| 左侧曲线 | 所选设备×所选来源的实际曲线，最多12条；不把四台数据求和，不计算平均；当前界面没有钢绳总拉力按钮 |
| 单位 | 吨t/公斤kg，默认t；kg=t×1000；不请求接口重取 |
| 右侧查看曲线 | 多于一条时下拉选择“动力X号·来源”；只有一条时显示曲线名称；所列仅限当前选中设备及来源 |
| 右侧逐点表 | 当前曲线的全部符合图表坐标条件的真实运动采样；三列高度、拉力、速度；滚动查看，表头固定 |
| 导出本次数据 | 下载该记录完整响应的采样，不受当前设备、来源、单位、右表选择影响；见导出章节 |

设备与来源按钮均只改变本地展示，不再次请求。右表与曲线共用`heightPoints`筛选和顺序：保留**高度为有限数，方向为UP或DOWN**的点；RUN不纳入STOP/无方向点。若所有这些点的时间均可解析，按真实时间升序连接，否则保留接口原数组顺序。回退后同一高度再次经过的点保留多条，不按高度去重或高度排序。

因此截图右表从25m、24.5m依次下降，是该曲线下降过程的实际时间顺序，不代表组件统一按高度倒序。新文档不能再写成“只看上行”“同高度只保留一个点”或“右表只是当前悬停点”。

拉力数值：GOOD、PARTIAL且value为有限数时可显示；INVALID、OFFLINE、NULL及非有限数在曲线显示断点、表格显示—。当前可见RUN图及表格的`usableForce`未额外排除sampleCount<=0，正式接口应保证原始点质量与样本语义一致。表格速度按独立字段speed展示：质量不是INVALID/OFFLINE且speed有限则显示，允许拉力缺失而速度仍可用；单位米/分钟，最多两位且去掉末尾零。高度显示原始有限数字加米；时间不占表列，保留为高度单元格的采样时间提示。t两位小数、kg整数，数据格式化不改变原始采样。

图表横轴固定0～30m，主刻度5m；仅选单绳时纵轴0～1.4t，加入伺服时0～3t，kg同步乘1000。这些是显示范围，不是预警、保护阈值或设备物理限位；越界数据可能被当前坐标显示范围裁切，原始接口值仍需保留。

鼠标靠近实际曲线时显示该点时间、高度、速度和拉力，并把右表切换到命中曲线；空白区域的高度探针不切换曲线。下拉选择也会突出对应曲线。**当前未实现按悬停点定位/高亮/自动滚动到对应表格行**；仅在曲线或单位变化时把右表滚动到顶部。表格没有点击行反向定位图表的处理。重复高度的区分仍依赖时间和接口顺序，当前没有统一sampleKey。

### 详情接口与字段结构

`GET /command-center/history/detail`，Query：`systemCode`、`recordCode`、`yearGrouping=MONTH`。MONTH是当前仍传的固定兼容字段，不表示该RUN页面显示月统计。

| 返回结构 | 字段及含义 |
| --- | --- |
| `entry` | id、kind=RUN、name、startDate/endDate、startedAt/endedAt、durationSeconds、sequenceNo、startMethod、setSpeedMPerMinute、intervalMinutes、maxTension/minTension、statusText、runResult/endReason、exceptionEvents、各运行分类计数、abnormalCount等 |
| `sensors[]` | 八路单绳曲线；id/name/groupNo/ropeNo/points/displayMin/displayMax。ropeNo应明确为1或2；当前兼容工具也会从S1～S8编号推断 |
| `servos[]` | 四路独立伺服曲线；同样带id/name/groupNo/points/displayMin/displayMax，不用两条钢绳相加冒充 |
| `points[]` | time、height(m)、value(t或null)、speed(m/min)、direction、quality；可选progressPercent、elapsedSeconds、sampleCount、min/max |
| `heightMin/heightMax` | 实际返回的采样高度范围；新版RUN图仍使用固定横轴0～30m |
| `heightLimits` | 可选设备限位，与采样高度范围、图表轴范围分开 |

响应仍有可选alarms/timeline/children，用于遗留非RUN汇总处理；当前RUN返回不包含告警列表，页面不额外请求告警。保护事件原结构保留occurredAt、outcome、returnedAt、resumedAt、旧attemptCount；本页目前只展示异常次数，不单独展开每次保护处理过程。

进入页面、路由systemCode/recordCode变化、失败后重新加载时请求；路由变化重置默认动力一号、钢绳1号来源。加载“正在加载记录…”；失败显示接口/异常消息并提供“重新加载”；空采样图文案“本次运行暂无有效拉力采样数据”。无定时轮询。

### 当前前端处理与后端目标

当前RUN可见图不生成总拉力或平均统计；它仍做设备/来源选择、时间排序、UP/DOWN筛选、质量掩码、单位格式化。运行结果中文仍由前端解释结构化结果，列表/详情DEV极值由模拟API层计算。后端需要保存完整原始过程并直接提供本轮结果、参数快照、八路极值、明确来源/绳号、时间/方向/质量。为落实“后端业务、前端展示”，后续应明确采样展示纳入规则并提供稳定采样键；不能用旧文档中“右上四来源同高度读数＋右下均值极值表”解释此新版RUN图。

<a id="page-09"></a>

## 两种新版拉力对比图的关系与数据处理

### 新截图应归入历史单次运行详情

用户本次截图中的“动力一号/二号/三号/四号”“钢绳1号拉力/钢绳2号拉力/伺服电机拉力”“查看曲线”和“导出本次数据”，对应`history-detail/index.vue`的RUN分支及`RunForceComparison`。不是动力详情的四范围控件。

| 对照项 | 历史单次运行详情 | 动力设备详情 |
| --- | --- | --- |
| 数据范围 | 系统一轮，四台设备可选 | 一台设备，多轮或多周期可选 |
| 数据接口 | `/command-center/history/detail` | `/command-center/winch/detail`＋`/command-center/winch/tension/trend` |
| 左侧控件 | 四动力设备多选，三种来源多选 | 四查看范围，四种来源，COUNT运行记录下拉 |
| 默认 | 动力一号＋钢绳1号＋吨 | COUNT第一条运行＋SENSOR1＋吨 |
| 来源按钮 | ROPE1、ROPE2、SERVO，无总拉力按钮 | SENSOR1、SENSOR2、SERVO、SUM |
| 右侧 | 指定曲线的高度/拉力/速度完整点表 | 指定分组曲线的高度/拉力/速度，平均视图换成平均列名 |
| 图表与右表联动 | 鼠标/点击真正命中曲线时切换右表对应曲线；下拉选择也突出对应曲线 | 当前WinchForceComparison未绑定activeSeriesId联动，右表通过下拉选择；不能声称已与历史页同等联动 |
| 导出 | 导出本次系统全部采样，范围不随可见筛选缩小 | 本页无导出按钮 |
| 旧右上单点表/右下统计表 | 已替换为完整点表 | 已替换为完整点表 |

截图本身没有展示运行编号/日期，因此不能把它继续解释为旧截图的“第9次运行、22.5m、总拉力1.21t”。截图表格25m、24.5m等从高到低，也不能推断为全局按高度倒排：当前单次行按实际采样时间排序，下降过程自然从高到低；上升过程自然从低到高；保护回退保留重复高度。

### 当前已生效的点序、质量和绘图规则

- 历史单次`heightPoints`只取有限高度且direction为UP/DOWN的运动采样；保留回退和再次经过同高度的点，时间全部有效时按实际时间排序，否则沿用原顺序。STOP/未知方向点当前不进入这张运动曲线/逐点表；导出范围应另看导出规则。
- 动力COUNT按有效采样时间排序，时间不完整时尝试elapsedSeconds，全都不可用时保留原顺序；它没有历史页同样的方向筛选。动力平均视图按高度排序。文档不应将两条路径写成完全相同的筛选规则。
- 曲线及表格拉力只使用有限数值且quality不为INVALID/OFFLINE的点；GOOD、PARTIAL可显示。无效点不接线，不能把缺失值当0。当前表格速度独立检查有限值及quality，但没有独立速度质量字段。
- t→kg乘1000，只改变显示；原始数据不覆盖为格式化字符串。两种新图横轴固定0～30m，实际点通常0～25m；单绳纵轴0～1.4t，选伺服后0～3t；动力页选SUM也使用0～3t。画布范围不是阈值或设备量程。
- TrendChart对曲线用平滑连线；鼠标/点击靠近真实线段时选择邻近实际端点展示提示，不把插值位置冒充真实采样。空白高度探针可以定位已有采样高度，但没有采样的区间不外推。
- 同高度联动标记当前按真实时间最近的该高度点选择；动力图还限定相同hoverGroupId。尚未使用后端稳定sampleKey，存在重复高度/异步来源时，应按明确帧标识继续规范，不能宣称所有联动点已严格同步。
- 历史新图无旧均值/最大/最小双表。单次概况中的最大/最小仍读取`entry.maxTension/minTension`，定义为系统八路钢绳的本轮有效原始极值，不包含独立伺服，不等于当前可见曲线峰值。

### 仍在前端执行的业务与后端目标

当前动力页保留SUM按钮。`sensorSumSeries`仍在浏览器中找到SENSOR1和SENSOR2，按相同height、time、direction、sampleCount严格配对，再将未格式化值相加到6位小数；缺失/无效返回null，任一PARTIAL则合计PARTIAL。其速度及其他字段沿用第一来源点，并未独立验证两路速度。

这是当前实现事实，不能因为右侧统计表已经删除，就写成总拉力已迁移后端。另一方面，旧`winchForceInspection`已经移除；历史工具中的`buildRunForceInspection`以及多种合计/平均helper虽仍存在，但当前RUN页的可选按钮仅ROPE1、ROPE2、SERVO，不会触发被隐藏的总拉力/四台平均模式。普通路由也已将旧周期详情链接重定向回运行列表，不能把遗留统计分支继续作为当前普通页面功能介绍。

建议后端负责来源对齐、总拉力、周期平均、有效样本数量与原始极值；前端保留来源显隐、分组选择、表格索引、格式化和绘图。若后端暂未完成，不在前端“补一份生产平均”来假装接口具备能力。当前模拟周期平均在DEV数据函数生成，正式请求不走模拟生成公式。

上一份文档中的`ROPE_TOTAL`、`sampleKey`、`statistics`、`ropeExtrema`、`defaultUnitId`仍是建议的目标契约，当前`WinchTrendUnitSetData`没有这些字段；不能保留其示例后又称已实现。新版页面取消均值/极值表后，应按页面真正需要精简接口：历史图以真实三来源全点和稳定帧标识为主；动力页若保留SUM则由后端明确返回合计曲线。是否保留单次statistics可由列表/概况复用需求决定，不必为了旧UI继续强制返回整套旧表数据。

代码核对位置：`src/views/power-detail/index.vue:1、83、127、155`；`src/api/commandCenterPages.ts:32、372、405、409`；`src/utils/powerDetailTrend.ts:7、26、47`；`src/utils/powerDetailPresentation.ts:16、42`；`src/components/WinchForceComparison/index.vue:1、29`；`src/components/RunForceComparison/index.vue:1、28`；`src/utils/systemHistoryTrend.ts:85、254、266`；`src/components/TrendChart/index.vue:184、251、479`。这些定位仅用于维护核对，不属于后端业务字段。

<a id="page-10"></a>

## 新截图的逐项核对与实际数据示例

> 原文图片引用：assets/历史单次运行拉力对比图_20261025.png。本次附件未包含该图片文件，仓库保留此引用说明，不补造截图。

截图显示历史单次RUN页，可选动力一号至四号，来源为两根单绳与独立伺服，右侧为所选曲线的完整点表。图上的竖向参考线及圆点是高度探针，**右表不会因此缩成一行**。截图选中了两根钢绳，因此有两条曲线；页面首次默认仍只选钢绳1号。

截图没有露出运行编号，不将它强行认定为某条真实记录。为校对显示，读取当前DEV的`RUN-20261022-20`，选动力一号钢绳1，得到下面的实际数据。此样本与截图前六行一致，但仅是当前演示核验，不表示已读到生产设备。

| 采样时间（上海） | 高度m | 钢绳1拉力t | 实际速度m/min |
| --- | ---: | ---: | ---: |
| 2026-10-22 17:50:00 | 25 | 0.75 | 10 |
| 2026-10-22 17:50:03 | 24.5 | 0.74 | 10 |
| 2026-10-22 17:50:06 | 24 | 0.73 | 10 |
| 2026-10-22 17:50:09 | 23.5 | 0.72 | 10 |
| 2026-10-22 17:50:12 | 23 | 0.71 | 10 |
| 2026-10-22 17:50:15 | 22.5 | 0.71 | 10 |

该演示轮每来源51点，完整响应有8路单绳和4路伺服；整次CSV共204个设备采样数据行，即4台×51帧。当前右表只有选中那条曲线的51行。第13轮保护过程点数不同，不能把204写成每条记录固定导出行数。

请求示意：

```http
GET /api/command-center/history/detail?systemCode=QJXT-01&recordCode=RUN-20261022-20&yearGrouping=MONTH
Authorization: Bearer <token>
```

以下JSON为该真实DEV样本的**响应字段节选**，只保留一个钢绳点，不是可用于完整绘图的全部响应。正式后端需返回本轮实际存在的全部来源/点；遗漏数据不能由前端填造。

```json
{
  "code": 200,
  "message": "数据获取成功",
  "data": {
    "entry": {
      "id": "RUN-20261022-20",
      "kind": "RUN",
      "name": "第20次运行",
      "startedAt": "2026-10-22 17:50:00",
      "endedAt": "2026-10-22 17:52:30",
      "durationSeconds": 150,
      "setSpeedMPerMinute": 10,
      "intervalMinutes": 30,
      "runResult": "COMPLETED",
      "endReason": "COMPLETED",
      "maxTension": 0.75,
      "minTension": 0.295
    },
    "sensors": [
      {
        "id": "S1",
        "name": "S1",
        "groupNo": 1,
        "ropeNo": 1,
        "displayMin": 0.1,
        "displayMax": 1.3,
        "points": [
          {
            "time": "2026-10-22 17:50:00",
            "height": 25,
            "speed": 10,
            "direction": "DOWN",
            "value": 0.75,
            "quality": "GOOD"
          }
        ]
      }
    ]
  }
}
```

参数保存请求示例为当前字段结构，数值取演示默认值，不代表已向设备下发：

```json
{
  "systemCode": "QJXT-01",
  "startTime": "08:20",
  "intervalMinutes": 30,
  "speedMPerMinute": 10
}
```

后端响应`data`返回实际保存后的startTime、intervalMinutes、speedMPerMinute。当前没有独立生效版本、执行回执或生效时间，不能把请求成功解释成设备已按新计划运行。

<a id="page-11"></a>

## 历史记录导出与单次采样导出

当前两种导出都在浏览器生成CSV，无现成后端导出任务/下载接口。CSV为UTF-8带BOM、CRLF行尾，文本统一引号和转义；可能被表格当作公式的外部字符串加文本前缀。浏览器提交下载不等于能确认用户已保存文件。

### 历史列表“导出表格”

弹窗选择自然日、月、季度、半年、年，再选具体周期；可选周期由真实availableDates产生，不能把数据不存在的年份开放为有数据。周期边界仍是上海时间左闭右开。导出的不是当前列表页或当前筛选范围的固定快照，而是弹窗选择的新周期内全部系统RUN摘要。

打开/更换周期先以history/list的page=1、pageSize=1查询记录总数。确认导出后按每页200条、每批最多1000条准备CSV。用户下载本批后再准备下一批；进度显示已提交下载条数/总条数。开始后锁定周期，支持停止、关闭、重试本批、重新选择；请求可取消。

每批校验总数、预期页条数、记录编号不空不重复；下一批还复核前一批末页编号，发现分页漂移提示重新查询后导出。这些是当前客户端一致性检查，不等于后端snapshotId。后端应支持固定数据版本的导出，避免总数不变的补数/修改仍影响结果。

CSV列共16项：序号、系统编号、运行记录编号、开始时间、结束时间、运行时长（秒）、运行时长、运行结果、结束原因、拉力异常（次）、历史处理次数（次）、启动方式、设定速度（m/min）、运行间隔（分钟）、单绳最大拉力（t）、单绳最小拉力（t）。历史处理次数仅在每个事件都有attemptCount时求和；未知不冒充0。

文件名：`{systemCode}_运行记录_{开始日}_{实际包含的结束日}_第{批次}批共{总批数}批.csv`。没有记录、总数加载失败、准备未完成时不能下载；失败不会撤回已经提交下载的前批。

### 单次详情“导出本次数据”

无需再次请求接口；取当前完整SystemHistoryDetail响应。包含全部sensors与servos，即接口返回的四台设备八路单绳和四路伺服，不仅是按钮选择的曲线；**不执行图表UP/DOWN筛选**，因此响应中STOP、无方向、无高度等点也会保留行。固定基础单位t，不随界面kg切换改变导出单位。

CSV25列：系统编号、运行记录编号、动力设备、设备组号、采样时间、高度（m）、运行方向、钢绳1拉力（t）、钢绳1质量、钢绳1速度（m/min）、钢绳1来源编号、钢绳2拉力（t）、钢绳2质量、钢绳2速度（m/min）、钢绳2来源编号、钢绳总拉力（t）、合计质量、电机拉力（t）、电机质量、电机速度（m/min）、电机来源编号、有效样本数、未识别来源编号、未识别来源采样值（t）、未识别来源质量。

导出当前仍有前端业务处理：按相同设备组、时间字符串、高度、方向、sampleCount建桶；只有两条单绳各恰好一个来源且均有效才能相加，保留六位结果；有重复来源歧义时保留原始点但不凑出合计。GOOD/PARTIAL有限值且sampleCount缺失或>0才输出拉力，否则数值留空、质量保留；缺失来源写“缺失”，合计不满足条件写“未配对”。速度字段单独保留有限值，没有按拉力质量全部抹除。未识别绳号的来源进入独立列，不静默丢弃。

文件名：`{systemCode}_{recordCode}_拉力采样.csv`，非法文件名字符替换为下划线。按时间字符串、设备组和高度排序；目前不是稳定sampleKey或统一时间戳配对。后端目标是返回已对齐的导出帧及派生合计，前端不再猜测配对、计算总值。

<a id="page-12"></a>

## 数据归档页面

路由：`/command-center/archive/:systemCode`；标题“数据归档”，副标题“按动力设备、指标和比较方式查看历史数据”。这是历史查询分析视图，不代表点击后把数据写入归档库。本页没有导出按钮。

### 筛选与展示区块

| 区块/筛选 | 当前选项与字段 | 触发方式 |
| --- | --- | --- |
| 选择动力设备 | WJ-01～WJ-04，显示动力一号设备至四号设备 | 改变后请求归档接口 |
| 分析指标 | TENSION拉力、SPEED速度 | 改变后请求；类型仍有ALARM但当前界面不开放该选项 |
| 拉力项 | WINCH_TENSION传感器1号拉力、TRACTION_ROPE_TENSION传感器2号拉力 | 仅拉力指标显示，改变后请求 |
| 统计周期 | WEEK周、MONTH月、QUARTER季度、YEAR年 | 改变后请求；不自动重置日期输入 |
| 对比方式 | SELECTED_PERIODS自选区间、PREVIOUS_RUN本条vs上次、HISTORY_AVERAGE本条vs历史、PREVIOUS_PERIOD环比、YEAR_ON_YEAR同比 | 生产声明五种，DEV隐藏同比，改变后请求 |
| 数据变化图 | 左图日期范围，查询按钮，拉力单位AUTO/T/KG | 日期修改后点查询请求；单位仅改变本地图表 |
| 数据对比图 | 自选方式显示当前区间/对比区间；单次方式显示本条记录下拉；环比/同比显示自动范围说明 | 选择记录或日期后点比较请求；单位只改本图 |
| 运行进度说明 | 单次比较横轴0%～100%对齐 | 来自comparisonAxis=PROGRESS；其他TIME按日期/月份标签 |
| 统计说明条 | 当前范围平均/最大/最小、对比变化率、相关告警链接 | 平均/极值来自summary；相关告警跳/cloud/alarms并带systemCode/deviceCode/view=history |

默认动力一号设备、拉力、传感器1号、周、本条vs上次、左右图自动单位。DEV初始结束日2026-10-22，初始7天范围；生产初始为当前日向前6天。DEV日期限制2026-04-01～2026-10-22并显示模拟覆盖文案；生产禁止未来日期。

初始化先查询history/list，范围左图当前区间、page=1、pageSize=20，取响应首批记录ID作为记录下拉；当前范围没记录时可定位availableDates最新日再查。下拉不是完整历史检索，不包含所有页。选首条后请求归档接口。

### 归档接口输入

`GET /command-center/archive`。当前直接发送：`systemCode`、`winchCode`、`metric`、`tensionMetric`、`period`、`comparisonType`、`recordCode`、`changeStart/changeEnd`、`compareStart/compareEnd`。

需要如实记录当前不足：页面有两组对比日期，但当前请求把`compareStart`取“当前区间开始”，`compareEnd`取“对比区间结束”；没有完整四个边界。DEV非单次当前序列也直接使用左图changePoints，不按右图当前区间独立生成。不能宣称这两组日期已被完整独立传给后端。正式契约需要分别表达左图范围、对比当前范围、对比参照范围，并明确环比/同比的后端派生规则。

### 归档响应字段

| 字段 | 含义及当前使用 |
| --- | --- |
| systemCode/winchCode/metric/period/comparisonType | 查询身份与口径 |
| comparisonTitle/comparisonAxis | 比较说明和TIME/PROGRESS横轴类型 |
| historySampleCount | 可选历史运行数；当前图例字段缺失或0时回退显示30条，属于待清理的显示猜测，正式应直接返实际数 |
| changePoints[] | 左图点，字段label、tension(t)、speed(m/min)、normalAlarms、seriousAlarms |
| comparisonCurrent[]/comparisonPrevious[] | 当前与参照序列，同ArchivePoint结构；后端应负责对齐标签和样本意义，前端当前按数组索引绘图 |
| summary.average/maximum/minimum | 左图changePoints对应指标的汇总，底部统一t或m/min显示两位；不是随右图单位一起变成kg |
| summary.changeRate | 右图当前与参照均值的百分比变化；有参照显示正负号，没有参照显示— |
| summary.normalAlarmCount/seriousAlarmCount | 响应有但当前统计条未直接展示；相关告警用链接查看 |

当前自动拉力单位按该图数据极差≤0.1t选择kg，否则t；左右两图独立。这是显示规则，不是告警阈值。kg图显示1位，t图2位；底部摘要仍t单位。速度不参与力值单位切换。

DEV通过固定样本生成日期/月度平均：周/月按日点，季/年按月份点；单次比较按固定进度网格。本条vs上次直接取前一批，历史平均取此前批次，没有按真实完成条件筛选“有效记录”；环比也没有因切换按钮就保证自动生成正确上一周期。这些是演示数据规则，不是已验证的生产业务口径。DEV空组部分返回0，真实接口应明确空值，不用0冒充采样。

### 刷新、失败与职责

进入初始化、设备/指标/拉力项/周期/比较方式变化、点击查询或比较、重试时请求，无自动轮询。记录下拉变化不自动请求，需点击比较。错误显示“归档数据加载失败，请重试”或“历史运行记录加载失败，请重试”；无参照显示“所选范围暂无可用的对比数据”；两图分别有当前范围暂无归档数据、当前比较条件暂无归档数据文案。当前缺少统一加载遮罩和完整前置日期校验。

生产分支直接接收归档响应，均值、极值、变化率应由后端计算。前端仍负责选项、单位显示和图表映射；不能把DEV生成器计算等同于真实后端已完成。按“前端简单提醒、后端业务计算”目标，需补齐三套日期范围契约、实际参照记录/运行数、空值、数据版本、有效运行条件，以及稳定对齐后的曲线数据。

### 本节源码核对位置

- 路由：`src/router/index.ts:70`附近。
- 列表：`src/views/history-list/index.vue`；接口：`src/api/systemHistory.ts:20`。
- 详情概况、选择与导出入口：`src/views/history-detail/index.vue`。
- 新版图及右侧滚动表：`src/components/RunForceComparison/index.vue`；样本排序/过滤、曲线/表生成：`src/utils/systemHistoryTrend.ts:85、254、266`。
- 鼠标命中与选中曲线：`src/components/TrendChart/index.vue:184、480`。
- 运行摘要与采样CSV：`src/utils/historyExport.ts`；批次：`src/utils/historyExportBatch.ts`；周期：`src/utils/historyExportPeriod.ts`；弹窗：`src/components/HistoryExportDialog/index.vue`。
- 归档页与真实发送参数：`src/views/data-archive/index.vue:90`附近；DEV生成器与生产请求：`src/api/commandCenterPages.ts:72～159`。

<a id="page-13"></a>

## 告警中心

### 分工与事件含义

**PLC判断拉力状态并上报普通/严重信号；后端按一次连续过程维护、保存事件；前端按同一alarmCode更新显示。** 拉力采样用于首页数值和曲线，不能每收到一个采样就新增告警，页面刷新也不增加事件次数。

一次普通事件A持续或升级为严重，仍更新A；PLC给出本次越限结束信号后，A记录endedAt；后续重新开始才产生事件B。endedAt表示现场过程结束，不代表工人进行了平台处理。页面只有查看、关闭、查询和筛选，没有确认、处理、恢复或消警按钮。

PLC有事件编号时由后端保留并规范命名空间；没有编号时，后端依据PLC开始/状态变化/结束信号维护编号，不能重新比较拉力值判定故障。尚未确认的现场协议、后端状态机与数据库不在本前端仓库中，不能宣称本次已接通真实PLC。

### 页面内容与交互

路由`/cloud/alarms`，默认进入“最新告警”；另一标签为“历史告警”。只有普通、严重两种等级复选框。表格列为发生时间、等级、告警内容、所属设备、操作；时间统一按上海时区显示年月日时分秒，原始时间保留为悬浮提示。所属设备显示动力设备名称或现场设备，不展开传感器通道/编码器等内部部件。错误内容直接展示后端传来的PLC原文，不根据数值、阈值或旧恢复状态重新编写错误。

| 区域/操作 | 当前实现 |
| --- | --- |
| 最新告警 | 独立读取recent实时/近期集合；包含后端指定的跨日持续事件，不请求TODAY，不从历史补旧数据 |
| 历史告警 | 后端按日期、等级、动力设备分页查询；当前每页10条 |
| 等级 | 普通/严重，默认都选；历史等级变化重新查询；全部取消立即为空，不把空参数误当查询全部 |
| 日期 | 仅历史标签显示今日、近7天、近30天、自定义；默认近30天；自定义包含首尾，最多366天 |
| 设备 | 可带deviceCode上下文及取消设备筛选；最新在完整系统集合中显示该设备，历史提交deviceCode |
| 查看详情 | 只读弹窗；同一事件发生版本更新，已打开详情也采用已知较新版本 |
| 刷新 | 最新刷新共享快照，历史刷新当前查询；不会向后端创建事件 |
| 侧栏红点 | 严格消费最新接口hasAttention；不从拉力值、访问次数或前端已读状态推断；进入页面不清除 |

最新集合与侧栏由共享状态统一读取，多个组件不会各启动一套相同轮询。初次订阅即查询，每15秒在页面可见时读取完整最新快照；离开最后一个使用组件时停止。切系统或退出时清理订阅，旧系统迟到响应不能覆盖新系统。

最新第一页可随共享快照更新；查看后续页不主动插入新记录打乱当前页，手动刷新再更新列表。详情仍按编号采用已知较新记录。切标签/系统/设备时关闭弹窗并重新查询。历史翻页提交页码，不在一页结果上假造全量total。

### 独立最新查询

`GET /command-center/alarms/latest`，Query：systemCode必传，deviceCode可选。前端共享轮询读取完整系统集合，避免用设备或等级筛选后的结果决定全系统红点。

| data字段 | 含义 |
| --- | --- |
| systemCode | 所属系统，必须与请求一致 |
| asOf | 服务端数据截止时间/统计日期；日期选择按上海业务日期解释，UTC时间不能直接截取前10位 |
| recent[] | 后端独立提供的完整实时/近期事件集合，不能仅筛今日发生事件 |
| hasAttention | 后端提供的全系统现场提醒标志；前端不按严重等级或数值重算 |
| demo? | 演示标记 |

### 历史分页查询

`GET /command-center/alarms/history`。Query：systemCode、range=TODAY/7D/30D/CUSTOM、可选deviceCode、levels逗号分隔NORMAL/SERIOUS、page、pageSize。CUSTOM时增加startDate、endDate。普通/严重筛选及分页由后端执行，不能先只取一页再按前端过滤重新解释total。

返回data包含systemCode、asOf、historyRange{startDate,endDate}、list[]、total、page、pageSize、可选demo。CUSTOM必须回显一致范围，页面校验系统、分页和重复事件；没有自动回退到旧dashboard接口。

### 事件字段与更新规则

| 字段 | 含义/当前使用 |
| --- | --- |
| alarmCode | 同一连续过程的稳定编号，缺失时拒绝按临时编号展示；同设备再次越限使用新编号 |
| systemCode、deviceCode、deviceName? | 系统、所属动力设备及其名称；内部通道编号只作后端定位，不在本页展开 |
| source? | PLC现场事件，或后端连接监测提供的PLATFORM事件；HTTP请求失败不创建PLC离线事件 |
| runCode? | 关联运行轮次，非运行期间/平台通信事件可以为空；跨轮持续事件不能因换轮次自动新建编号 |
| level、content、type | PLC/后端提供的等级、错误原文和类型；SERIOUS严重，其余旧普通等级兼容显示普通 |
| occurredAt、updatedAt?、endedAt? | 过程开始、最后更新、PLC结束时刻；不代表工人人工处理 |
| eventState? | ACTIVE / ENDED，由后端维护；本页不按恢复文字猜状态 |
| revision? | 同一事件递增版本；优先选择较新版本，不按“严重优先”覆盖较新的普通信号 |
| triggerValue?、peakValue?、peakAt?、unit? | 触发拉力、过程最高拉力、峰值时刻、t/kg；未知不以触发值补造峰值 |
| direction?、occurredHeight? | 发生方向UP/DOWN/STOP、发生高度m；后端记录的现场上下文 |
| thresholdKind?、threshold? | 发生时预警/保护类别与阈值快照，不读取当前阈值覆盖历史事件 |

当前共享类型还兼容sensorCode、sourceGroupNo/sourceMetric、旧state/handling/recoveredAt，以承接已有演示源和遗留历史代码；它们不再作为告警页处理结果列或人工操作依据。新生产契约应明确保证alarmCode、systemCode、deviceCode、level、content、occurredAt、updatedAt、revision及过程状态等关键字段。

同一完整快照中alarmCode重复时只保留较新版本；已见记录面对旧版本不会倒退。只有本次完整快照中的成员留在最新集合，不能把过期历史重新拼回；后端必须把尚在持续的事件保留在最新集合。最高拉力、等级、结束条件由后端/PLC提供，前端仅进行身份和版本选择。

### 加载、失败、演示与后端限制

首次进入加载表格；无最新数据提供“查看历史告警”，历史范围为空有单独提示。更新失败保留上次记录，提示当前显示旧数据，不伪造PLC故障；切系统/上下文时不保留上一系统内容。自定义日期非法、未来日期或超过366天时阻止提交。

DEV新接口复用固定演示事件补充过程元数据，编号在重复查询时不变化；近期窗口演示为末日及前一业务日并保留持续事件，而不是TODAY。旧样本没有可靠过程峰值/发生高度时保持缺失，不凭采样曲线推断。真实后端还需实现这两个新查询、事件状态机、PLC原始信号接入和持久化；模拟通过不等于真实事件已准确合并。

旧`/command-center/alarm-dashboard`与CommandCenterAlarmPageData只供未启用的历史统计代码及DEV固定源复用。其activeCount、seriousActiveCount、todayCount、todayRecoveredCount、recoveredCount、trend、categoryCounts等旧聚合字段不再是本页可见统计卡或正式新接口的必需数据。

源码：`src/views/alarm/index.vue`、`src/api/alarmEvents.ts`、`src/utils/alarmEvents.ts`、`src/utils/alarmFeed.ts`、`src/store/modules/alarm.ts`、`src/utils/alarmPresentation.ts`、`src/layouts/MainLayout.vue`。

<a id="page-14"></a>

## 设置参数

### 路由、可见区块与字段

路由`/cloud/parameter-settings`，系统上下文取query的systemCode，缺省QJXT-01。页面标题“运行参数设置”，说明“各项参数可分别使用默认参数或自定义数值。”，三个卡片为“启动时间”“运行间隔”“运行速度”，各自提供“默认参数/自定义”。底部“设置说明”当前文字为“设备初始位置在底部，设备完成一次上升和下降，计为一次清理”，操作按钮为“恢复默认”“保存参数”。

| 字段 | 类型/单位/当前默认值 | 输入与显示规则 |
| --- | --- | --- |
| `startTime` | string，HH:mm；默认08:20 | 自定义通过24小时制小时/分钟滚轮选择，小时0～23、分钟0～59；页面保存值不含日期和时区 |
| `intervalMinutes` | number，分钟；默认30 | 最小30分钟，必须安全整数且以30分钟递增；页面转换成天/小时/分钟文字，接口仍提交总分钟数 |
| `speedMPerMinute` | number，m/min；默认10 | 自定义1～10的整数，不能为0；页面显示最低/最高速度 |

当前默认值和合法范围来自前端`parameterDefaults/parameterLimits`常量，不是GET额外返回的后台默认配置。默认/自定义是本地交互模式，不是独立后端字段：读取值等于前端默认值则展示默认模式，否则自定义。

运行间隔选择弹窗提供常用值30分钟、1/2/4/8/12小时、1/2/3/4天。自定义由0～7天、0～23小时组合，不能组合成0；30分钟由常用按钮选择。弹窗编辑草稿，取消不改变表单，确定只更新待保存表单。既有合法半小时值可以保留；界面自定义组合当前以整小时为单位，不代表接口只允许整小时。

### 接口及保存回执

| 接口 | 输入 | 返回 |
| --- | --- | --- |
| `GET /command-center/parameter-settings` | Query：`systemCode` | `data`包含上述三项已保存参数 |
| `PUT /command-center/parameter-settings` | JSON：`systemCode`、`startTime`、`intervalMinutes`、`speedMPerMinute` | `data`为后端实际保存后的三项参数快照 |

保存前校验当前表单，首次错误用弹窗说明并聚焦对应选择器。保存中禁用相关修改和重复提交。成功使用接口返回值更新表单，打开“参数保存成功”弹窗；“本次保存参数”展示独立保存快照，含启动时间、运行间隔、运行速度，不能随表单后续编辑变化。

“恢复默认”先弹出“恢复默认参数”确认，确认后只改待保存表单；仍需点击“保存参数”才发送PUT。选择单个卡片默认参数也不立即下发设备。

### 加载、失败和前后端职责

组件挂载时读取参数，点“重新加载”可重试；当前没有监听同一路由query变动后重新读取的watch，也没有自动刷新。加载失败提示“参数加载失败，当前显示的不是设备已保存的参数。”，同时禁用恢复默认和保存，不能把本地初始默认值当成设备已保存值。保存失败弹窗保留表单供修正或重试；旧配置若不符合新范围，会原样显示并标错，不静默改成默认值。

DEV读写`localStorage['smartwash.parameter-settings.demo.v1']`，该键不按系统隔离；模拟保存不等于修改真实设备计划，也不用于回填历史启动参数。生产由后端持久化、校验、授权，并负责参数何时生效、计划调度、冲突处理；当前返回体尚无生效时间、配置版本或设备执行确认字段。

代码定位：`src/views/parameter-settings/index.vue`、`src/api/parameterSettings.ts`、`src/components/ParameterWheelPicker/index.vue`、`src/components/ParameterIntervalPicker/index.vue`、`src/utils/parameterInterval.ts`、`src/config/demo.ts`。

<a id="page-15"></a>

## 用户管理

### 当前页面范围与字段

路由`/cloud/user-management`。当前是只读用户列表，包含“关键词”搜索框（“请输入账号 / 姓名 / 角色”）、“查询”“重置”、“用户列表”、匹配总人数和分页。没有新增、修改、删除、启停、重置密码或角色授权按钮，也没有相应写接口。

| 字段 | 页面列/用途 | 空值与格式 |
| --- | --- | --- |
| 本地序号 | 序号 | `(page-1)×10 + index + 1`，不是用户ID |
| `id` | 行唯一标识，number | 当前不作为表格列展示 |
| `username` | 用户账号 | 直接显示 |
| `nickname` | 姓名 | 空字符串显示“—” |
| `roles[]` | 角色 | ADMIN→管理员、OPERATOR→操作员、VIEWER→查看员；未知编码原样显示，多角色用“、”连接；空数组显示“—” |
| `lastLoginAt` | 最近登录时间 | null明确表示“从未登录”；缺失或空字符串显示“—”，两种情况不能混为一谈 |
| `createdAt` | 创建时间 | 空值显示“—”；时间字符串当前直接展示，无独立前端时区转换 |

### 查询与响应

生产声明`GET /users`，Query：`keyword`（去首尾空白后为空则不传）、`page`（从1开始）、`pageSize=10`。返回`data: { list, total, page, pageSize }`；list包含表中用户字段。该路径在API源码仍标注待后端确认，不能写成已联调。

输入文字不会逐字请求；提交查询/按搜索表单回车才将关键词作为已应用条件并重置至第1页。重置清空关键词后重新查询。翻页发新请求；返回总数使当前页越界时回到合法末页再请求。旧请求/离开页后的响应不会覆盖新状态。

### 加载、空结果、失败及责任

进入页加载，期间显示“正在加载用户列表…”，查询、重置、分页受加载状态约束。空结果为“未查询到符合条件的用户”“请更换关键词后重试”。失败为“用户列表加载失败”，提供“重新加载”，清空列表和总数，禁用分页；没有自动轮询。

DEV有24位用户（1管理员、14操作员、9查看员），在API演示层按账号、姓名、角色编码/中文角色包含匹配后分页。生产由后端执行授权、关键词匹配、排序和分页，前端不拿一页反算全量人数。当前菜单未按角色隐藏，后端必须限制该接口的访问权限。

用户页局部字号：正文16px、表头15px、账号17px、列表标题22px；宽表最小1020px并横向滚动，长账号/姓名/角色允许换行。不是所有页面都机械套用首页的大字号。

代码定位：`src/views/user-management/index.vue`、`src/api/userManagement.ts`、`src/config/pagination.ts`、`docs/用户列表接口约定.md`。

<a id="page-16"></a>

## 模型管理

路由`/cloud/model-management`。当前展示静态模型卡片及待开放状态，没有模型查询或增删改接口。

| 区域/字段 | 当前内容及含义 |
| --- | --- |
| 模型概览 | 模型总数1、已启用0、待开放1、分析模型1；由本地models数组计算，不是后端统计 |
| `id` | scraping-analysis，静态卡片标识 |
| `name` | 清痂工艺分析模型 |
| `category` | 工艺分析；用于分类标签与“模型类型” |
| `status` | PENDING，页面文案“待开放” |
| `purpose` | 分析模型，用于“用途”与概览计数 |
| 序号 | 数组位置加1后补两位，显示01；不是模型数据库版本 |

“查看详情”仅打开本地“模型详情”弹窗，显示选中模型名称与“待开放”，点击“关闭”收起，不发请求。“添加新模型”按钮disabled，标题和辅助文字均为“待开放”；不能写成已支持模型上传、训练、启用、推理或配置版本管理。

当前页面没有异步加载/空数据/请求失败流程，因为未接后端。未来若开放，应由后端提供模型清单、状态和执行能力；本轮只如实记录现有展示。概览数字字号34px、模型名22px、模型事实正文17px；窄容器逐步改为单列。

代码定位：`src/views/model-management/index.vue`。

<a id="page-17"></a>

## 历史巡检与数据分析占位页

| 页面 | 路由 | 当前可见内容 | 当前接口/交互 |
| --- | --- | --- | --- |
| 历史巡检 | `/cloud/history-inspection` | 标题“历史巡检”，提示“该功能正在开发” | 统一ModulePlaceholder，无业务数据、筛选、历史任务或巡检报告接口 |
| 数据分析 | `/cloud/data-analysis` | 标题“数据分析”，提示“该功能正在开发” | 同一占位组件，无实际分析图表、模型推理、统计接口 |

两路由均标记`developing: true`；占位组件此时不显示额外description段落。登录守卫仍生效，但不能因为侧栏有入口就推导其业务功能已完成。历史巡检占位页与当前真正的“系统历史记录/历史记录详情”是不同页面，应分别说明。

代码定位：`src/views/history-inspection/index.vue`、`src/views/data-analysis/index.vue`、`src/components/ModulePlaceholder/index.vue`、`src/router/index.ts`。

<a id="page-18"></a>

## 历史运行头的完整字段补充

以下补齐列表/详情共享的`SystemHistoryEntry`，避免将“当前没显示”误读为“接口已删除”。`?`表示当前TypeScript允许缺省；正式生产必返项仍应按业务契约落实，不能由页面猜测。

| 字段 | 类型与含义 | 当前用途 |
| --- | --- | --- |
| id、kind、name | string、RUN/旧统计kind、string | 稳定记录编号、记录类型、名称；当前正常入口为RUN |
| startDate、endDate | string，YYYY-MM-DD | 记录覆盖日期；与范围查询的右开结束时间区分 |
| startedAt?、endedAt? | string，实际起止时间 | 列表、详情、CSV |
| durationSeconds? | number，秒 | 运行经过时长，格式化中文及原始秒导出 |
| setSpeedMPerMinute?、intervalMinutes? | number，m/min与分钟 | 本轮启动参数；不取当前设置回填 |
| maxTension?、minTension? | number，t | 本轮系统八路单绳有效原始采样极值 |
| startMethod?、sequenceNo? | SCHEDULED/MANUAL、number | 启动来源、当日第几次；不替代记录ID |
| statusText | string | 后端文字字段；当前结果标签仍经结构化字段解释，未直接全面采用它 |
| runResult?、endReason? | 结构化结果与原因枚举 | 历史结果及CSV原因，枚举见前文 |
| exceptionEvents? | SystemHistoryExceptionEvent[] | 缺失表示没有完整处理信息，空数组才明确没有事件 |
| runCount | number，次 | RUN通常为1；旧周期为纳入运行数 |
| completedCount | number，次 | 正常完成＋异常恢复后完成 |
| normalCompletedCount | number，次 | 没有保护异常的完成轮数 |
| recoveredCount | number，次 | 保护恢复后最终完成的轮数；不是告警恢复条数 |
| faultTerminatedCount | number，次 | 故障终止轮数 |
| incompleteCount | number，次 | 未完成轮数；与故障终止并为页面异常结束 |
| abnormalCount | number，次 | 保护异常事件数；不是处理尝试次数 |
| alarmCount?、empty? | number、boolean | 旧周期告警数与空标记，当前RUN概况不另显示告警列表 |

保护事件的occurredAt为发生时间，outcome为RECOVERED/FAILED/IN_PROGRESS；returnedAt、resumedAt为可选回零/恢复运行时间；attemptCount是旧来源可选处理尝试次数。不能凭一个attemptCount补造具有具体发生时间的多条新事件。

<a id="page-19"></a>

## 字体、颜色和表格呈现

字体族由前端统一设置：`'Microsoft YaHei', 'PingFang SC', Arial, sans-serif`。接口返回文字内容和状态，字体、字号、颜色、布局、滚动、图表坐标样式由前端定义，不要求每条业务数据返回CSS。

| 公共层级 | 变量 | 当前基础值 |
| --- | --- | --- |
| 页面标题 | --page-title-size | 28px，默认白色 |
| 模块标题 | --section-title-size | 24px，标题色#85cbdc |
| 卡片标题 | --card-title-size | 20px |
| 正文/主要选项 | --body-text-size | 20px |
| 辅助说明 | --support-text-size | 18px |
| 单位/紧凑说明 | --caption-text-size | 16px |

这些是公共基础值，各页和窄屏有局部覆盖，不能据此写成所有表格都是20px。当前主要差异如下：

| 页面/组件 | 主要字号与布局规则 |
| --- | --- |
| 登录 | 平台标题、登录标题使用公共标题层级；介绍和能力标签16px，英文辅助15px；窄屏重新排列介绍区与表单 |
| 首页常规/大屏 | 常规三栏标题24px、摘要字段名20px、高度/拉力数字24～28px、速度22～24px、设备名18～20px；钢绳名/速度辅助标签14～16px、状态标签20～22px、单位14～18px。窗口≥1720px覆盖为标题29px、摘要标签25px、数字32px/速度28px、设备名25px、辅助标签20px、状态标签26px、单位21px。SVG标尺常规10px/大屏12px，高度框24px/28px |
| 告警 | 正文20px、辅助18px、紧凑16px，标签页24px/窄屏20px；只保留最新/历史列表和只读详情，无旧数量卡/分类统计图；宽表独立滚动 |
| 动力设备详情 | 使用公共20/18/16px层级，当前读数有26px及桌面30px覆盖；图表来源按钮、单位和右侧表采用紧凑样式，具体随断点生效 |
| 历史列表 | 桌面行正文21px，表头/时间20px；窄容器转为带字段名的记录卡片，不将整张宽表压缩成极小字号 |
| 单次历史详情 | 模块标题26px，概况数值26px，正文20px；拉力图筛选按钮沿页面正文层级，设备按钮有宽容器18px覆盖 |
| 两种新版右侧点表 | 正文16px、表头15px、曲线选择15px；低屏高时表头14px，窄容器正文14px/表头13px；表头固定、表体滚动 |
| 图表公共组件 | 轴名18px/16px，普通提示17px，高度探针紧凑提示14px；刻度、图例由绘图组件样式控制 |
| 数据归档 | 桌面标题28px、模块24px、控件和说明18px、摘要数值27px；小屏双图上下排列，左右单位独立 |
| 设置参数 | 使用公共标题/正文层级，数值与滚轮选择有独立样式；取消弹窗只丢弃草稿 |
| 用户管理 | 表格正文16px、表头15px、账号17px、列表标题22px；宽表滚动并允许长文字换行 |
| 模型管理 | 概览数字34px、模型名22px、事实17px；窄容器改成单列 |
| 历史巡检/数据分析 | 占位标题28px、开发提示20px，无业务图表和数据字体契约 |

| 颜色用途 | 当前公共值/说明 |
| --- | --- |
| 页面、面板、卡片 | #061b34、#092744、#0d304f |
| 正文、辅助 | #dce5e9、#bdced8 |
| 边框、强边框 | #365f7b、#60889c |
| 按钮、悬停、选中 | #21495f、#32647d、#236985 |
| 提示、危险、正常 | #ffd05a、#ff6679、#62efaa；具体状态组件可局部覆盖 |
| 首页状态 | 待机/检修黄、运行绿、故障红、离线灰；同时显示状态文字 |
| 历史单次曲线 | 动力设备有固定颜色；单台第二根绳使用对比色，不能把动力页SENSOR2绿色直接套到历史页第二绳黄色 |

控件保留可见焦点、加载禁用和aria说明；颜色不是唯一状态信息。字体与颜色依据`src/assets/styles/global.scss`、各页scoped样式、`src/views/home/styles/home.scss`、两种对比图组件和`TrendChart`核实。旧`docs/ui-typography.md`仍有过时告警跳转/阈值推断描述，本版以当前代码与本文为准，不把旧文字当成已生效规则。

<a id="page-20"></a>

## 前后端职责与下一步对接清单

目标继续遵循：**PLC负责判断拉力状态；后端负责采集、存储、整理PLC事件、来源对齐和统计；前端负责数据显示、筛选选择、格式化、导航和简单提醒。后端不通过比较每笔拉力数值自行新增故障事件。** 当前事实与目标要分别验收。

| 范围 | 当前尚未完成/需确认 | 建议后端输出或处理 |
| --- | --- | --- |
| 首页实时数据 | 生产仅初始GET，真实推送未接；首页无完整失败重试区 | 全量初始快照及约定实时协议；权威状态、平均值、阈值、更新时间 |
| 急停 | 成功只代表请求已下发，未独立表达执行确认 | 指令身份、授权校验、执行状态/设备确认及后续快照 |
| 动力SUM | 浏览器仍配对相加，第一来源速度被沿用 | 明确的合计来源、稳定采样键、质量和独立速度语义 |
| 历史单次新图 | 三原始来源显示，不再需要旧双统计表契约 | 完整实际采样、来源/绳号、时间/方向/质量、稳定帧身份；不要删掉原始回退过程 |
| 周期平均 | DEV由演示数据层生成，生产直接等接口结果 | 明确纳入运行、方向、质量、高度网格、有效样本数、截止时间和正确加权平均 |
| 历史列表/概况 | DEV临时算八绳极值；结果中文仍有前端解释 | 运行头、启动快照、结构化结果/原因、八单绳极值、全量日期索引和分页 |
| 运行边界定义 | 参数页说明一次完整升降计一次清理，但普通DEV RUN仅模拟150秒单一方向 | 统一“一轮运行/一次清理”的真实起止条件；不能把演示单向样本直接当生产轮次规范 |
| CSV | 前端导出仍配对相加；分页检查不等于后端快照 | 可复现数据版本、完整对齐明细或后端导出任务；缺失不补0 |
| 归档 | 三组范围未完整表达；有效记录、环比/同比规则待落实；历史条数存在30条回退 | 独立范围、实际参照记录/区间/条数、对齐后的序列与统计，空值明确 |
| 告警 | 前端独立latest/history契约与版本更新已实现，真实PLC/后端状态机待接 | 同一alarmCode维护全过程、历史分页、权威hasAttention；不根据每笔拉力新增事件 |
| 参数设置 | 默认值/范围为前端常量；缺生效版本，DEV存储未按系统隔离 | 后端合法范围/默认值、保存快照、何时生效与配置版本；历史保留启动时参数 |
| 用户与认证 | 用户列表路径待联调；菜单未按角色隐藏；401跳转/刷新等待/提示风暴已修正 | 账号权限、资源授权、令牌轮换及用户分页查询 |
| 模型/占位页 | 未接业务API | 开放时另定义契约；本期不补造模型运行、巡检、分析接口 |

上一版建议中的`sampleKey`、`ROPE_TOTAL`、`statistics`、`ropeExtrema`、`defaultUnitId`不是当前正式返回字段。新布局已删除右侧统计双表，应按当前页面需要裁剪目标契约：保留稳定帧身份、动力页需要的合计曲线、列表/概况所需极值；不要为旧UI强制增加不显示的整套统计表。

历史存储可继续参考《MySQL历史数据存储与查询建议_20261024.md》（原文引用，本次附件未提供该文件），但该文为设计建议，不是已建表证明。采集侧应完整保存原始数据，页面仅显示运动采样也不能成为删除STOP/异常原始数据的理由；新图的展示筛选与数据库留存范围分别定义。

<a id="page-21"></a>

## 本次文档核验与适用限制

本轮先修改前端，再更新本说明。红字提取9处的逐项处理及事件契约详见[PLC告警事件与红字修订统一说明](plc-alarm-revision-20261025.md)。

- 路由与页面逐项覆盖：12个页面，另含公共布局、两种CSV导出、17种当前页面HTTP方法/路径组合，另保留旧dashboard供遗留/DEV源使用；视频预览排除。
- 以当前模板、接口声明、类型及调用链核对；没有仅根据截图猜接口，也没有把保留的旧统计函数计作当前可见模块。
- 运行当前DEV历史详情、曲线/右表生成及CSV函数，核对第20次运行的51点、截图前六行和完整204行采样导出；这些证明模拟样本与文档一致，不能证明真实服务或设备已接入。
- 本轮新增告警生命周期/独立查询/版本保护/订阅、阈值编辑、认证和重复操作测试；整套前端测试300项通过。类型检查、生产模式构建及9项浏览器实际HTTP模拟检查通过；不把模拟通过当作PLC联调完成。
- Markdown表格、链接和JSON示例会在最终交付前检查；Word原件不改。

本文是截至2026-10-25当前工作区的页面与接口说明。真实数据库字段、设备协议、后端服务实现、生产权限和执行回执仍需后端联调；所有建议新增字段均已明确标注，不与当前已存在字段混写。
