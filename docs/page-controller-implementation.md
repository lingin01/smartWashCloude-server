# 页面 Controller 实现与接入状态

更新日期：2026-10-26。依据[页面接口与拉力图筛选总结](reference/page-interface-and-force-filter-20261026.md)和[接口主规范](external-api-contract.md)。本轮实现 Java Controller 层及必要的表示、权限和身份转换，未扩展历史数据库、运行识别或 PLC 写点。

**26 个目标方法已具备路由、类型化输入/返回与鉴权。其中 10 个方法连接现有能力，16 个方法在权限和请求检查后返回明确未就绪。Controller 完成不等于历史、告警和导出业务完成。**

## 1. 现在可以调用哪些接口

下文 `C=/api/command-center`。所有查询均通过登录账号及真实系统授权，操作者不从请求体取得。除登录和刷新外，目标接口要求 Bearer 访问令牌。

| 方法/地址 | 当前响应和实现范围 |
|---|---|
| POST `/api/auth/login` | 账号服务登录；token、refreshToken、expiresIn、expiresInUnit=SECONDS |
| POST `/api/auth/refresh` | 核对真实账号状态；访问令牌及秒有效期；refreshToken=null，不宣称已轮换 |
| GET `/api/auth/user-info` | 实际用户、权限、已配置且授权的 systems；单系统时给 currentSystemCode |
| POST `/api/auth/logout` | data=null，提示清除本地令牌；当前不撤销已签发 JWT |
| GET `/api/users` | 只读分页及关键词查询；user:read；不返回密码，缺登录历史为 UNKNOWN |
| GET `C/home` | 从现有清洗快照转换公开编号、t/m/m/min、IDLE 和显式 null；没有快照时 available=false |
| GET `C/home/stream` | 立即及每秒完整首页事件；同序号也发送，updatedAt 不变，另给 pushedAt |
| POST `C/emergency-stop` | 经安全、清洗、命令服务入既有写队列；仅使用已配置点位；未配置时明确拒绝 |
| GET `C/commands/{commandCode}` | 按实际 commandId 及系统查询进程内命令；不把 requestId 当 commandCode |
| GET `C/parameter-settings` | 现有同步状态；NOT_READY 或 UNCONFIRMED，尚未确认的 saved/applied 版本和值显式 null |

“连接现有能力”仍受实际配置和事实来源约束。首页沿用现有采集转换，D201、D203～D206 新状态解析尚未迁移；返回 notices 说明。传感器拉力有效不等于 PLC 已判定 NORMAL，不依据拉力自行生成告警。当前没有独立运动证据、机械限位、阈值和采集实例编号的字段继续为空。

急停新受理为 HTTP202、body.code=200。内部 SUCCEEDED 只映射 WRITE_CONFIRMED，deviceExecutionConfirmed=false；失败使用实际非 2xx 和原因。M50 的现场地址、类型与反馈仍须核实，本轮不替换点位模板、不新增远程启动/复位/解除急停/暂停/恢复。

## 2. 已完成入口、业务尚未接入的 16 个方法

以下错误发生在认证、权限、系统归属和必要请求检查之后。没有系统映射时先返回 SYSTEM_MAPPING_NOT_READY；未知系统/设备为 404，无权为 403。

