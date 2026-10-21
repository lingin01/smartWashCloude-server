package com.company.project.design;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Java 17 页面数据类设计稿。
 * 本文件位于 docs，仅供评审和拆分类参考，不注册控制器、不实现业务或数据库访问。
 * DTO 仅接收字段；校验、可信身份和资源解析复用现有 Converter/Param/大厅调用链。
 * 引用类型允许表达未知值；必填和条件必填规则以 README 及字段字典为准。
 * 落地时分别放入现有 dto.cloud / vo.cloud 包，复用已有类，不保留本设计容器。
 */
public final class CommandCenterContractDraft {
    private CommandCenterContractDraft() { }

    /** 现有首页查询；tankCode 需要解析为唯一系统及盒子 */
    public record HomeQueryDto(
            // 当前前端资源参数，不能直接当作盒子编号
            String tankCode) { }

    /** 单台动力设备详情查询 */
    public record WinchDetailQueryDto(
            // 资源选择参数
            String tankCode,
            // 必须属于当前系统的绞机
            String winchCode) { }

    /** 保留旧趋势查询，并补齐自然周期和分组筛选 */
    public record WinchTrendQueryDto(
            // 资源选择参数
            String tankCode,
            // 绞机编号
            String winchCode,
            // REALTIME 当前页面表示今日；CURRENT_RUN/WEEK/MONTH/QUARTER/YEAR
            String rangeType,
            // NONE/PREVIOUS_RUN/HISTORY_AVERAGE/HISTORY_BASELINES/PREVIOUS_PERIOD/YEAR_ON_YEAR
            String comparisonType,
            // WINCH_TENSION/TRACTION_ROPE_TENSION 是兼容键，实际来源靠 ropeId/sensorId
            String metric,
            // 旧调用使用 Q1/Q2/Q3/Q4
            String quarter,
            // 旧接口兼容选项
            Boolean allQuarters,
            // SENSOR/TOTAL/AVERAGE
            String viewType,
            // MONTH/QUARTER/HALF_YEAR；MIXED 为旧入口兼容值
            String yearGrouping,
            // 新增，用户选择的实际日期；单位/格式：yyyy-MM-dd
            String anchor,
            // 新增，服务端生成的轮次或周期选择项
            List<String> unitIds,
            // 新增，AUTO/KG/TON；兼容前端 T 时明确转换
            String displayUnit) { }

    /** 当前历史列表查询，保留现有表单字段 */
    public record HistoryListQueryDto(
            // 当前前端字段；仍需确认是否传 tankId 或 systemId
            String systemCode,
            // TODAY/MONTH/YEAR
            String scope,
            // 未传时使用上海当前日期；单位/格式：yyyy-MM-dd
            String anchor,
            // 从 1 开始
            Integer page,
            // 建议 1～100
            Integer pageSize,
            // MONTH/QUARTER/HALF_YEAR
            String yearGrouping) { }

    /** 当前历史详情查询 */
    public record HistoryDetailQueryDto(
            // 系统资源选择
            String systemCode,
            // 单轮稳定 ID 或服务端生成的周期 ID
            String recordCode,
            // 年度子记录分组方式
            String yearGrouping) { }

    /** 当前告警筛选条件 */
    public record AlarmDashboardQueryDto(
            // 资源选择参数
            String tankCode,
            // TODAY/7D/30D/CUSTOM
            String range,
            // 可选，当前系统的绞机编号
            String deviceCode,
            // CUSTOM 时必填；单位/格式：yyyy-MM-dd
            String startDate,
            // CUSTOM 时必填且不得晚于上海今天；单位/格式：yyyy-MM-dd
            String endDate) { }

