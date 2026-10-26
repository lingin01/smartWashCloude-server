# SmartWash PLC 后端

Java 17、Spring Boot 3.2.5、MySQL 8 的单体后端，服务端直接通过 Modbus TCP 读写 PLC。
当前范围是一套 PLC 下的四台绞机和八路索拉力。当前内部设备标识为 `deviceId`，对应 `cleaning.plc.devices[].id` 和数据库 `device_id`；新版页面目标统一使用公开 `systemCode`，由后端映射内部身份。

## 文档从哪里开始看

当前代码和部署入口看[2026-10-27后端实施说明](docs/updates/2026-10-27/backend-implementation.md)及[验证进度](docs/updates/2026-10-27/implementation-progress.md)。已接通PLC数据转换、有序处理、M106整轮识别、持久待写与事务保存、历史查询、五周期次数/高度统计、严重告警、配置保存、操作审计和后台导出。现场PLC和业务数据库尚未变更。

完整需求基线见[后端完整总结](docs/updates/2026-10-26/backend-complete-summary.md)。该文档的旧实现状态保留为2026-10-26快照；当前完成度以上一段的新文档为准。

- [M106实时、历史与统计规则](docs/updates/2026-10-26/realtime-history-statistics-overview.md)
- [最新PLC点位契约](docs/updates/2026-10-26/realtime-point-contract.md)
- [接口契约](docs/external-api-contract.md)，本次新增字段及行为见实施说明第5节
- [数据库安装和升级](docs/database/README.md)
- [0.5m历史与一年容量估算](docs/database/height-history-period-capacity-20261025.md)
- [Modbus TCP文档核对](docs/modbus-tcp-document-check-20261025.md)

旧“一次上行＋所属往返”、M204边界以及“16个接口未就绪”的文档属于设计演变记录，不能作为当前代码行为。当前以M106系统整轮为主历史，上行按高度留点，M60/M61锁存结算。

## 数据库与环境

空库执行 [schema.sql](src/main/resources/schema.sql)，当前为16张目标/兼容表＋3张保留旧历史表，共19表；脚本不创建或切换数据库，不创建默认账号。已有7表和上一版17表分别使用独立增量升级脚本，不能用新库脚本代替升级。
已使用旧表结构的数据库，请先备份，再审阅 [数据库迁移说明](docs/database/README.md)。应用不会自动运行迁移。

本地在项目根目录 [application-local.yml](application-local.yml) 直接填写数据库地址、账号、密码及 JWT 密钥，`dev` 启动会自动读取；无需配置环境变量或系统 PATH。模板见 [application-local.example.yml](application-local.example.yml)。按用户明确要求，该应用配置及 `field-config/field-test.yml`、`field-config/plc.yml` 随仓库提交，配置中的凭据对仓库访问者可见。数据库名需对应实际目标库；JWT 密钥至少32字节，使用固定的私有随机值。

```bash
mvn clean verify
mvn spring-boot:run
```

默认端口8080。PLC总开关和写入均默认关闭；确认实际地址、类型、倍率、单位、极性、业务编号后，通过外部 Spring 配置导入 [点位模板](src/main/resources/plc-points.example.yml)。模板不自动加载，也不能将现场标签数字直接作为协议地址。实际字段、保留的现场标签参考和采集边界见 [PLC接入说明](docs/plc.md)。

## 现场采集与测试

见[真实 PLC 现场测试说明](docs/field-testing.md)：在 `field-config/field-test.yml` 填写参数后，IDE 中直接运行对应测试类，无需环境变量或 VM 参数。提供真实 PLC 只读采集、现场 CSV/完整帧保存、已有历史 DAO 的 MySQL 保存验证、只读接口检查和离线回放。普通 Maven 测试自动排除四个现场入口，命令行脚本为可选方式。测试文件保存和旧历史 DAO 验证不代表正式上行历史自动保存链路已完成。

## 当前API

