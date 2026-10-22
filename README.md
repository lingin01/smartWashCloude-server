# SmartWash PLC 后端

Java 17、Spring Boot 3.2.5、MySQL 8 的单体后端，服务端直接通过 Modbus TCP 读写 PLC。
当前范围是一套 PLC 下的四台绞机和八路索拉力。设备标识统一为 `deviceId`，对应 `cleaning.plc.devices[].id` 和数据库 `device_id`。

## 数据库与环境

在自己的开发目标库执行 [schema.sql](src/main/resources/schema.sql)，只有账号、PLC拓扑、历史批次/记录/测量共7张表；脚本不创建或切换数据库，不创建默认账号。
已使用旧表结构的数据库，请先备份，再审阅 [数据库迁移说明](docs/database/README.md)。应用不会自动运行迁移。

设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和固定的 `JWT_SECRET`；不要把密码或密钥写入仓库。`DB_URL` 必须指定实际目标库。

```bash
mvn clean verify
mvn spring-boot:run
```

默认端口8080。PLC总开关和写入均默认关闭；确认实际地址、类型、倍率、单位、极性、业务编号后，通过外部 Spring 配置导入 [点位模板](src/main/resources/plc-points.example.yml)。模板不自动加载，也不能将现场标签数字直接作为协议地址。实际字段、保留的现场标签参考和采集边界见 [PLC接入说明](docs/plc.md)。

## 当前API

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

设备字段、控制返回值和URL已统一，前端必须同步更新。没有旧地址别名，也没有远程启动、复位、解除急停、暂停/恢复接口。

## 业务核心与数据流

大厅为统一入口，下设清洗、命令、安全、统计四个业务服务。每个服务委派自己的规则/状态辅助类；系统状态只在清洗模块维护，命令状态只在命令模块维护。设备管理与DAO不反调上层。职责、目录、单向调用图和四条流程见 [核心架构说明](docs/architecture.md)。

依据PLC点位表整理的点位取舍、系统/单机五态、每次清洗与历史保存建议见[点位与历史方案](docs/plc-data-and-history-plan.md)。该文档只讨论方案，不表示功能已实现。

## 数据流与边界

```text
PLC → 底层驱动/快照队列 → 上层PLC表示入口 → 大厅 → 清洗编排 → 设备管理/系统状态/统计
首页 → SSE推送入口 → 大厅/安全检查 → 清洗服务只读状态 → 用户连接
用户申请 → 登录身份 → 大厅/安全服务 → 清洗编排 → 命令服务 → PLC写队列 → 写入结果回流
已整理历史批次 → 大厅/统计服务 → 有界历史队列 → 统计专属保存器 → DAO事务 → 数据库
```

只输出当前采集能够提供的数值、状态和有效性；没有固定填充的轮次、挡位或告警判定。圆环高度不由四机均高替代，缺测不补零。

急停写响应确认仅表示请求已写入，设备是否停止仍需读取实际状态。参数写入后仍有版本持久化和回读核对待实现，不伪装为完整成功。

保留了已有历史批次事务保存实现，`cleaning.history.persistence-enabled` 默认关闭。目前还没有从实时快照识别作业/轮次、生成历史批次的生产链路，也没有历史列表、曲线、轮次/周期统计或可靠原始归档。当前统计接口仅计算实际接收样本，SSE已连接同一份清洗只读状态；没有用空接口冒充未完成的历史功能。

实时缓存和命令状态为进程内数据，重启不能恢复。历史保存范围和年限需另行确定，当前没有自动删除业务历史的任务。