    /** 归档页左右两张图的独立查询条件 */
    public record ArchiveQueryDto(
            // 资源选择参数
            String tankCode,
            // 可选绞机编号
            String winchCode,
            // TENSION/SPEED/ALARM
            String metric,
            // 旧传感器角色键
            String tensionMetric,
            // WEEK/MONTH/QUARTER/YEAR
            String period,
            // SELECTED_PERIODS/PREVIOUS_RUN/HISTORY_AVERAGE/PREVIOUS_PERIOD/YEAR_ON_YEAR
            String comparisonType,
            // 运行对比所选记录
            String recordCode,
            // 左图范围起点；单位/格式：yyyy-MM-dd
            String changeStart,
            // 左图范围终点；单位/格式：yyyy-MM-dd
            String changeEnd,
            // 右图当前比较范围起点；单位/格式：yyyy-MM-dd
            String compareStart,
            // 右图基期范围起点，当前参数不足表达两段完整范围；单位/格式：yyyy-MM-dd
            String compareEnd,
            // 新增，右图当前周期终点；单位/格式：yyyy-MM-dd
            String compareCurrentEnd,
            // 新增，右图基期终点；单位/格式：yyyy-MM-dd
            String comparePreviousEnd) { }

    /** 当前页面三项表单，转换后复用 CloudCleaningSettingsDto */
    public record ParameterSettingsForm(
            // 本地工艺启动时间；单位/格式：HH:mm
            String startTime,
            // 页面当前要求 >=30 且为30的倍数；单位/格式：分钟
            Long intervalMinutes,
            // 页面当前要求1～10整数，不能视作已确认现场限值；单位/格式：m/min
            BigDecimal speedMPerMinute) { }

    /** 新增公共采样可信度，不把缓存可读当成现场在线 */
    public record FreshnessVo(
            // 是否曾接收到可展示采样
            Boolean available,
            // FRESH/STALE/OFFLINE/UNKNOWN
            String freshness,
            // 盒子采样时刻；单位/格式：ISO-8601
            Instant sampleTime,
            // 云端接收时刻；单位/格式：ISO-8601
            Instant receivedAt,
            // 同一流内采样序号
            Long sequence,
            // 盒子采样流或启动标识，协议待补
            String streamId,
            // 本帧有效索通道数
            Integer validRopeCount,
            // 本帧有效绞机通道数
            Integer validWinchCount,
            // 是否完整同帧
            Boolean complete,
            // 盒子确认已应用的规则版本
            String policyVersionApplied) { }

    /** 对应 CleanScrapingWinch，并补充数据有效性 */
    public record CleanScrapingWinchVo(
            // 绞机稳定编号
            String winchCode,
            // 档案显示名称
            String winchName,
            // 配置槽位1～4
            Integer groupNo,
            // 兼容状态字段，包含IDLE/EMERGENCY_STOP
            String status,
            // RUNNING/MAINTENANCE/FAULT/OFFLINE；待扩展IDLE/UNKNOWN
            String operatingStatus,
            // 服务端中文显示文本
            String statusText,
            // UP/DOWN/STOP，未知时为空
            String direction,
            // 方向中文文本
            String directionText,
            // 同帧两索合计，由盒子明确提供；单位/格式：t
            BigDecimal currentLoad,
            // 实际速度；单位/格式：m/min
            BigDecimal speed,
            // 按配置槽位给出两传感器，缺绑定时报告不完整
            List<String> sensorCodes,
            // 当前数值是否有效
            Boolean valid) { }