下表是当前代码；真实请求/返回、SSE及命令限制见[现有接口参考](docs/current-api-reference.md)。新版`/api/command-center/...`路由已加入，逐项可用状态见[Controller 实现说明](docs/page-controller-implementation.md)；以下表格保留旧设备接口，不能直接作为新页面返回结构。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/cleaning/devices/{deviceId}/realtime` | 最新采集快照 |
| GET | `/api/cleaning/devices/{deviceId}/realtime/stream` | 首页SSE实时推送，需要Bearer访问令牌 |
| GET | `/api/cleaning/devices/{deviceId}/statistics` | 本进程已观测样本统计，scope=PROCESS |
| GET | `/api/cleaning/devices/{deviceId}/settings` | 参数同步状态 |
| POST | `/api/cleaning/devices/{deviceId}/emergency-stop` | 急停申请，body为requestId/reason |
| PUT | `/api/cleaning/devices/{deviceId}/settings` | 三参数修改，body为requestId/expectedVersion/settings |
| GET | `/api/cleaning/devices/{deviceId}/commands/{requestId}` | 本地命令状态 |

三项参数为 `startTime`（HH:mm）、`intervalSeconds` 和 `groupSpeedMetersPerMinute`。请求不接收操作者身份，操作者和权限来自已验证登录账号。设备接口需要Bearer访问令牌；刷新令牌不能当访问令牌。

登录和用户信息沿用 `/api/auth`，账号管理沿用 `/api/admin/user`。管理员入口要求登录并核对当前账号的实际管理员角色。

当前设备接口没有旧地址别名，也没有远程启动、复位、解除急停、暂停/恢复接口。新页面使用公开systemCode、t和显式null，并核对系统授权；新历史和统计已接入。旧deviceId、STANDBY、kg响应继续兼容。

## 业务核心与数据流

从页面和PLC事实理解整个业务，先读[业务流程与页面数据总览](docs/business-flow-and-page-data-overview.md)：包含四服务分工、一次上行的生命周期、各页数据来源、原始历史与周期计算，以及跨日记录示例。

基于PLC点位和页面需求的[统一方案](docs/unified-plc-business-and-data-plan.md)保留业务依据；当前M106整轮、M60/M61锁存与19表保存已经接通，具体口径和接口变化以本日实施说明为准。

列表、历史明细、异常和周期统计已接入后台导出任务；按批输出CSV，周期统计以ZIP包含次数概要与高度数据，文件完整发布后才允许下载。设计依据见[导出方案](docs/data-export-plan.md)。

大厅为统一入口，下设清洗、命令、安全、统计四个业务服务。每个服务委派自己的规则/状态辅助类；系统状态只在清洗模块维护，命令状态只在命令模块维护。设备管理与DAO不反调上层。职责、目录、单向调用图和四条流程见 [核心架构说明](docs/architecture.md)。

依据PLC点位表整理的点位取舍、系统/单机五态、每次清洗与历史保存建议见[点位与历史方案](docs/plc-data-and-history-plan.md)。该文档只讨论方案，不表示功能已实现。

页面字段依据见[页面驱动设计](docs/database/page-driven-database-design.md)，最终表和字段以[当前数据库说明](docs/database/README.md)及SQL为准。

## 数据流与边界

```text
PLC → 底层驱动/快照队列 → 上层PLC表示入口 → 大厅 → 清洗编排 → 设备管理/系统状态/统计
首页 → SSE推送入口 → 大厅/安全检查 → 清洗服务只读状态 → 用户连接
用户申请 → 登录身份 → 大厅/安全服务 → 清洗编排 → 命令服务 → PLC写队列 → 写入结果回流
M106整轮/上行留点 → 持久待写 → 统计保存器 → DAO事务/检查点 → 历史查询/周期统计/导出
```

只输出当前采集能够提供的数值、状态和有效性；没有固定填充的轮次、挡位或告警判定。圆环高度不由四机均高替代，缺测不补零。

急停写响应确认仅表示请求已写入，设备是否停止仍需读取实际状态。页面参数和阈值已保存数据库版本及审计；现场读回点位尚未明确，保存与现场生效分别表达。

保留原历史批次保存，`cleaning.history.persistence-enabled`默认关闭。新流程由`cleaning.history.runtime-enabled`独立控制；迁移完成后启用，即可使用M106生产历史、0.5m曲线、全流/分日摘要和五周期统计。

实时状态和命令工作缓存为进程内数据，最近状态、正式历史、持久待写、操作审计和导出任务可从持久存储恢复。重启不续接旧轮次，也不自动重发不确定命令。没有自动删除业务历史的任务。
