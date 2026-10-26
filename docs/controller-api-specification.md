# Controller 接口实施清单：17 个页面入口与 9 个补充入口

修订日期：2026-10-26。依据两份最新附件及[页面接口主规范](external-api-contract.md)，列出 Controller 的方法、输入、输出、权限和服务调用路径。26个方法的Java路由和类型已实现；[实际接入状态](page-controller-implementation.md)列出10个连接现有能力的方法和16个业务未就绪方法。当前没有新增历史表或PLC写点。

当前代码行为见[现有接口参考](current-api-reference.md)。页面与 PLC 事件分工见[告警和阈值处理说明](plc-alarm-and-threshold-processing.md)，图表范围见[详情拉力对比处理说明](detail-force-comparison-processing.md)。字段的最终名称、空值与枚举以主规范为准；下列类型现已集中在PageRequests/PageResponses及账号DTO/VO中；最终服务路径仍是后续业务接入要求。

## 1. 统一边界

所有地址使用 `/api` 基地址；下文 `C` 代表 `/api/command-center`。GET 使用 Query/Path，不接收 JSON 请求体；POST/PUT 使用 JSON。登录身份由认证上下文提供，不能在业务请求中提交 operatorId 或权限。

Controller 只接收 HTTP 对象并交给表示层。表示层完成 DTO/业务参数转换，再进入大厅；大厅按安全、清洗、命令、统计四服务编排。清洗模块维护系统、运行和事件事实，命令模块维护命令状态，统计模块保存与查询；DAO 和设备接入不回调上层。认证和账号查询沿用现有账号服务，不新增第五个清洗业务服务。

| 通用项 | 目标约定 |
|---|---|
| 成功包装 | `Result<T>={code:200,message,data:T}`；HTTP202只表示已受理，body.code仍为200，阶段由data说明 |
| 错误 | 使用对应HTTP错误及明确业务错误码；未实施/依赖未就绪不得返回伪空200 |
| 认证 | 除login/refresh外均要求有效访问令牌、实际启用账号及资源权限；刷新令牌不能读业务资源 |
| 权限 | 清洗查询device:read，操作device:write，告警/独立事件alarm:read，用户user:read；同时核对systemCode真实归属 |
| 身份 | systemCode、winchCode、sensorCode由后端解析实际内部身份；deviceCode在告警接口表示所属动力设备公共编号 |
| 可空值 | 目标DTO定义的可空字段显式null，有效0保留；未知事实不填演示值 |
| 单位 | 页面拉力t，高度m，速度m/min；数据库测量kg，边界转换一次 |
| 时间 | 时刻带时区，业务日历Asia/Shanghai；除告警历史日期输入外，查询范围左闭右开 |
| 采集 | 不以采样时间先后、年龄或时钟偏差拒收、丢弃或判离线；必要查询格式校验不等于采集时间校验 |
| 列表 | 页码从1开始；普通页面默认10、最大100，导出任务默认20；不能把本页长度当全量total |

当前SecurityConfig已保护新版页面、认证私有入口及只读用户列表；旧管理员CRUD仍待独立授权完善。新页面成功业务码固定200，旧清洗写接口保持原包装。

## 2. AuthController：已完成页面响应和入口保护

认证不经过清洗/命令业务链路。下列四个地址已适配真实账号检查及类型化响应；JWT服务端撤销仍未实施。

| 编号 | 方法/地址 | 拟议Java方法与输入 | 响应DTO | 权限与调用 | 状态 |
|---|---|---|---|---|---|
| P01 | POST `/api/auth/login` | `login(LoginRequest)`；username、password | `Result<TokenResponse>` | 公共登录入口→账号服务→令牌签发 | 已适配秒有效期及类型化输入 |
| P02 | GET `/api/auth/user-info` | `userInfo(Authentication)`；无业务参数 | `Result<UserInfoResponse>` | 有效账号→账号资料/角色/授权系统查询 | 已读取真实账号/授权映射 |
| P03 | POST `/api/auth/logout` | `logout(Authentication)`；无业务参数 | `Result<Void>` | 有效账号→真实会话退出处理 | 已说明仅客户端退出，服务端撤销未实施 |
| P04 | POST `/api/auth/refresh` | `refresh(RefreshRequest)`；refreshToken | `Result<TokenResponse>` | 有效刷新凭据→真实账号状态→令牌服务 | 已检查禁用账号、返回秒有效期 |

