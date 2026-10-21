# 清洗云端代码目录与MyBatis-Plus实施约定

日期：2026-10-12。本文记录用户要求，当前不移动类、不替换DAO、不执行SQL；后续迁移保持现有业务链路、基类、构建方式和线程/缓冲框架。

## 1. 分层和接口目录

网络接入→表示层DTO/Param转换→CloudLobbyService→业务服务→DAO/缓冲→BO→表示层VO，网络不直接访问Mapper，业务不创建网络线程。新增接口按服务/DAO聚合职责，不每个功能一个接口。

目标目录约定：

```text
com/company/project/
  service/
    interfaces/       业务服务接口
    impl/             业务服务实现
  repository/cleaning/
    interfaces/       业务DAO接口
    impl/             手写DAO实现，内部注入MyBatis-Plus Mapper
    mapper/           @Mapper接口，实际代理由框架生成
  domain/entity/cleaning/   表实体
  domain/bo/               业务对象
  domain/param/cloud/      已转换的业务参数
  dto/cloud/、dto/mqtt/    外部输入
  vo/cloud/               页面输出
  network_access_layer/、presentation/、info/ 沿用现有模块
```

interfaces不是Java关键字interface，因此用复数作为合法包名。DTO/BO/Entity/Converter不为了目录要求强行新增一套接口。已有service接口当前在service根目录，实现在impl；已有清洗DAO和Jdbc类混在repository/cleaning，需要后续单独迁移，不能说现在已经全部分好。

改包会影响import、注入、扫描、测试等，迁移时逐组更新并验证；本轮不进行这种跨文件改包。网络/线程/缓冲的既有接口不顺带全项目搬家。

## 2. 数据层主要使用MyBatis-Plus

项目已有MyBatis-Plus 3.5.6及MySQL驱动。新增清洗表优先按MyBatis-Plus实现；当前JdbcCleaningDeviceDao/JdbcTelemetryHistoryDao仍是JdbcTemplate，需分批替换而不是宣称已迁移。

业务DAO接口保持既有方法和调用关系，例如CleaningDeviceDao.findByBoxId/findBySystemId/insertNew、TelemetryHistoryDao.insertBatch/findByRange。业务服务不知道底层是Mapper还是JdbcTemplate。

Mapper接口继承BaseMapper<对应Entity>并标@Mapper。**Mapper本身是接口，没有手写MapperImpl；MyBatis生成代理。**手写实现放DAO impl中，组织同事务的多个Mapper操作，避免每个查询方法再包装一层服务。

Entity使用对应表/列映射，复合业务键不要强行假装单列自增ID。BaseMapper无法直接表达的复合键更新/批量插入/联表查询采用参数化自定义Mapper SQL，仍属于同一MyBatis技术栈。ID由既定云端/盒子身份生成策略提供，不随意改成数据库自增。

只扫描带@Mapper的Mapper接口。普通DAO接口不得标@Mapper；当前@MapperScan按repository树与注解过滤，迁移后仍核对递归扫描和Bean唯一性，不能同时注册新旧两份实现造成重复Bean。

## 3. 事务和数据处理规则

- 保持Spring@Transactional，事务边界在业务/DAO聚合操作，不把HTTP/MQTT等待包进数据库事务。
- 历史批次、记录、测量同事务保存；核对同batchId内容指纹后去重，提交后才发送业务ACK。
- 参数申请入命令记录，只有盒子实际生效值才能入settings_revision。
- 命令requestId及告警operationId幂等；版本有条件更新，迟到回执不能回退状态。
- 数值BigDecimal/明确单位，时间按V1设计核对UTC，未知值null；前端不计算单位/轴/均值。
- accessCode不进入普通Entity输出VO、日志或命令业务payload；凭据数据访问独立。
- 新增/改动代码优先中文注释，说明业务意图、事务和边界，不逐行翻译赋值。

14表定义在docs/database。现有数据库必须先核对再生成迁移；MyBatis-Plus不能替代建表、唯一键、业务校验或事务，也不能自动补齐当前缺失的曲线、配置快照和保存ACK。

## 4. 下一步实现批次

先固定双端协议及Entity映射；再逐组迁移接口/实现目录和DAO；每组用现有调用链验证。设备关系→作业/轮次→历史及ACK→命令/实际配置→告警/恢复。最后再做页面查询及统计，不改变整个项目框架。

本次仅文档；没有代码迁移、MyBatis集成测试或数据库执行结果。