    /** 对应 CleanScrapingPressureSensor；pressure 为历史字段名，语义为单索拉力 */
    public record CleanScrapingPressureSensorVo(
            // 传感器稳定编号
            String sensorCode,
            // 显示名称
            String sensorName,
            // 旧角色键，来自明确绑定
            String metric,
            // 牵引索拉力传感器
            String businessRole,
            // 所属绞机槽位
            Integer groupNo,
            // 绞机稳定编号
            String winchCode,
            // 配置位置
            String position,
            // 单索实测拉力，未知返回null；单位/格式：t
            BigDecimal pressure,
            // 兼容前端固定 t
            String unit,
            // 盒子生效的安全停机值，未确认返回null；单位/格式：t
            BigDecimal safetyValue,
            // 旧字段语义尚未确认，不虚构低拉力阈值；单位/格式：t
            BigDecimal thresholdMin,
            // 兼容预警值字段；单位/格式：t
            BigDecimal thresholdMax,
            // 盒子生效的预警值；单位/格式：t
            BigDecimal alarmThreshold,
            // NORMAL/WARNING/ALARM/OFFLINE，待扩展UNKNOWN/INVALID
            String status,
            // 新增，不能把索与传感器当成同一ID
            String ropeId,
            // 单路测量有效性
            Boolean valid,
            // 生效阈值版本
            String policyVersionApplied) { }

    /** 当前首页完整响应，推送可复用同一字段合同 */
    public record CommandCenterHomeVo(
            // 前端兼容资源编号
            String tankCode,
            // 罐体显示名称
            String tankName,
            // 兼容历史状态
            String tankStatus,
            // 中文状态
            String tankStatusText,
            // 粗粒度工况，不从拉力预警推断检修
            String systemStatus,
            // 仅表示云盒连接，不代表全部设备安全正常
            Boolean online,
            // 罐体机械总行程；单位/格式：m
            BigDecimal tankHeight,
            // 盒子组高度，未知为null；单位/格式：m
            BigDecimal currentHeight,
            // 盒子确认的运动事实
            Boolean isMoving,
            // 盒子确认方向
            String movementDirection,
            // 盒子完整八索帧平均值；单位/格式：t
            BigDecimal averageTension,
            // 盒子完整四机平均值；单位/格式：m/min
            BigDecimal averageSpeed,
            // 兼容显示时间，不代替sampleTime
            String updatedAt,
            // 四台绞机
            List<CleanScrapingWinchVo> winches,
            // 八路拉力
            List<CleanScrapingPressureSensorVo> sensors,
            // 新增明确系统编号
            String systemId,
            // 新增明确盒子编号
            String boxId,
            // 当前自动会话
            String sessionId,
            // 当前轮次
            Integer roundNo,
            // 新增采样完整性和新鲜度
            FreshnessVo dataQuality) { }

    /** 伺服电机换算拉力，不能直接将原始转矩标为吨 */
    public record TorqueForceVo(
            // 已由盒子换算的电机拉力，缺换算为null；单位/格式：kg或t
            BigDecimal value,
            // kg/t
            String unit,
            // 伺服电机来源说明
            String sourceName,
            // SERVO
            String sourceRole,
            // 机械参数及换算版本
            String calculationVersion,
            // 换算是否有效
            Boolean valid) { }

    /** 单绞机页面关联整组轮次，不创建4份独立清洗作业 */
    public record WinchRunRecordSummaryVo(
            // 全组轮次稳定ID
            String recordCode,
            // 开始时刻；单位/格式：ISO-8601
            String startedAt,
            // 结束时刻，当前轮为空；单位/格式：ISO-8601
            String completedAt,
            // RUNNING/COMPLETED
            String state,
            // 轮次实际终态
            String result,
            // AUTO/MANUAL是旧字段，不替代自动/检修归属
            String mode,
            // 运行秒数；单位/格式：s
            Long durationSeconds,
            // 服务端固定进度合同，未完成不拉满；单位/格式：%
            BigDecimal progressPercent,
            // 页面日期内显示序号，不做数据库主键
            Integer sequenceNo,
            // 新增自动会话ID
            String sessionId,
            // 新增会话内轮次号
            Integer roundNo) { }