`TokenResponse`包含token、expiresIn及真实返回的refreshToken。**现有login和refresh的expiresIn已统一为秒**，expiresInUnit=SECONDS。只有实际实施轮换才返回新的refreshToken，不宣称当前已轮换。

`UserInfoResponse`包含id、username、nickname、avatar、email、phone、roles、permissions、systems、currentSystemCode。systems为实际授权`{systemCode,systemName}`，单系统也不能在浏览器固定套编号。缺失资料及系统选择如实返回；无资料来源不生成演示用户。

## 3. CommandCenterController：10 个页面方法

该 Controller 是目标页面入口。以下路由、输入和返回类型已存在。首页和急停连接既有能力，其余按[接入状态](page-controller-implementation.md)明确返回未就绪；旧CleaningController继续保留独立协议。

| 编号 | 方法/地址 | 拟议Java方法与输入DTO | 响应DTO | 权限 | 服务路径与状态 |
|---|---|---|---|---|---|
| P05 | GET `C/home` | `home(SystemQuery)` | `Result<HomeSnapshot>` | device:read | 大厅→安全→清洗快照；已接入 |
| P06 | POST `C/emergency-stop` | `emergencyStop(EmergencyStopRequest)` | HTTP202 `Result<CommandReceipt>`，或明确拒绝/失败 | device:write | 大厅→安全→清洗编排→命令入队；已适配 |
| P07 | GET `C/winch/detail` | `winchDetail(WinchQuery)` | `Result<WinchDetailResponse>` | device:read | 入口已实现；409 CONFIGURATION_NOT_READY |
| P08 | GET `C/winch/tension/trend` | `winchTrend(WinchTrendQuery)` | `Result<WinchTrendResponse>` | device:read | 入口已实现；503 HISTORY_NOT_READY |
| P09 | PUT `C/winch/tension-thresholds` | `updateThresholds(ThresholdUpdateRequest)` | `Result<ThresholdApplyResponse>`；PENDING或APPLIED | device:write | 入口已实现；503 THRESHOLD_MAPPING_NOT_READY |
| P10 | GET `C/archive` | `archive(ArchiveQuery)` | `Result<ArchiveResponse>` | device:read | 入口已实现；503 STATISTICS_NOT_READY |
| P11 | GET `C/history/list` | `historyList(RunListQuery)` | `Result<RunListResponse>` | device:read | 入口已实现；503 HISTORY_NOT_READY |
| P12 | GET `C/history/detail` | `historyDetail(RunDetailQuery)` | `Result<RunDetailResponse>` | device:read | 入口已实现；503 HISTORY_NOT_READY |
| P13 | GET `C/parameter-settings` | `parameterSettings(SystemQuery)` | `Result<ParameterSettingsResponse>` | device:read | 已读取同步状态；保存/生效值未确认 |
| P14 | PUT `C/parameter-settings` | `updateParameters(ParameterUpdateRequest)` | `Result<ParameterSettingsResponse>`及命令阶段 | device:write | 入口已实现；409 CONFIGURATION_NOT_READY |

### 3.1 输入DTO应包含的字段

| DTO | 必填 | 可选及特别规则 |
|---|---|---|
| SystemQuery | systemCode | 只选择已授权实际系统 |
| WinchQuery | systemCode、winchCode | 后端核对设备属于该系统 |
| EmergencyStopRequest | systemCode、requestId | reason；同一次操作重试沿用requestId，操作者取登录 |
| WinchTrendQuery | systemCode、winchCode、viewType=FORCE_COMPARISON、comparisonView=COUNT/DAY/MONTH/YEAR | cursor、maxPointsPerSeries、unitPageSize及主规范的原始点读取选项；响应有界 |
| ThresholdUpdateRequest | systemCode、winchCode、requestId、expectedVersion、thresholds | thresholds恰为该设备两路`{sensorCode,alarmThreshold,safetyValue}`；输入固定t，不随图表kg切换 |
| ArchiveQuery | systemCode及页面指标/周期/比较条件 | winchCode、metric、tensionMetric、period、comparisonType、recordCode；左图changeStartDate/changeEndDate、当前currentStartDate/currentEndDate、参考referenceStartDate/referenceEndDate各自成对 |
| RunListQuery | systemCode、scope=RUN、startTime、endTime、endExclusive=true | page/pageSize、runResult、dateBasis、includeAvailableDates、availableDatesCursor；winchCode仅回显导航上下文，不筛掉其他设备参与的系统记录 |
| RunDetailQuery | systemCode、recordCode | yearGrouping=MONTH仅兼容当前请求，不生成月度统计；游标、点数预算及局部范围用于分批读取；设备/来源选择不改系统运行头 |
| ParameterUpdateRequest | systemCode、requestId、expectedVersion、startTime、intervalMinutes、speedMPerMinute | startTime为HH:mm，间隔分钟由后端映射内部单位；三项之外不混入阈值参数 |

