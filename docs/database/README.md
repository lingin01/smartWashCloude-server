# 数据库结构与升级

后续业务定义以[实时、M106整轮历史与统计](../updates/2026-10-26/realtime-history-statistics-overview.md)为准：一次M106周期一条主历史。当前17表SQL按先前上行/往返分离语义编制，需后续一起核对M106/M60/M61字段、主从关联和计次口径，本轮文档整理没有修改DDL或升级实际数据库。

2026-10-26已将每0.5m上行历史、单次/分日摘要、日历统计和操作审计落实为SQL。仓库当前建表结构为**17张表：14张目标表＋3张保留的旧历史表**。完整清单见[本次数据库优化说明](../updates/2026-10-26/README.md)。现场库尚未升级，新历史业务尚未接入。

| 场景 | 文件 | 说明 |
|---|---|---|
| 空库首次安装 | [schema.sql](../../src/main/resources/schema.sql) | 完整17表；先选定空库；不建库、不创建默认账号 |
| 现有7表、身份列为device_id的库 | [V20261026升级脚本](migrations/V20261026__upgrade_cleaning_v2.sql) | 扩展3张拓扑表、新增10张表；保留旧数据；可重复执行 |
| 更早的box_id等旧结构 | [历史清理脚本](migrate_legacy.sql) | 单独审阅；不是V2升级入口；检测到新链路表会拒绝执行 |
| 维护SQL定义 | [旧7表兼容快照](../../src/main/resources/db/legacy-compatible-base.sql)、[V2新增表定义](../../src/main/resources/db/cleaning-v2-tables.sql)、[生成器](../../tools/database/build_schema_v2.py) | 生成新库和升级脚本，避免结构漂移 |

实际升级前先备份并暂停应用写入。脚本先校验已知基线、槽位、公共编号冲突和已有V2表关键结构，再执行增量DDL；不删除旧表、清空数据、伪造公共编号或把旧摘要变成虚假曲线。MySQL DDL自动提交，中断后处理报告的问题再重跑，不能依赖事务回滚全部DDL。IF NOT EXISTS不能代替升级机制。

要求MySQL 8.0.16及以上。升级沿用旧拓扑标识的utf8mb4排序规则；新库统一utf8mb4_general_ci。自定义或未知结构需要单独审阅，预检不能代替任意数据库版本间的迁移设计。

应用明确配置`spring.sql.init.mode=never`，不会随启动执行这些SQL。当前DAO仍保存旧历史批次/分段/测量摘要，新表采集整理、事务保存器、查询和Controller待后续接入。PLC连接与写入总开关仍默认关闭。

数据粒度与容量依据见[高度历史与一年容量](height-history-period-capacity-20261025.md)；[旧表评审](table-review-20261025.md)和[页面驱动设计](page-driven-database-design.md)保留作为设计演变依据，实际字段以当前SQL为准。