    /** 对应 CommandCenterWinchDetailData */
    public record CommandCenterWinchDetailVo(
            // 独立电机换算拉力
            TorqueForceVo torqueForce,
            // 资源编号
            String tankCode,
            // 显示时间
            String updatedAt,
            // 当前绞机
            CleanScrapingWinchVo winch,
            // 本绞机两路传感器
            List<CleanScrapingPressureSensorVo> sensors,
            // 档案额定载荷；单位/格式：t
            BigDecimal ratedLoad,
            // 档案额定速度；单位/格式：m/min
            BigDecimal ratedSpeed,
            // 唯一权威累计运行计时，未知为null；单位/格式：h
            BigDecimal runningHours,
            // 最近真实维护时刻，未知为null；单位/格式：ISO-8601
            String maintenanceAt,
            // 实际高度；单位/格式：m
            BigDecimal currentHeight,
            // 上海今日完成的整组有效轮次
            Integer todayRunCount,
            // 当前轮次摘要
            WinchRunRecordSummaryVo currentRecord,
            // 上一轮次摘要
            WinchRunRecordSummaryVo previousRecord,
            // 统计查询参与记录数；不与遥测点数混同
            Long historySampleCount,
            // 新增采样状态
            FreshnessVo dataQuality) { }

    /** 统一自然周期范围，业务查询为左闭右开 */
    public record TrendRangeVo(
            // 含起点；单位/格式：ISO-8601
            Instant startTime,
            // 不含终点；单位/格式：ISO-8601
            Instant endTime,
            // Asia/Shanghai
            String timezone) { }

    /** 拉力展示坐标轴，不作为设备物理量程 */
    public record AxisHintVo(
            // 展示下界
            BigDecimal min,
            // 展示上界
            BigDecimal max,
            // 展示小数位
            Integer decimalPlaces) { }

    /** 高度坐标范围与拉力轴明确分开 */
    public record HeightRangeVo(
            // 实际有效高度下界；单位/格式：m
            BigDecimal min,
            // 实际有效高度上界；单位/格式：m
            BigDecimal max) { }

    /** 曲线点保留真实时间、高度、方向和缺测信息 */
    public record WinchTensionTrendPointVo(
            // 真实采样或聚合桶时刻；单位/格式：ISO-8601
            Instant time,
            // 轮次进度，周期点不得假造；单位/格式：%
            BigDecimal progressPercent,
            // 本轮经过秒数；单位/格式：s
            Long elapsedSeconds,
            // 测量高度，缺失返回null；单位/格式：m
            BigDecimal height,
            // 实际速度；单位/格式：m/min
            BigDecimal speed,
            // UP/DOWN/STOP
            String direction,
            // 缺测返回null；单位/格式：曲线unit
            BigDecimal value,
            // 桶内最小值；单位/格式：曲线unit
            BigDecimal min,
            // 桶内最大值；单位/格式：曲线unit
            BigDecimal max,
            // 参与统计的有效原始点数
            Long sampleCount,
            // GOOD/PARTIAL/INVALID/OFFLINE
            String quality,
            // 新增真实自动会话
            String sessionId,
            // 新增轮次
            Integer roundNo,
            // 新增本点配置版本
            String policyVersion) { }

    /** 对应 WinchTensionTrendSeries，独立伺服和双索来源不混同 */
    public record WinchTensionTrendSeriesVo(
            // SENSOR1/SENSOR2/SERVO
            String sourceRole,
            // 兼容前端角色键
            String metric,
            // 展示名称
            String name,
            // SENSOR必填；SERVO不得伪造传感器ID
            String sourceSensorCode,
            // 来源显示名
            String sourceSensorName,
            // CURRENT/COMPARISON/WEEK_BASELINE/MONTH_BASELINE/YEAR_BASELINE
            String periodRole,
            // 服务端生成的轮次或周期ID
            String unitId,
            // 按真实时间和阶段排序
            List<WinchTensionTrendPointVo> points,
            // 新增物理索标识
            String ropeId,
            // 新增绞机标识
            String winchId,
            // 电机力或派生指标计算版本
            String calculationVersion) { }