requestId/expectedVersion是必要技术字段，新附件前端写请求尚未全部携带，需要前后端同步适配。阈值expectedVersion从详情的`thresholdSettings.savedVersion`取得；三参数expectedVersion取真实参数savedVersion。版本未准备好时明确未就绪，不猜0，不以当前读数代替设定。

阈值输入校验只覆盖真实两路身份、有限正数和预警小于保护等必要规则，业务层统一处理；图表1.4t/3t范围不是现场上限。具体PLC写地址、编码、回读及两路生效确认未知时不能自动填入。

### 3.2 返回DTO的最低业务内容

| DTO | 主要字段和保证 |
|---|---|
| HomeSnapshot | systemCode/systemName、系统五态、真实高度/运动、winches、sensors、updatedAt/asOf；状态来自PLC明确事实，不按每笔拉力比较阈值自造故障 |
| CommandReceipt | systemCode、requestId、commandCode、commandStatus、phase、deviceExecutionConfirmed、message及真实时间；写应答不等于设备已停 |
| WinchDetailResponse | systemCode、winch、sensors、torqueForce、currentHeight/currentMode、有效setSpeedMPerMinute、todayCompletedCount、runningHours、maintenanceAt、thresholdSettings、updatedAt/asOf及说明 |
| ThresholdApplyResponse | systemCode、winchCode、status、effectiveThresholds、configurationVersion及关联请求/命令；APPLIED必须是PLC确认的完整两路，PENDING不覆盖旧生效值 |
| WinchTrendResponse | kind、units、series、真实高度范围/限位、数据范围/截至、加载完整性/游标；动力页需要的两单绳、独立伺服和ROPE_TOTAL由后端准备 |
| ArchiveResponse | 左图changePoints、当前comparisonCurrent、参考comparisonPrevious、对齐轴、实际三组范围、summary、数据覆盖及统计准备情况 |
| RunListResponse | systemCode、entries、total、page/pageSize、独立availableDates目录及其完整性/游标、asOf/dataRange；total为系统记录数 |
| RunDetailResponse | systemCode、entry、sensors、servos、真实高度范围/限位、事件摘要及各自完整性/游标、asOf；八路单绳/四路独立伺服按实际身份关联 |
| ParameterSettingsResponse | 三项已确认显示值、savedVersion/savedSettings、appliedVersion/appliedSettings、applyStatus、关联命令及说明；保存、写应答、生效分别表达 |

新版图表右侧是点表，已删除旧统计双表。因此statistics、rawStatistics、ropeExtrema、defaultUnitId不作为这些页面DTO的强制字段；运行概况和归档仍使用已保存摘要。稳定sampleKey及segments必须区分重复高度、换向和缺口。历史图保留两单绳与独立伺服三类来源，不强制返回隐藏的合计/四机平均；动力页合计由后端提供。

“全点表”通过有界分批最终读取全部已保存点，不能以降采样点冒充原始全点，也不一次把全部历史交给浏览器。正常DOWN没有保存的连续采样不能补造。历史entry.maxTension/minTension固定为本生产上行记录全系统八路单绳有效集合极值，不受选中设备或图例影响，不包含伺服和两索合计。

## 4. AlarmEventController：2 个页面方法与 2 个补充方法

旧alarm-dashboard退出本期目标页面；不保留两套正式告警入口让前端选。告警和一般异常仍共用真实事件事实，补充入口用于发现无RUN关联、非告警的下行异常，不为方便列表把它们转成告警。

| 编号 | 方法/地址 | 拟议Java方法与输入DTO | 响应DTO | 权限 | 服务路径与状态 |
|---|---|---|---|---|---|
| P15 | GET `C/alarms/latest` | `latest(LatestAlarmQuery)` | `Result<LatestAlarmResponse>` | alarm:read | 入口已实现；503 EVENTS_NOT_READY |
| P16 | GET `C/alarms/history` | `history(AlarmHistoryQuery)` | `Result<AlarmHistoryResponse>` | alarm:read | 入口已实现；503 EVENTS_NOT_READY |
| S04 | GET `C/events/history` | `events(EventHistoryQuery)` | `Result<EventHistoryResponse>` | alarm:read | 入口已实现；503 EVENTS_NOT_READY |
| S05 | GET `C/events/{eventCode}` | `event(String eventCode, SystemQuery)` | `Result<EventDetailResponse>` | alarm:read | 入口已实现；503 EVENTS_NOT_READY |