| 方法/地址 | 当前业务响应 | 后续接入位置 |
|---|---|---|
| GET `C/winch/detail` | 409 CONFIGURATION_NOT_READY | 清洗模块提供实际已生效速度/配置及本机详情 |
| GET `C/winch/tension/trend` | 503 HISTORY_NOT_READY | 清洗→统计：运行/周期高度曲线、来源和游标 |
| PUT `C/winch/tension-thresholds` | 503 THRESHOLD_MAPPING_NOT_READY | 清洗配置编排→命令：两路阈值真实写点及确认 |
| GET `C/archive` | 503 STATISTICS_NOT_READY | 清洗→统计：独立范围、预计算、对比结果 |
| GET `C/history/list` | 503 HISTORY_NOT_READY | 清洗→统计：系统 RUN 分页及独立日期目录 |
| GET `C/history/detail` | 503 HISTORY_NOT_READY | 清洗→统计：运行头、八索/四机曲线与事件 |
| GET `C/history/point` | 503 HISTORY_NOT_READY | 清洗→统计：稳定点键对应的真实保存帧 |
| PUT `C/parameter-settings` | 409 CONFIGURATION_NOT_READY | 真实 savedVersion、保存与回读未闭环，不提前下发 |
| GET `C/alarms/latest` | 503 EVENTS_NOT_READY | PLC 事件事实、完整近期集合和 hasAttention |
| GET `C/alarms/history` | 503 EVENTS_NOT_READY | 事件筛选、去重与分页 |
| GET `C/events/history` | 503 EVENTS_NOT_READY | 含无 RUN 关联的独立异常 |
| GET `C/events/{eventCode}` | 503 EVENTS_NOT_READY | 事件与有限现场快照 |
| POST `C/exports` | 503 EXPORT_NOT_READY | 统计模块的持久任务与一致快照导出 |
| GET `C/exports` | 503 EXPORT_NOT_READY | 当前账号的真实任务列表 |
| GET `C/exports/{taskCode}` | 503 EXPORT_NOT_READY | 任务归属、状态与已发布文件信息 |
| GET `C/exports/{taskCode}/files/{fileCode}` | 503 EXPORT_NOT_READY | 核对任务/文件归属后流式下载完整文件 |

这些方法不返回假空列表、无告警红点、PENDING 任务或空 CSV。输入/返回类型已建立；接入业务实现时替换表示层中的明确未就绪分支，继续通过大厅和既有四服务，不把 SQL、曲线计算或事件状态机写进 Controller。导出任务创建者和文件归属还须在真实任务接入时实施，当前拒绝路径没有任何任务或文件可读。

## 3. 本轮对齐的请求规则

- 单机图接收 `systemCode/winchCode/viewType=FORCE_COMPARISON/comparisonView=COUNT|DAY|MONTH|YEAR`，不要求额外日期。完整点续读使用 resolution/cursor，默认 500 点/序列、COUNT 每批 5 组，上限分别 2000 和 10。
- 单次图接收 `systemCode/recordCode`；兼容 yearGrouping=MONTH，可省略。四机及三种来源选择仍是前端本地筛选，后端没有把隐藏曲线删除的默认规则。RAW/OVERVIEW、分段、稳定点键、显式空值均已有返回类型；曲线内容待历史服务接通。
- 归档接收独立的 change/current/reference 三组日期边界，均为右开。自选比较六个边界；环比/同比不带隐藏参考范围；运行比较带 recordCode。拒绝旧 compareStart/compareEnd，不再追加结束日。
- 系统历史为带时区 startTime/endTime、endExclusive=true；告警 CUSTOM 日期首尾包含，二者不混用。告警 levels 省略与显式空串不同。
- 三参数和阈值写请求保留 requestId/expectedVersion；阈值固定 t、两路真实 sensorCode。没有版本或现场写入依据不虚构成功。
- 没有采样时间先后、年龄或过期校验。日期格式/范围、分页预算和登录令牌有效期各自服务于 HTTP 请求与认证。

统一成功包装为 `{code:200,message,data}`，包括 HTTP202。失败为对应 HTTP 状态及 `{code,message,data:{errorCode}}`。新页面 DTO 可空字段显式 null，有效 0 不丢弃。旧 `/api/cleaning/...` 仍保留旧字段与命令包装语义，不能混用。

## 4. 公开编号与系统授权如何配置

当前七表没有完整的新页面公开编号/名称列。本轮使用外部 Spring 配置 `cleaning.page.systems` 明确映射，默认空列表；不把内部 deviceId 猜成 systemCode，不自动生成 QJXT-01 或给所有账号授权。