    /** 选择批次、周、月、季度、半年；空桶仍返回 */
    public record TrendUnitVo(
            // 服务端稳定选择ID
            String id,
            // 显示名称
            String name,
            // 日期范围文字
            String rangeLabel,
            // 起点；单位/格式：ISO-8601
            Instant startAt,
            // 终点；单位/格式：ISO-8601
            Instant endAt,
            // 有已确认异常
            Boolean abnormal,
            // 无符合口径的数据
            Boolean empty,
            // 实际统计截止时刻；单位/格式：ISO-8601
            Instant dataThrough,
            // 参与的原始轮次记录数
            Long sampleCount) { }

    /** 全范围汇总保持原始权重 */
    public record TrendSummaryVo(
            // 汇总范围
            TrendUnitVo unit,
            // 汇总曲线；高度分桶规则必须固定
            List<WinchTensionTrendSeriesVo> series) { }

    /** 直接满足当前 power-detail 页分组模型，取消WHOLE适配降级 */
    public record WinchTrendUnitSetVo(
            // BATCH/WEEK/MONTH/PERIOD/WHOLE
            String kind,
            // 完整可选项
            List<TrendUnitVo> units,
            // 带unitId的曲线
            List<WinchTensionTrendSeriesVo> series,
            // 有效高度下界；单位/格式：m
            BigDecimal heightMin,
            // 有效高度上界；单位/格式：m
            BigDecimal heightMax,
            // 数据截止时刻；单位/格式：ISO-8601
            Instant updatedAt,
            // 正式数据固定false
            Boolean demo,
            // 实际缺少能力的中文说明
            String capabilityNote,
            // 全范围加权汇总
            TrendSummaryVo summary,
            // 新增曲线力单位，t/kg
            String unit,
            // 新增TIME/HEIGHT/PROGRESS
            String axisType) { }

    /** 兼容现有生产趋势入口，不与单位集合响应混用 */
    public record CommandCenterWinchTensionTrendVo(
            // 资源编号
            String tankCode,
            // 绞机编号
            String winchCode,
            // 查看范围
            String rangeType,
            // 比较方式
            String comparisonType,
            // 角色键
            String metric,
            // 当前范围
            TrendRangeVo range,
            // 基期范围
            TrendRangeVo comparisonRange,
            // 采样或分桶说明
            String bucket,
            // t
            String unit,
            // 拉力展示轴
            AxisHintVo axisHint,
            // 高度展示轴
            HeightRangeVo heightRange,
            // 曲线列表
            List<WinchTensionTrendSeriesVo> series,
            // 原始统计量计算的当前均值；单位/格式：t
            BigDecimal currentAverage,
            // 基期均值；单位/格式：t
            BigDecimal comparisonAverage,
            // 当前减基期；单位/格式：t
            BigDecimal difference,
            // 基期0或缺失时为null；单位/格式：%
            BigDecimal changeRate,
            // 数据截止时刻；单位/格式：ISO-8601
            Instant updatedAt,
            // 新增可比较标志
            Boolean comparable,
            // 新增不能比较的原因
            String comparisonReason) { }

    /** 一次现场异常的处理结果，事件次数与尝试次数分开 */
    public record SystemHistoryExceptionEventVo(
            // 异常发生时刻；单位/格式：ISO-8601
            Instant occurredAt,
            // 原始上报尝试次数；最多5次为前端需求待PLC确认
            Integer attemptCount,
            // RECOVERED/FAILED；待扩展UNKNOWN/PROCESSING
            String outcome,
            // 新增稳定异常事件ID
            String eventId,
            // 新增确认恢复时刻；单位/格式：ISO-8601
            Instant recoveredAt) { }