| DTO | 输入或返回内容 |
|---|---|
| LatestAlarmQuery | 必填systemCode，可选deviceCode；不接受TODAY、开始/结束日期或历史页码 |
| LatestAlarmResponse | systemCode、asOf、recent、hasAttention及实际近期窗口说明；完整近期成员集合保留跨日持续事件，不能用历史页补足 |
| AlarmHistoryQuery | systemCode，range默认30D，CUSTOM时startDate/endDate；levels、deviceCode、page/pageSize；levels省略默认两级，空串表示不选等级、返回空结果 |
| AlarmHistoryResponse | systemCode、asOf、historyRange.startDate/endDate、list、total、page、pageSize；先全量筛选再分页，回显实际请求条件 |
| EventHistoryQuery | systemCode、startTime/endTime右开区间、recordKind=ALL/NON_ALARM/ALARM及分页；可选真实设备/方向/运行关联筛选，不要求recordCode |
| EventHistoryResponse | systemCode、asOf、filters、list、total、page、pageSize；非告警项保留isAlarm=false及真实eventCode，不强填等级 |
| EventDetailResponse | systemCode、event、关联alarm或null、有限事件快照和覆盖说明；缺少RUN关联也可查，不执行确认/恢复动作 |

告警CUSTOM输入是上海自然发生日期**首尾包含**，1～366天；后端将结束日次日零点转换为数据库右开上界，响应仍回显用户输入日期。此例外不改变其他历史接口的右开区间，也不是采样时间校验。

`AlarmItem`至少有alarmCode、systemCode、deviceCode、level、type、content、occurredAt及真实时间来源、updatedAt/revision、eventState/endedAt。触发值、峰值、峰值时刻、方向、高度、触发阈值/类别/版本和runCode按真实来源提供，未知不补0。内部通道/控制器会话用于区分同时发生的过程，页面按所属动力设备显示。

PLC给出普通/严重错误信号；清洗模块维护同一连续过程的alarmCode，统计模块保存和查询。持续、升级、结束只更新同一事件及revision，不新增一次；断联不自动结束、跨runCode不自动拆分。ENDED/endedAt只表示现场过程结束，不代表人工确认、处理或恢复。峰值仅对PLC已经判定的事件窗口整理，必须说明观测覆盖，不声称已保存完整下降轨迹。

recent和hasAttention由同一后端事实快照产生；前端网络失败、进入告警页、关闭详情均不创建PLC事件或清除红点。最新集合窗口、关注规则及版本语义详见主规范，不由Controller按拉力或颜色判断。

## 5. ReadOnlyUserController：1 个页面方法

| 编号 | 方法/地址 | 拟议Java方法与输入 | 响应DTO | 权限与调用 | 状态 |
|---|---|---|---|---|---|
| P17 | GET `/api/users` | `users(UserListQuery)`；keyword、page、pageSize | `Result<UserListResponse>` | user:read→实际账号权限→账号服务查询 | 已实现只读分页、实际权限和UNKNOWN登录历史 |

`UserListResponse={list,total,page,pageSize}`，行按新页面包含id、username、nickname、roles、lastLoginAt、createdAt及后端角色文字/可用性说明。关键词由后端按账号、姓名、角色筛选并稳定排序；密码/哈希不进入响应。最近登录缺乏记录不等于从未登录，必须区分“未知”和“确认从未登录”，不能照搬前端null文案补造事实。当前账号表没有形成该登录历史闭环。

本页面只读，不因已有管理员CRUD就增加用户编辑、密码重置或角色授权入口。账号服务保留原职责，不进入清洗命令链路。

## 6. CommandCenterController：3 个补充读取/传输方法