| 配置层级 | 字段 |
|---|---|
| systems[] | systemCode、systemName、deviceId、allowedUsernames、winches |
| winches[] | winchCode、winchName、winchId、groupNo、sensors |
| sensors[] | sensorCode、sensorName、sensorId、ropeId、ropeNo |

deviceId/winchId/sensorId/ropeId 必须对应已核实的采集身份；公开编号可以与内部编号相同，但必须明确配置。四台设备 groupNo 为 1～4，各自两路 ropeNo 为 1/2，不能按数组位置或编号后缀推断。名称无来源可以为空，不能当作已取得现场名称。allowedUsernames 是实际允许访问该系统的登录账号，空集合不授权；同时仍要求角色权限。

`user-info.systems=[]` 表示没有已配置且获授权的系统，前端不应硬填编号继续请求。外部映射需要随正式档案存储落地后统一迁移，不能长期维护两份互相冲突的身份来源。此配置不会开启 PLC 连接或写入。

## 5. 代码从哪里看

| 关注点 | 文件 |
|---|---|
| 首页、图表、历史、参数、急停 | [CommandCenterController](../src/main/java/com/company/project/controller/CommandCenterController.java) |
| 告警与独立事件 | [AlarmEventController](../src/main/java/com/company/project/controller/AlarmEventController.java) |
| 导出列表、任务与下载 | [DataExportController](../src/main/java/com/company/project/controller/DataExportController.java) |
| HTTP 输入与返回 | [PageRequests](../src/main/java/com/company/project/dto/page/PageRequests.java)、[PageResponses](../src/main/java/com/company/project/vo/page/PageResponses.java) |
| 现有能力适配和未就绪分支 | [CommandCenterPresentation](../src/main/java/com/company/project/presentation/web/CommandCenterPresentation.java)、[PageAccess](../src/main/java/com/company/project/presentation/web/PageAccess.java) |
| 每秒完整推送 | [HomeStreamPresentation](../src/main/java/com/company/project/presentation/web/HomeStreamPresentation.java) |
| 账号与用户列表 | [AuthController](../src/main/java/com/company/project/controller/AuthController.java)、[ReadOnlyUserController](../src/main/java/com/company/project/controller/ReadOnlyUserController.java) |
| 权限和错误 | [SecurityConfig](../src/main/java/com/company/project/config/SecurityConfig.java)、[PageWebExceptionHandler](../src/main/java/com/company/project/controller/PageWebExceptionHandler.java) |

调用层级仍为 Controller→表示层→大厅→安全/清洗→命令或统计→设备管理/DAO。系统和命令状态的写入归属未改变。公开映射由数据层登记，安全服务决定是否允许访问；数据层不回调上层。

## 6. 验证范围与后续工作

本轮执行 `mvn -s /workspace/.setup/settings.xml -B verify`，67 项测试全部通过，并完成打包。自动化检查覆盖请求绑定、预算、归档范围、账号状态/权限、系统映射、实际命令号、写应答与执行确认的区别、显式 null、有效 0、旧采样时刻不被丢弃，以及同序号每秒完整 SSE；同时运行原有通信、业务分层、统计与推送测试。本地构建使用 Java 21 编译为项目声明的 Java 17 目标；环境专用 settings 路径不是部署要求。

尚未完成真实 PLC、真实前端和新历史库的端到端联调；本轮不创建目标历史表、不自动生成 RUN 或告警过程、不实现后台导出。新页面路径已受保护，旧 `/api/admin/user/**` 的完整管理员权限仍是既有独立待办，不能据本轮测试宣称全站权限已验收。JWT 服务端撤销、持久命令审计及新点位事实同样未完成。

下一步按[系统完成清单](project-completion-status.md)接入清洗记录与可靠保存，逐项把上表的未就绪方法替换成真实服务结果。