    /** 当前 RUN=单轮；日/月/季度/半年/年=统计投影 */
    public record SystemHistoryEntryVo(
            // 轮次稳定ID或服务端周期ID
            String id,
            // RUN/DAY/MONTH/QUARTER/HALF_YEAR/YEAR
            String kind,
            // 显示名称
            String name,
            // 上海统计起始日期；单位/格式：yyyy-MM-dd
            String startDate,
            // 上海统计末日期，展示含终点；单位/格式：yyyy-MM-dd
            String endDate,
            // 单轮开始时刻；单位/格式：ISO-8601
            Instant startedAt,
            // 单轮结束时刻；单位/格式：ISO-8601
            Instant endedAt,
            // 持续秒数；单位/格式：s
            Long durationSeconds,
            // 实际SCHEDULED/MANUAL，缺证据为null
            String startMethod,
            // 当日显示序号
            Integer sequenceNo,
            // 中文结果文字
            String statusText,
            // 实际终态，需扩展当前COMPLETED/FAULT_TERMINATED二值模型
            String runResult,
            // 未知为空；已确认无异常为[]
            List<SystemHistoryExceptionEventVo> exceptionEvents,
            // 终态轮次总数
            Long runCount,
            // 完成总数=正常完成+异常恢复后完成
            Long completedCount,
            // 无异常且证据完整的完成轮次
            Long normalCompletedCount,
            // 异常恢复后完成轮次，按轮去重
            Long recoveredCount,
            // 故障终止轮次，其他终止不冒充故障
            Long faultTerminatedCount,
            // 异常事件数
            Long abnormalCount,
            // 去重设备告警事件数
            Long alarmCount,
            // 无轮次数据
            Boolean empty,
            // 新增会话ID
            String sessionId,
            // 新增轮次号
            Integer roundNo,
            // 新增实际结束原因
            String stopReason,
            // 新增事件/统计数据是否完整
            Boolean dataComplete,
            // 新增急停/人员结束等其他終态计数
            Long otherTerminatedCount) { }

    /** 全年总览先计算再分页 */
    public record SystemHistoryYearSummaryVo(
            // 统计年
            Integer year,
            // 全年轮次总数
            Long runCount,
            // 全年正常完成
            Long normalCompletedCount,
            // 全年恢复后完成
            Long recoveredCount,
            // 全年故障终止
            Long faultTerminatedCount,
            // 新增其他终态
            Long otherTerminatedCount,
            // 新增完整性
            Boolean dataComplete) { }

    /** 当前历史列表响应不是 PageResult.list */
    public record SystemHistoryListVo(
            // 当前页记录
            List<SystemHistoryEntryVo> entries,
            // 全条件记录数，不是当前页大小
            Long total,
            // 实际日期锚点；单位/格式：yyyy-MM-dd
            String anchor,
            // 服务端统计日期；保留当前前端日期格式；单位/格式：yyyy-MM-dd
            String asOf,
            // 首个有数据的上海日期；单位/格式：yyyy-MM-dd
            String enabledFrom,
            // 有实际数据的年份
            List<Integer> dataYears,
            // YEAR范围使用
            SystemHistoryYearSummaryVo yearSummary,
            // 新增数据实际截止时刻，不能以整日表示采样新鲜度；单位/格式：ISO-8601
            Instant dataThrough) { }

    /** 当前历史高度曲线，8索与4电机独立 */
    public record SystemHistoryCurveVo(
            // 稳定测点/曲线ID
            String id,
            // 显示名称
            String name,
            // 绞机槽位
            Integer groupNo,
            // 1/2；电机曲线为null
            Integer ropeNo,
            // 真实点或明确算法版本的统计曲线
            List<WinchTensionTrendPointVo> points,
            // 图表显示下界，未知为null
            BigDecimal displayMin,
            // 图表显示上界，未知为null
            BigDecimal displayMax,
            // 新增t/kg
            String unit,
            // 新增SENSOR1/SENSOR2/SERVO
            String sourceRole,
            // 新增派生计算版本
            String calculationVersion) { }