| 编号 | 方法/地址 | 拟议Java方法与输入 | 响应DTO/协议 | 权限 | 服务路径与状态 |
|---|---|---|---|---|---|
| S01 | GET `C/home/stream` | `homeStream(SystemQuery, Authentication)` | `SseEmitter`，event=home、每条为`Result<HomeSnapshot>` | device:read，连接期保持实际账号检查 | 大厅→安全→清洗快照；已实现每秒完整推送 |
| S02 | GET `C/commands/{commandCode}` | `command(String commandCode, SystemQuery)` | `Result<CommandReceipt>` | device:read及实际系统/命令归属 | 已按真实commandCode及系统查询 |
| S03 | GET `C/history/point` | `historyPoint(SamplePointQuery)`；systemCode、recordCode、sampleKey，可选winchCode | `Result<SamplePointResponse>` | device:read | 入口已实现；503 HISTORY_NOT_READY |

首页先HTTP快照再SSE，新页面已每秒完整发送，同采样序号也发送，保留实际采样时间。旧设备SSE仍同序号只保活，两条通道各自保留。命令查询不把写应答翻译成现场停止/参数生效。

`SamplePointResponse`包含systemCode、recordCode、sampleKey、真实sampleFrameId/time、按实际winch关联的来源读数、质量及说明。平均高度桶没有真实帧，不调用本接口伪造采样。历史当前设备/光标选择不能替换成此刻实时读数。

## 7. DataExportController：4 个补充方法

导出仍采用后台任务及导出记录列表；新附件中的浏览器CSV是前端现状，不能取代已明确的服务端完整快照导出目标。生成过程属于统计模块内部导出器，不新增独立业务服务。

| 编号 | 方法/地址 | 拟议Java方法与输入 | 响应DTO/协议 | 权限 | 服务路径与状态 |
|---|---|---|---|---|---|
| S06 | POST `C/exports` | `create(ExportCreateRequest)` | HTTP202 `Result<ExportTaskResponse>` | 对应数据读取权限及系统归属 | 入口已实现；503 EXPORT_NOT_READY |
| S07 | GET `C/exports` | `list(ExportListQuery)` | `Result<ExportListResponse>` | 登录、系统及创建者归属 | 入口已实现；503 EXPORT_NOT_READY |
| S08 | GET `C/exports/{taskCode}` | `task(String taskCode, SystemQuery)` | `Result<ExportTaskResponse>` | 对应读取权限、系统及创建者归属 | 入口已实现；503 EXPORT_NOT_READY |
| S09 | GET `C/exports/{taskCode}/files/{fileCode}` | `download(String taskCode, String fileCode, SystemQuery)` | 完整发布的CSV/ZIP流；成功不套JSON | 对应读取权限、系统及创建者归属 | 入口已实现；503 EXPORT_NOT_READY |

`ExportCreateRequest`包含systemCode、exportType、unit和已提交筛选范围或选中编号：RUN_LIST运行范围/recordCodes，RUN_SAMPLES运行/采集范围，EXCEPTIONS事件范围/eventCodes，PERIOD_STATISTICS周期/设备范围。范围与编号选择互斥，不提交SQL、物理表名或页面page。EXCEPTIONS要求alarm:read，其余要求device:read；任务读取/下载继续检查相应源数据权限，不能凭任务编号绕过。

`ExportListQuery`包含systemCode、可选exportType/state及page/pageSize。`ExportListResponse={list,total,page,pageSize}`。`ExportTaskResponse`包含taskCode、systemCode、exportType、state、phase、冻结filters/unit、真实时间、行数、files、tableCounts及失败原因；未完成文件不能下载。文件清单含fileCode、fileName、mediaType、sizeBytes、dataRows、rowKind、downloadable，不把内部存储路径交给客户端。完整列、版本与一致快照规则见[导出方案](data-export-plan.md)。

## 8. 实施时的交付检查

本清单共 **P01～P17共17个页面方法，S01～S09共9个补充方法，总计26个方法/路径组合**，路由和HTTP类型均已建立。10个方法连接现有能力，16个保留明确未就绪响应；具体业务接通情况以[当前实现说明](page-controller-implementation.md)为准。

每个方法完成时应同时核对：请求DTO和响应DTO、所属系统授权、业务服务入口、真实存储/采集来源、错误/未就绪回执、必要分页/响应预算。Controller不要直接查DAO、比较拉力判断事件、识别运行结果或操作PLC缓冲。

先打通身份/授权与明确事实来源，再实施页面读取及PLC事件保存，随后接阈值/三参数的真实生效闭环、图表完整点读取和导出。PLC未知编号/结束/等级语义、阈值点位与确认条件未明确的部分保持未就绪，不使用模拟成功或空列表冒充。上行原始、下行有限事件、永久历史和无采样时间校验的既有边界不因接口补齐而改变。
