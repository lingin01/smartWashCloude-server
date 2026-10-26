# 数据库结构与升级

更新：2026-10-27。当前共19表：16张目标/兼容表及3张原历史表。M106系统整轮为主记录，0.5m上行留点、全流/分日摘要、五周期统计、严重事件、配置和审计已接入Java保存及查询。完整说明见[后端实施](../updates/2026-10-27/backend-implementation.md)。

| 场景 | 文件 |
|---|---|
| 空库首次安装 | [schema.sql](../../src/main/resources/schema.sql) |
| 原7表device_id基线 | [7→19增量升级](migrations/V20261026__upgrade_cleaning_v2.sql) |
| 上一版2026-10-26的17表 | [17→19运行流程升级](migrations/V20261027__m106_runtime.sql) |
| 维护定义 | [新增表源](../../src/main/resources/db/cleaning-v2-tables.sql)、[旧7表基线](../../src/main/resources/db/legacy-compatible-base.sql)、[原17表新增部分基线](../../src/main/resources/db/cleaning-v2-20261026-baseline.sql) |
| 生成与一致性检查 | [生成器](../../tools/database/build_schema_v2.py)，支持`--check` |

要求MySQL 8.0.16及以上。执行前选择实际目标数据库、备份并暂停应用写入。按来源选择一个升级入口；空库脚本不代替已有数据库迁移。脚本先预检已知结构，再改表；不删除原数据、不伪造旧曲线。DDL自动提交，中断后修正原因再重跑，不能依赖事务撤销全部DDL。未知或更早的box_id结构须另行核对，不运行历史清理脚本尝试修复。

新表`cleaning_runtime_checkpoint`与本轮/采样在同一事务提交，重放不重复统计；`cleaning_event_run`关联跨轮持续故障。`cleaning_cycle_record`为兼容保留，新流程不再生成第二套M204轮次计数。

应用仍设置`spring.sql.init.mode=never`，不会自动升级业务数据库。迁移完成后配置`cleaning.history.runtime-enabled=true`启用新保存、查询和统计；待写及导出目录需持久保存。实际业务库尚未变更。

已在临时MySQL 8.0.37验证新库、7表升级、17表升级、重复执行和原数据保留，并验证真实Java事务保存/回滚、查询、五周期统计、配置幂等和CSV/ZIP导出。最终记录见[验证进度](../updates/2026-10-27/implementation-progress.md)。

容量与粒度依据见[每0.5m历史与一年容量](height-history-period-capacity-20261025.md)。统计摘要不替代原始点，平台周期次数来源于保存的M106主记录，PLC累计次数仅作实时显示。
