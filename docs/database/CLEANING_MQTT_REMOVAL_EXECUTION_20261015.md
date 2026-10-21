# 原服务端 MQTT 清理执行记录

日期：2026-10-15。实际目标：D:/javaweb/all/SmartWashCloud-Server。SmartWashLocal-Server 副本不修改。

## 已处理范围

- 移除 34 个活动 MQTT Java 文件：网络接入、传输、Topic、表示层、签名、DTO、专属消息缓冲及 Broker／配置 Bean。
- 按删除说明移除 8 个旧大厅／清洗／命令接口实现及旧 RuntimeDataCenter，保留 Cloud* 主路线。
- 移除 CommandAck、CommandDeliveryParam，以及两个 MQTT 专属测试和 application-flow-check.yml：合计删除 47 个文件。
- 清理主大厅接收回执入口、命令服务发送依赖、命令登记 applyReply、Broker 状态和 ACK 缓冲；保留 HTTP、安全、现有设备业务对象、热／温数据、历史 DAO 和查询。
- 删除 Maven 中 Paho／Moquette 两项依赖，删除开发配置的 MQTT 连接／Topic；其他认证及数据库配置保持原值。
- init.sql 仅移除新建旧设备表模板里的 mqtt_topic 列，不执行 SQL、不修改实际数据库。
- 新增 CleaningLocalCommandReadinessTest，覆盖两种允许操作的未就绪结果、查询归属、重复请求、并发重试和终态保护。
- 主命令服务只登记并返回 BLOCKED / PLC_DOWNLINK_NOT_READY；表示层按现有逻辑返回 HTTP 503，不产生假发送队列、不标记急停完成或参数已生效。
- 参数申请保留业务设置对象，不再编码成旧通信协议 JSON。boxId/boxCommandId 暂保留 API 与数据库兼容形状，boxCommandId 不生成虚假值。

## 未实现与验证限制

本轮是移除旧通信，不是实现整套 PLC 直连。内部 Message 消费、PLC Socket／点位、轮次观察、五状态、0.5 米历史及配置确认 DAO 按既有方案下一批实施。没有 PLC 通道时停止操作是有意的安全边界，不是联调完成。

执行静态残留引用检查、Java 项目内 import 检查、POM XML 解析、备份与文件哈希核对。未运行 Java 编译、JUnit、服务器、Broker、数据库或 PLC；新增测试尚未执行，不能据此宣称编译／运行通过。

既有 target 构建输出未自动清理，不可直接运行其中旧 class／旧打包文件验证本轮。后续允许编译时应先执行 clean，防止删除源码后仍扫描到旧 Bean。

## 恢复与未变更范围

实际删除和覆盖前，保存本轮每个目标文件的原内容、路径及 SHA256 到原项目 archive/mqtt-removal-20261015-* 备份目录，并输出部署清单。恢复时按清单核对当前修改，不盲目覆盖用户后续内容。

原 archive 历史资料、用户数据、运行日志、Git 元数据、既有 Markdown 和 SmartWashLocal-Server 副本不删除。规划文档记录全部目标能力，本记录只声明本轮实际完成部分。