    /** 周期详情的日/月子时间段 */
    public record HistoryTimelineItemVo(
            // 周期子记录
            SystemHistoryEntryVo entry,
            // 该子区间索曲线
            List<SystemHistoryCurveVo> sensors,
            // 该子区间电机曲线
            List<SystemHistoryCurveVo> servos) { }

    /** 当前历史详情，独立于旧 CommandCenterRecordPageData */
    public record SystemHistoryDetailVo(
            // 轮次或周期主摘要
            SystemHistoryEntryVo entry,
            // 索曲线
            List<SystemHistoryCurveVo> sensors,
            // 独立电机力曲线
            List<SystemHistoryCurveVo> servos,
            // 高度下界；单位/格式：m
            BigDecimal heightMin,
            // 高度上界；单位/格式：m
            BigDecimal heightMax,
            // 当前前端RUN不使用；日/月等按条件查询
            List<CommandCenterAlarmVo> alarms,
            // 周期时间分组
            List<HistoryTimelineItemVo> timeline,
            // 下一层记录
            List<SystemHistoryEntryVo> children,
            // 新增数据完整性
            Boolean dataComplete,
            // 新增缺测或协议能力说明
            String capabilityNote) { }

    /** 兼容前端告警项，并补齐唯一事件ID和独立恢复/处置状态 */
    public record CommandCenterAlarmVo(
            // 告警类型码，不作为唯一事件ID
            String alarmCode,
            // 资源编号
            String tankCode,
            // 作用设备
            String deviceCode,
            // 作用传感器，可空
            String sensorCode,
            // 发生时绞机槽位
            Integer sourceGroupNo,
            // 发生时角色键
            String sourceMetric,
            // WARNING/PROTECTION，与严重等级分开
            String thresholdKind,
            // 中文分类
            String type,
            // 保留盒子中文原始提示
            String content,
            // NORMAL/MEDIUM/SERIOUS为页面兼容等级
            String level,
            // ACTIVE/RECOVERED/ARCHIVED为页面兼容状态
            String state,
            // FIELD_REQUIRED/AUTO_ARCHIVE
            String handling,
            // 实际触发值；单位/格式：unit
            BigDecimal triggerValue,
            // 发生时生效阈值；单位/格式：unit
            BigDecimal threshold,
            // 盒子事件发生时刻；单位/格式：ISO-8601
            Instant occurredAt,
            // 盒子确认恢复时刻；单位/格式：ISO-8601
            Instant recoveredAt,
            // 新增唯一事件ID
            String eventId,
            // 新增BOX_DEVICE/CLOUD_PLATFORM/BOX_SYSTEM
            String source,
            // 新增kg/t/m/min等明确单位
            String unit,
            // 新增发生时索ID
            String ropeId,
            // 新增发生时规则版本
            String policyVersion,
            // 新增盒子实际动作
            String actualAction,
            // 新增人工处置状态，不等于设备恢复
            String handlingState,
            // 新增状态同步版本
            Long revision) { }

    /** 去重告警按上海日期统计 */
    public record AlarmStatisticPointVo(
            // 日期显示
            String label,
            // 一般告警数
            Long normal,
            // 严重告警数
            Long serious) { }

    /** 告警类别统计 */
    public record CategoryCountVo(
            // 类别中文
            String name,
            // 去重事件数
            Long value) { }

    /** 前端筛选日期展示 */
    public record AlarmHistoryRangeVo(
            // 含起始日期；单位/格式：yyyy-MM-dd
            String startDate,
            // 含终止日期；单位/格式：yyyy-MM-dd
            String endDate) { }

