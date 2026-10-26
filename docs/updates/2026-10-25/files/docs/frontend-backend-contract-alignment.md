# 前后端契约对齐：2026-10-25页面与PLC事件版本

本轮依据[全部页面新报告](reference/frontend-pages-report-20261025.md)和[PLC告警红字说明](reference/plc-alarm-revision-20261025.md)。第一份附件虽保留20261024文件名，正文已是2026-10-25版；它取代旧截图和旧统计双表的页面现状。此前原文仍保留在reference供追溯，不与本轮目标并列执行。

先看[业务总览](business-flow-and-page-data-overview.md)，按需查[新版图表](detail-force-comparison-processing.md)、[PLC事件/阈值](plc-alarm-and-threshold-processing.md)、[数据库设计](database/page-driven-database-design.md)。对接实施直接使用[Controller清单](controller-api-specification.md)与[接口输入/返回](external-api-contract.md)；当前Java真实行为单独看[当前接口参考](current-api-reference.md)。

## 1. 本轮更新后的职责

| 内容 | 本轮统一决定 |
|---|---|
| PLC | 判断拉力状态、普通/严重错误及现场过程结束；执行实际生效阈值和联锁 |
| 清洗模块 | 解释明确PLC事实，唯一维护系统、运行、事件身份/过程，编排读取与操作 |
| 命令模块 | 请求幂等、缓冲下发及命令状态；写应答不等于现场生效 |
| 安全模块 | 真实登录身份、系统归属与读取/操作权限，必要规则在所属模块检查一次 |
| 统计模块 | 可靠保存、事件峰值整理、曲线关联/合计、预计算、查询及导出；不以拉力比较阈值自行新建故障 |
| 前端 | 显示PLC/后端结论，筛选、选择、格式化、图表交互及简单提醒；不补造故障、总力、历史结果或平均 |

大厅仍是上层入口，每个业务门面委派本模块辅助类；设备管理/DAO不回调上层，不增加第五业务服务。新增Controller仅是表示入口，不把点位、事件状态机、SQL或统计都放进Controller。

## 2. 最新页面变化与接口取舍

| 页面/内容 | 更新后方案 |
|---|---|
| 动力详情 | 设备查询快照＋单机多次/周期图；右侧是所选曲线逐点表，合计由后端ROPE_TOTAL提供 |
| 系统单次详情 | 四台设备可选、ROPE1/ROPE2/SERVO三来源，最多12条；无合计/四机平均按钮，右侧同样是点表 |
| 删除的旧双表 | statistics/rawStatistics/ropeExtrema/defaultUnitId不再为图表必返；已有摘要保留供运行头、归档和报表 |
| 点表完整性 | 同接口resolution=OVERVIEW/RAW及cursor；概览不冒充全点，逐批完成后才称请求范围已全部读取 |
| 采样身份 | sampleKey是目标稳定关联键，sampleFrameId是实际帧；平均桶没有现场帧，不按最近时间或同高度补配 |
| 运行头极值 | 本生产UP记录全系统8条单绳有效样本集合中的最大/最小，不含合计/伺服，不随图例或winchCode改变 |
| 系统历史列表 | winchCode仅导航上下文，不把系统记录筛成单机；一系统记录仍不是四台计四次 |
| 告警页 | 正式改用alarms/latest、alarms/history；旧alarm-dashboard、统计卡、处理/恢复操作退出页面要求 |
| 事件维护 | PLC同一连续过程始终同alarmCode，持续/升级/结束更新revision；断联和跨RUN不自动结束/拆分 |
| 阈值编辑 | 新增winch/tension-thresholds两路请求；PENDING保留旧确认值，APPLIED必须PLC两路真实确认 |
| 归档 | 使用新报告确认的TENSION/SPEED、WEEK/MONTH/QUARTER/YEAR及五种比较方式；三组日期独立传输，不能用当前compareStart/compareEnd猜完整范围 |
| 只读用户 | username/nickname/roles/lastLoginAt/createdAt；最近登录UNKNOWN不能显示从未登录，角色名称和筛选由后端给出 |

新附件当前图表分组COMPLETED仅指已结束，可能对应故障终止；目标通过recordState与最终runResult分开表达。前端结果COMPLETED/INCOMPLETE/FAULT_TERMINATED按真实事实映射，未知或仍进行时null并给说明，不改变内部状态事实。

## 3. Controller对接必须补齐的差异

