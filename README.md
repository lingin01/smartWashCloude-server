# SmartWash PLC 后端

Java 17、Spring Boot 3.2.5、MySQL 8 的单体后端，服务端直接通过 Modbus TCP 读写 PLC。
当前范围是一套 PLC 下的四台绞机和八路索拉力。当前内部设备标识为 `deviceId`，对应 `cleaning.plc.devices[].id` 和数据库 `device_id`；新版页面目标统一使用公开 `systemCode`，由后端映射内部身份。

## 文档从哪里开始看

最新统一口径先看[实时、M106整轮历史与历史统计](docs/updates/2026-10-26/realtime-history-statistics-overview.md)：M106开始/结束一条系统记录，M60/M61锁存决定三种结果，检修只实时，上行每0.5米留点，周期次数由已保存主记录统计。最新点位与统计分别有配套文档。本轮为规则整理，未修改业务代码或SQL。

2026-10-26先前已落实[数据库优化及17表清单](docs/updates/2026-10-26/README.md)，保留旧3张历史表。该版SQL按旧“一次上行＋所属往返”语义编制；本轮M106主记录口径需在后续实施中同步适配，先前SQL验证不表示新流程已接通。现场数据库未升级。

采样与容量依据见[每0.5m上行历史与一年容量](docs/database/height-history-period-capacity-20261025.md)；[原7表与目标问题评审](docs/database/table-review-20261025.md)保留为历史设计依据，最新执行入口见[数据库说明](docs/database/README.md)。

2026-10-25现场补充先看[实时点位与换算核对](docs/plc-realtime-point-review-20261025.md)和[PLC到实时、历史与存储的主流程](docs/plc-realtime-history-main-flow-20261025.md)：更新M3极性、独立方向、编码器标定、M15和M204；历史保持一次上行一条并关联往返轮次。两份文档为分析依据，运行代码尚未适配。

当前逐项遗漏见[实时与历史数据遗漏清单](docs/plc-data-omissions-20261025.md)：正常读点直接处理，通信状态统一由PLC TCP连接层提供，重点补齐点位到业务快照、历史原始点和运行/轮次关联的数据传递。

用户提供的信捷PDF已用于[Modbus TCP报文核对](docs/modbus-tcp-document-check-20261025.md)，设备默认单次寄存器读写按120个限制处理。站号已确认为1，IP按现场局域网配置；M区地址映射及32位字序仍需补齐。

本轮已实现页面 Controller，先读[Controller 实现与接入状态](docs/page-controller-implementation.md)：26 个目标入口中，10 个连接现有能力，16 个明确返回业务未就绪。

先确认当前完成度，可读[系统完成情况与未完成清单](docs/project-completion-status.md)：已有代码、缺失的业务流程、PLC待确认项及分阶段交付标准。

1. [业务流程与页面数据总览](docs/business-flow-and-page-data-overview.md)：四服务、一次上行、页面与历史的完整流程。
2. [本次前后端职责对齐](docs/frontend-backend-contract-alignment.md)：2026-10-25页面与PLC事件变化；最新筛选补充见[10月26日附件](docs/reference/page-interface-and-force-filter-20261026.md)。
3. [PLC与业务统一方案](docs/unified-plc-business-and-data-plan.md)：点位、状态、运动用途、保存与统计规则。
4. [数据库设计](docs/database/page-driven-database-design.md)：身份映射、参数/阈值快照、历史与预计算。
5. [页面接口契约](docs/external-api-contract.md)：新页面地址、输入、输出、推送、告警、归档和导出。
6. [Controller接口实施清单](docs/controller-api-specification.md)：17个页面方法＋9个补充方法，逐项说明DTO、权限、服务调用及实现差距。

页面细节和文件处理按需看[页面与历史方案](docs/realtime-upward-history-and-detail-plan.md)、[导出方案](docs/data-export-plan.md)。当前已经实现什么，单独看[现有接口参考](docs/current-api-reference.md)和[代码架构](docs/architecture.md)。本轮[全部页面报告](docs/reference/frontend-pages-report-20261025.md)和[PLC红字原文](docs/reference/plc-alarm-revision-20261025.md)保留作依据，不将其中前端模拟和建议当成已接通PLC的事实。