    /** 当前告警大屏响应 */
    public record CommandCenterAlarmDashboardVo(
            // 资源编号
            String tankCode,
            // 统计日期；单位/格式：yyyy-MM-dd
            String asOf,
            // 正式数据为false
            Boolean demo,
            // 今日发生的设备告警
            Long todayCount,
            // 今日恢复数，恢复事件时间独立统计
            Long todayRecoveredCount,
            // 筛选范围内恢复数
            Long recoveredCount,
            // 实际筛选范围
            AlarmHistoryRangeVo historyRange,
            // 当前未恢复设备告警
            Long activeCount,
            // 当前严重未恢复设备告警
            Long seriousActiveCount,
            // 当前或最近去重事件
            List<CommandCenterAlarmVo> recent,
            // 筛选范围历史事件
            List<CommandCenterAlarmVo> history,
            // 日期统计
            List<AlarmStatisticPointVo> trend,
            // 类型统计
            List<CategoryCountVo> categoryCounts,
            // 新增历史总数
            Long historyTotal,
            // 新增是否截断，后续历史分页接口读取全部
            Boolean historyTruncated) { }

    /** 归档图表点；无数据不填0 */
    public record ArchivePointVo(
            // 桶显示标签
            String label,
            // 符合选择对象的拉力；单位/格式：t
            BigDecimal tension,
            // 实测速度；单位/格式：m/min
            BigDecimal speed,
            // 一般事件数
            Long normalAlarms,
            // 严重事件数
            Long seriousAlarms) { }

    /** 归档统计汇总，0基期不可比较 */
    public record ArchiveSummaryVo(
            // 原始加权均值
            BigDecimal average,
            // 原始有效最大值
            BigDecimal maximum,
            // 原始有效最小值
            BigDecimal minimum,
            // 基期无效为null；单位/格式：%
            BigDecimal changeRate,
            // 一般事件数
            Long normalAlarmCount,
            // 严重事件数
            Long seriousAlarmCount,
            // 新增可比性
            Boolean comparable,
            // 新增不可比较原因
            String comparisonReason) { }

    /** 当前数据归档响应 */
    public record CommandCenterArchiveVo(
            // 资源编号
            String tankCode,
            // 当前绞机
            String winchCode,
            // TENSION/SPEED/ALARM
            String metric,
            // WEEK/MONTH/QUARTER/YEAR
            String period,
            // 比较方式
            String comparisonType,
            // 服务端比较说明
            String comparisonTitle,
            // TIME/PROGRESS
            String comparisonAxis,
            // 参与的原始轮次记录数
            Long historySampleCount,
            // 左侧变化图
            List<ArchivePointVo> changePoints,
            // 右侧当前周期
            List<ArchivePointVo> comparisonCurrent,
            // 右侧基期
            List<ArchivePointVo> comparisonPrevious,
            // 汇总值
            ArchiveSummaryVo summary,
            // 新增明确当前指标单位
            String unit) { }

    /** 页面输入规则与现场工艺限值分开 */
    public record ParameterLimitsVo(
            // 当前前端最小30分钟；单位/格式：min
            Long minIntervalMinutes,
            // 当前前端步长30分钟；单位/格式：min
            Long intervalStepMinutes,
            // 当前前端1，现场确认后由配置返回；单位/格式：m/min
            BigDecimal minSpeedMPerMinute,
            // 当前前端10，现场确认后由配置返回；单位/格式：m/min
            BigDecimal maxSpeedMPerMinute,
            // UI_CONTRACT/FIELD_CONFIRMED
            String source,
            // 规则版本
            String version) { }

    /** 页面配置查询；settings只代表盒子已应用值 */
    public record ParameterSettingsPageVo(
            // 明确目标系统
            String systemId,
            // 明确目标盒子
            String boxId,
            // NOT_SYNCHRONIZED/APPLIED/UNKNOWN
            String state,
            // 盒子已应用版本；未同步为空
            Long version,
            // 已应用配置；未同步为空
            ParameterSettingsForm settings,
            // 来自工艺配置，禁止直接采用演示默认值
            ParameterSettingsForm defaults,
            // 表单输入约束
            ParameterLimitsVo limits,
            // 可信版本和目标是否已同步
            Boolean writable,
            // 在途更新请求
            String pendingRequestId) { }

}
