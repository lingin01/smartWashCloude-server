package com.company.project.vo.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 新页面的公开返回契约。可空字段明确输出 null，未知事实不填默认测量值。 */
public final class PageResponses {
    private PageResponses() {}

    @Builder(toBuilder = true) @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HomeSnapshot(
            String systemCode, String systemName, boolean available,
            String systemStatus, String systemStatusText, boolean statusValid, Boolean online,
            BigDecimal systemHeight, BigDecimal systemCurrentHeight, Boolean isMoving, String movementDirection,
            BigDecimal averageTension, BigDecimal averageSpeed, String captureId, String sequence,
            Instant updatedAt, Instant asOf, Instant pushedAt,
            List<Winch> winches, List<Sensor> sensors, List<FaultSource> faultSources, List<Notice> notices) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Winch(
            String winchCode, String winchName, Integer groupNo,
            String operatingStatus, String statusText, boolean statusValid,
            String direction, String directionText, BigDecimal speed, String speedSource, List<String> sensorCodes) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Sensor(
            String sensorCode, String sensorName, String winchCode, Integer groupNo, Integer ropeNo,
            String metric, String position, BigDecimal pressure, String unit,
            String status, String statusText, boolean statusValid, String quality, String missingReason,
            BigDecimal alarmThreshold, BigDecimal safetyValue) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FaultSource(String objectType, String objectCode, String objectName,
                              String faultCode, String reasonCode, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Notice(String code, String message, String objectType, String objectCode) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommandReceipt(
            String systemCode, String requestId, String commandCode, String commandStatus,
            String phase, boolean deviceExecutionConfirmed, String message, Instant createdAt, Instant updatedAt) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Settings(String startTime, Integer intervalMinutes, BigDecimal speedMPerMinute) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ParameterSettingsResponse(
            String systemCode, String startTime, Integer intervalMinutes, BigDecimal speedMPerMinute,
            String savedVersion, Settings savedSettings, String appliedVersion, Settings appliedSettings,
            String applyStatus, String requestId, String commandCode, String message, Instant asOf) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Threshold(String sensorCode, BigDecimal alarmThreshold, BigDecimal safetyValue) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ThresholdSettings(
            String savedVersion, List<Threshold> savedThresholds, String appliedVersion,
            List<Threshold> effectiveThresholds, String applyStatus, String commandCode, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ThresholdApplyResponse(
            String systemCode, String winchCode, String requestId, String commandCode, String status,
            String savedVersion, String configurationVersion, List<Threshold> effectiveThresholds,
            String confirmationState, String message, boolean demo) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Measurement(BigDecimal value, String unit, String sourceName, String quality, String missingReason) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record WinchDetailResponse(
            String systemCode, Winch winch, List<Sensor> sensors, BigDecimal currentHeight, String currentMode,
            BigDecimal setSpeedMPerMinute, String configurationVersion, Long todayCompletedCount,
            String countBasis, String countText, BigDecimal runningHours, String motionCoverage,
            Instant maintenanceAt, Measurement torqueForce, ThresholdSettings thresholdSettings,
            String statisticsState, List<Notice> notices, Instant updatedAt, Instant asOf) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TimeRange(Instant startAt, Instant endAt, boolean endExclusive) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DateRange(LocalDate startDate, LocalDate endDate, boolean endInclusive) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HeightLimits(BigDecimal min, BigDecimal max, String source, String configurationVersion) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Aggregation(String method, BigDecimal heightBucketSize, String heightRuleVersion,
                              String computedVersion, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChartPoint(
            String sampleKey, String sampleFrameId, String sequence, Instant time,
            BigDecimal height, BigDecimal heightFrom, BigDecimal heightTo, BigDecimal value, BigDecimal speed,
            String direction, String motionRole, String quality, Boolean valueValid, Boolean speedValid,
            String missingReason, Long sampleCount, Long speedSampleCount,
            String heightRuleVersion, String computedVersion) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChartSegment(String segmentCode, String motionRole, boolean gapBefore, List<ChartPoint> points) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChartUnit(
            String id, String name, String recordCode, String periodType,
            Instant startAt, Instant endAt, boolean endExclusive, String rangeLabel, Instant dataThrough,
            String runState, String recordState, String runResult, String runResultText,
            Boolean abnormal, boolean empty, String emptyReason, Long includedRunCount) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TrendSeries(
            String id, String unitId, String winchCode, String sourceRole, String sourceCode,
            String sourceSensorCode, String sourceSensorName, String metric, String periodRole,
            String name, String unit, boolean empty, String emptyReason, List<ChartSegment> segments,
            List<ChartPoint> points) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record WinchTrendResponse(
            String systemCode, String winchCode, String kind, String comparisonView,
            List<ChartUnit> units, List<TrendSeries> series, BigDecimal heightMin, BigDecimal heightMax,
            HeightLimits heightLimits, TimeRange dataRange, Instant asOf, Instant updatedAt,
            String resolution, boolean complete, String nextCursor, Long totalUnits, Integer loadedUnits,
            Integer loadedPointCount, Aggregation aggregation, List<Notice> notices) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EventSummary(
            String eventCode, String alarmCode, Boolean isAlarm, String recordCode,
            String objectType, String objectCode, String objectName, String direction,
            Instant occurredAt, String timeBasis, Instant actualOccurredAt, Instant firstObservedAt,
            String outcome, String outcomeText, Instant returnedAt, Instant resumedAt,
            Integer attemptCount, String reason, Boolean hasSnapshots) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Coverage(String objectCode, String sourceRole, String coverage, Long validSamples,
                           Long invalidSamples, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RunEntry(
            String id, String kind, String name, LocalDate startDate, LocalDate endDate,
            Instant startedAt, Instant endedAt, Instant firstObservedAt, Instant lastObservedAt,
            String state, String runResult, String completionKind, String runResultText, String statusText,
            String endReason, String endReasonText, String startMethod, String startMethodText, Long sequenceNo,
            Long durationSeconds, String durationText, Long motionDurationSeconds,
            BigDecimal setSpeedMPerMinute, Integer intervalMinutes, String startTime, String parameterVersion,
            BigDecimal maxTension, BigDecimal minTension, String unit, String extremaScope,
            Integer abnormalCount, Integer alarmCount, Integer handlingAttemptCount,
            Integer runCount, Integer completedCount, Integer normalCompletedCount, Integer recoveredCount,
            Integer faultTerminatedCount, Integer incompleteCount, List<EventSummary> exceptionEvents,
            boolean exceptionEventsComplete, Long eventTotal, Boolean hasDataGap, Boolean boundaryConfirmed,
            String operatorName, List<Coverage> dataCoverage) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RunFilters(String scope, Instant startTime, Instant endTime, boolean endExclusive,
                             String dateBasis, String runResult, String winchCode) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RunListResponse(
            String systemCode, List<RunEntry> entries, long total, int page, int pageSize, RunFilters filters,
            List<LocalDate> availableDates, boolean availableDatesComplete, String availableDatesNextCursor,
            String datesBasis, Instant asOf, Instant enabledFrom, TimeRange dataRange) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RunSeries(
            String id, String name, String winchCode, Integer groupNo, Integer ropeNo,
            String sensorCode, String sourceRole, String unit, boolean empty, String emptyReason,
            String quality, BigDecimal displayMin, BigDecimal displayMax,
            List<ChartSegment> segments, List<ChartPoint> points) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RunDetailResponse(
            String systemCode, RunEntry entry, List<RunSeries> sensors, List<RunSeries> servos,
            BigDecimal heightMin, BigDecimal heightMax, HeightLimits heightLimits,
            Instant asOf, TimeRange dataRange, String resolution, boolean complete, String nextCursor,
            Integer loadedPointCount, Aggregation aggregation, List<EventSummary> exceptionEvents,
            Long eventTotal, boolean eventsComplete, String eventNextCursor, List<Notice> notices) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SourceReading(String sourceRole, String sensorCode, BigDecimal value,
                                String unit, String quality, String missingReason) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record WinchReadings(String winchCode, BigDecimal height, BigDecimal speed,
                                String direction, List<SourceReading> sources) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SamplePointResponse(
            String systemCode, String recordCode, String sampleKey, String sampleFrameId, Instant time,
            List<WinchReadings> winchReadings, String quality, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlarmItem(
            String alarmCode, String eventCode, String systemCode, String deviceCode, String deviceName,
            String source, String type, String content, String runCode, String level, boolean levelConfirmed,
            Instant occurredAt, String timeBasis, Instant actualOccurredAt, Instant firstObservedAt,
            Instant updatedAt, Long revision, String eventState, Instant endedAt, String continuity,
            BigDecimal triggerValue, BigDecimal peakValue, Instant peakAt, String unit,
            String peakSource, String peakCoverage, Boolean hasDataGap,
            BigDecimal threshold, String thresholdKind, String configurationVersion,
            String direction, BigDecimal occurredHeight, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LatestAlarmResponse(
            String systemCode, Instant asOf, List<AlarmItem> recent, boolean hasAttention,
            String attentionScope, int recentWindowHours, Instant windowStart, Instant windowEnd,
            boolean complete, boolean demo) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlarmHistoryResponse(
            String systemCode, Instant asOf, DateRange historyRange, TimeRange effectiveRange,
            List<AlarmItem> list, long total, int page, int pageSize, boolean demo) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EventFilters(Instant startTime, Instant endTime, String recordKind,
                               String deviceCode, String direction, String recordCode) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EventHistoryResponse(
            String systemCode, List<EventSummary> list, long total, int page, int pageSize,
            Instant asOf, EventFilters filters) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EventSnapshot(String stage, String sampleKey, String sampleFrameId, String sequence,
                                Instant time, String direction, List<WinchReadings> winchReadings,
                                String coverage, String message) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EventDetailResponse(
            String systemCode, EventSummary event, AlarmItem alarm, List<EventSnapshot> snapshots, Instant asOf) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArchivePoint(
            String axisKey, String label, Instant time, BigDecimal progressPercent,
            BigDecimal tension, BigDecimal speed, Long normalAlarms, Long seriousAlarms) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArchiveRanges(ArchiveDateRange change, ArchiveDateRange current, ArchiveDateRange reference) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArchiveDateRange(LocalDate startDate, LocalDate endDate, boolean endExclusive) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MetricStatistics(BigDecimal sum, Long count, BigDecimal average,
                                   BigDecimal maximum, BigDecimal minimum, String unit) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArchiveSummary(
            BigDecimal average, BigDecimal maximum, BigDecimal minimum, String unit,
            BigDecimal changeRate, boolean comparable, String comparisonReason,
            MetricStatistics currentStatistics, MetricStatistics referenceStatistics) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArchiveResponse(
            String systemCode, String winchCode, String metric, String tensionMetric, String period,
            String comparisonType, String comparisonTitle, String comparisonAxis, Long historySampleCount,
            List<ArchivePoint> changePoints, List<ArchivePoint> comparisonCurrent, List<ArchivePoint> comparisonPrevious,
            ArchiveRanges requestedRanges, ArchiveRanges effectiveRanges, List<String> referenceRecordCodes,
            TimeRange dataRange, Instant asOf, ArchiveSummary summary, String statisticsState,
            boolean complete, String nextCursor, Aggregation aggregation, List<Notice> notices) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExportSelection(
            Instant startTime, Instant endTime, String recordCode, List<String> recordCodes, List<String> eventCodes,
            String winchCode, String deviceCode, String motionRole, String direction, String runResult, String levels,
            String period, LocalDate startDate, LocalDate endDate, String metric, String tensionMetric) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExportFile(String fileCode, String fileName, String mediaType, Long sizeBytes,
                             Long dataRows, String rowKind, boolean downloadable) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExportTableCount(String tableName, String rowKind, Long rows) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExportTaskResponse(
            String taskCode, String systemCode, String exportType, String state, String phase,
            ExportSelection filters, String unit, String templateVersion,
            Instant createdAt, Instant startedAt, Instant snapshotAt, Instant finishedAt,
            Long processedRows, Long outputRows, List<ExportFile> files, List<ExportTableCount> tableCounts,
            String errorCode, String errorMessage) {}

    @Builder @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExportListResponse(List<ExportTaskResponse> list, long total, int page, int pageSize) {}
}