新版详情图看[逐点表与来源处理](docs/detail-force-comparison-processing.md)：历史页三来源、动力页四来源，旧统计双表已退出目标。告警和新增阈值修改看[PLC事件与阈值流程](docs/plc-alarm-and-threshold-processing.md)：PLC判断、后端同号更新事件、PENDING/APPLIED确认。正式历史仍按生产上行统计，下行只留有限异常事件点。

## 数据库与环境

空库执行 [schema.sql](src/main/resources/schema.sql)，当前为14张目标表＋3张保留旧历史表，共17表；脚本不创建或切换数据库，不创建默认账号。已有7表基线使用独立增量升级脚本，不能用新库脚本代替升级。
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

登录和用户信息沿用 `/api/auth`，账号管理沿用 `/api/admin/user`。当前账号管理权限仍需要单独完善，不能将设备接口鉴权测试理解为全站生产权限验收。

当前设备接口没有旧地址别名，也没有远程启动、复位、解除急停、暂停/恢复接口。新首页已通过表示层适配systemCode、IDLE、t和显式null；系统映射使用外部cleaning.page.systems并核对账号授权。新历史等入口仍返回未就绪，旧deviceId、STANDBY、kg响应不作为新页面协议。

## 业务核心与数据流

从页面和PLC事实理解整个业务，先读[业务流程与页面数据总览](docs/business-flow-and-page-data-overview.md)：包含四服务分工、一次上行的生命周期、各页数据来源、原始历史与周期计算，以及跨日记录示例。

基于PLC点位和页面需求的[统一方案](docs/unified-plc-business-and-data-plan.md)保留业务依据，远程急停为M15；最新主记录按M106开始/结束、M60/M61锁存结果，详见前述统一口径。数据库已提供17表DDL，M106新口径及采集到新表的业务流程仍待实施；页面Controller与部分现有能力适配已完成。

列表、历史明细、异常和周期表格的导出设计见[导出方案](docs/data-export-plan.md)。导出任务表已纳入当前DDL，路由已建立并返回EXPORT_NOT_READY，任务生成和文件执行器尚未实现。

大厅为统一入口，下设清洗、命令、安全、统计四个业务服务。每个服务委派自己的规则/状态辅助类；系统状态只在清洗模块维护，命令状态只在命令模块维护。设备管理与DAO不反调上层。职责、目录、单向调用图和四条流程见 [核心架构说明](docs/architecture.md)。

依据PLC点位表整理的点位取舍、系统/单机五态、每次清洗与历史保存建议见[点位与历史方案](docs/plc-data-and-history-plan.md)。该文档只讨论方案，不表示功能已实现。

页面字段依据见[页面驱动设计](docs/database/page-driven-database-design.md)，最终表和字段以[当前数据库说明](docs/database/README.md)及SQL为准。

## 数据流与边界

```text
PLC → 底层驱动/快照队列 → 上层PLC表示入口 → 大厅 → 清洗编排 → 设备管理/系统状态/统计
首页 → SSE推送入口 → 大厅/安全检查 → 清洗服务只读状态 → 用户连接
用户申请 → 登录身份 → 大厅/安全服务 → 清洗编排 → 命令服务 → PLC写队列 → 写入结果回流
已整理历史批次 → 大厅/统计服务 → 有界历史队列 → 统计专属保存器 → DAO事务 → 数据库
```

只输出当前采集能够提供的数值、状态和有效性；没有固定填充的轮次、挡位或告警判定。圆环高度不由四机均高替代，缺测不补零。

急停写响应确认仅表示请求已写入，设备是否停止仍需读取实际状态。参数写入后仍有版本持久化和回读核对待实现，不伪装为完整成功。

保留了已有历史批次事务保存实现，`cleaning.history.persistence-enabled` 默认关闭。目前还没有从实时快照识别作业/轮次、生成历史批次的生产链路；历史列表、曲线和导出已有HTTP入口，但对应业务查询、周期统计和可靠原始归档仍未接通。当前统计接口仅计算实际接收样本，SSE已连接同一份清洗只读状态；没有用空接口冒充未完成的历史功能。

实时缓存和命令状态为进程内数据，重启不能恢复。目标方案长期保留上行原始与异常历史，当前可靠归档闭环仍待实施；没有自动删除业务历史的任务。
