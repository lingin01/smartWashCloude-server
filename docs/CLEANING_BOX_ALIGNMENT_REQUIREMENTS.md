# 盒子对齐要求入口

2026-10-14：[盒子上传数据的云端处理流程](database/CLEANING_BOX_UPLINK_PROCESSING_FLOW.md)。实时、历史、告警、命令回执及三类未接入上传的说明，与历史存储文档统一放在docs/database，见 [目录入口](database/README.md)。

2026-10-13 补充：[历史数据存放流程](database/CLEANING_HISTORY_STORAGE_FLOW.md)、[历史表、类、方法与实施清单](database/CLEANING_HISTORY_STORAGE_IMPLEMENTATION.md)。说明500ms采样与0.5米留点的区别、本地保存先行、事务落库、保存ACK和补传；不表示这些待实现项已完成。

日期：2026-10-12。本轮只编写交互实施要求及云端代码约定，未修改运行代码。

共同协议以 [盒子端对齐云端的交互实施要求](D:/文件整理/桌面归档/正在处理项目/清洗/盒子/iot_box_cleaning/docs/CLEANING_BOX_CLOUD_ALIGNMENT_REQUIREMENTS.md) 为主，避免双端各自维护相互冲突的字段表。

重点：统一ID人工导入、不下发设备档案；固定accessCode；建议新3.0协议的状态/恢复/真实高度/有效性；上升0.5m真实留点；下降异常片段；轮次终态；实际配置回执；数据库提交后的业务ACK。3.0是待双端实施的目标，不是当前已上线能力。

- [云端接口与实现目录、MyBatis-Plus约定](CLEANING_SERVER_DEVELOPMENT_CONVENTIONS.md)
- [完整JSON示例集合](cleaning_box_alignment_examples.json)
- [14表基线](database/README.md)

当前云端仍有JdbcTemplate DAO、签名占位和未完成的协议字段/路由。后续按约定迁移，不能把本文当成这些代码已经实现。