本轮列全17个页面方法，加9个必要传输/后续查询/导出方法，共26个方法/路径组合。四个Auth地址已经存在，但目标行为仍待适配；其他目标路径尚未在Java实现，不能当作已联调。

| 当前事实/前端请求 | 目标衔接 |
|---|---|
| 后端deviceId、kg、STANDBY、NON_NULL | 页面systemCode、t、IDLE、显式null，边界转换；内部主键与真实原值保留 |
| login返回毫秒，前端新版按秒消费 | login/refresh目标expiresIn明确秒，需实际转换；当前参考继续记录毫秒，不能只改说明 |
| 旧写接口HTTP202同时body.code=202，新前端只认code200 | 新目标HTTP202受理但业务code=200，执行阶段在data；失败保持明确非200 |
| 新路径尚无现有SecurityConfig保护 | /api/command-center/**、/api/users及身份读取需要实际鉴权、账号状态和资源归属，不能沿用permitAll |
| 前端急停/参数/阈值尚未齐备技术字段 | 补requestId，配置修改补expectedVersion；取真实savedVersion，版本未就绪不猜0 |
| 参数保存不等于生效 | 三参数与阈值分别维护saved/applied和明确回执；详情设定速度仍须真实确认，不用实际速度替代 |
| 图表平铺points与前端时间排序 | 目标segments保留用途/缺口，按采集序返回，页面适配sampleKey与RAW续读 |
| 当前日期目录单次返回 | 目标独立轻量日期游标，前端须读完才称全历史；不从本页十条猜日期 |
| 当前告警等级兼容MEDIUM、部分字段可缺 | 正式NORMAL/SERIOUS来自PLC；未知null＋说明，revision真实递增；不把缺省当无身份 |
| 当前用户null登录时间代表从未登录 | 目标lastLoginState=KNOWN/NEVER/UNKNOWN区分事实，UNKNOWN不冒充NEVER |

告警CUSTOM日期是上海首尾包含，内部转次日零点右开；运行startTime/endTime仍右开，不能统一误改。这些是查询参数语义，不是采样时间校验。

## 4. 本轮继续保留的业务边界

单PLC、四机八索、Modbus TCP直连，不恢复盒子/MQTT链路。所有已选UP用途保存原始，默认生产统计只取PRODUCTION_UP；正常DOWN仅实时，异常有限事件点与必要峰值标量。新前端模拟可画正常下降，不代表后端已有完整下降归档，本轮没有撤销此前上行存储决定。

RUN仍为生产上行单程，不凭页面“一轮”字样补成完整往返CYCLE。无确定点位依据时不写D200/D202阶段值、M204通用完成判断、定时启动来源、固定27/30m限位或D3=50五次明细。M50急停仍仅写1且地址/协议待核实，写应答不证明停止；远程启动、复位、解除急停、暂停/恢复、单轴升降仍不开放。

采集数据不做时间早晚、年龄、时钟偏差或过期校验；序号/源身份用于顺序与去重。事件更新用revision防回滚，时间用于说明、曲线及日历，不以浏览器故障判PLC离线。

次数及结果继续按开始日，测量按采集日，事件按明确实际/首次观测起点，可靠运动区间跨日拆分。todayCompletedCount仍是今日开始记录中的确认完成数；记录历时不等于有效运动工时，四机工时和不等于系统历时。原始与事件长期保存，历史月统计预先计算，查询有界，不把数据库全部给前端。

后台导出任务与一致快照设计继续保留。附件当前浏览器CSV仅是前端现状，不能用分页拼表替代后端完整导出，也不让图例隐藏删掉导出原始。首页目标仍为首次HTTP＋每秒完整SSE，详情按需查询；前端报告尚未接生产推送不能算后端能力完成。

## 5. 尚需事实来源，不能由文档代替的部分

PLC普通/严重映射、同过程变化/结束信号、控制器会话、阈值地址/单位/回读协议仍需明确；没有协议不能精确合并所有事件或返回阈值APPLIED。峰值整理需说明PLC提供/已观测集合及缺口，不能声称掌握漏采过程。

归档单次0～100%对齐没有可信机械限位/进度依据时返回不可比较及原因，不用高度除30或样本序号造进度。系统独立当前高度、阈值、维护、启动方式和处理次数无来源则null；有已确认PLC错误仍照实显示。

本轮只改文档。附件报告的300项前端测试和9项浏览器模拟检查不是本Java仓库本轮验证；未改Controller、权限配置、点位或数据库，也没有宣称真实设备已接通。
