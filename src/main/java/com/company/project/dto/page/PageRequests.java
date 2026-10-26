package com.company.project.dto.page;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 页面 HTTP 输入。只校验请求格式、必填条件与响应预算，不判断采样时间是否过期。 */
public final class PageRequests {
    private PageRequests() {}

    @Data
    public static class SystemQuery {
        @NotBlank private String systemCode;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class WinchQuery extends SystemQuery {
        @NotBlank private String winchCode;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class WinchTrendQuery extends WinchQuery {
        @NotBlank @Pattern(regexp = "FORCE_COMPARISON") private String viewType;
        @NotBlank @Pattern(regexp = "COUNT|DAY|MONTH|QUARTER|HALF_YEAR|YEAR") private String comparisonView;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate periodStartDate;
        @NotBlank @Pattern(regexp = "OVERVIEW|RAW") private String resolution = "OVERVIEW";
        private String cursor;
        @Min(1) @Max(2000) private int maxPointsPerSeries = 500;
        @Min(1) @Max(10) private int unitPageSize = 5;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class PagedSystemQuery extends SystemQuery {
        @Min(1) private int page = 1;
        @Min(1) @Max(100) private int pageSize = 10;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class RunListQuery extends PagedSystemQuery {
        @NotBlank @Pattern(regexp = "RUN") private String scope;
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime startTime;
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime endTime;
        @NotNull @AssertTrue private Boolean endExclusive;
        @NotBlank @Pattern(regexp = "STARTED|FIRST_OBSERVED") private String dateBasis = "STARTED";
        private boolean includeAvailableDates;
        private String availableDatesCursor;
        @Pattern(regexp = "NORMAL_COMPLETED|ABNORMAL_COMPLETED|ABNORMAL_ENDED|COMPLETED|INCOMPLETE|FAULT_TERMINATED") private String runResult;
        private String winchCode;
        @AssertTrue(message = "startTime 必须早于 endTime")
        public boolean isRangeValid() { return increasing(startTime, endTime); }
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class RunDetailQuery extends SystemQuery {
        @NotBlank private String recordCode;
        @Pattern(regexp = "MONTH") private String yearGrouping;
        @NotBlank @Pattern(regexp = "OVERVIEW|RAW") private String resolution = "OVERVIEW";
        private String cursor;
        private String eventCursor;
        @Min(1) @Max(2000) private int maxPointsPerSeries = 500;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime fromAt;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime toAt;
        private BigDecimal heightMin;
        private BigDecimal heightMax;
        private String winchCode;
        @AssertTrue(message = "fromAt/toAt、heightMin/heightMax 必须分别成对且范围有序")
        public boolean isRangeValid() {
            return paired(fromAt, toAt) && increasing(fromAt, toAt)
                    && paired(heightMin, heightMax)
                    && (heightMin == null || heightMin.compareTo(heightMax) <= 0);
        }
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class SamplePointQuery extends SystemQuery {
        @NotBlank private String recordCode;
        @NotBlank private String sampleKey;
        private String winchCode;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class LatestAlarmQuery extends SystemQuery {
        private String deviceCode;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class AlarmHistoryQuery extends PagedSystemQuery {
        private String deviceCode;
        @NotBlank @Pattern(regexp = "TODAY|7D|30D|CUSTOM") private String range = "30D";
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate startDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate endDate;
        // 显式空串表示用户未选择任何等级，与省略参数不同。
        @Pattern(regexp = "(?:(?:NORMAL|SERIOUS)(?:,(?:NORMAL|SERIOUS))*)?")
        private String levels = "NORMAL,SERIOUS";
        private boolean includeUnclassified;
        @AssertTrue(message = "CUSTOM 须提供首尾包含的日期范围，最多 366 天")
        public boolean isCustomRangeValid() {
            if (!"CUSTOM".equals(range)) return true;
            return startDate != null && endDate != null && !endDate.isBefore(startDate)
                    && ChronoUnit.DAYS.between(startDate, endDate) < 366;
        }
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class EventHistoryQuery extends PagedSystemQuery {
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime startTime;
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private OffsetDateTime endTime;
        @NotBlank @Pattern(regexp = "ALL|NON_ALARM|ALARM") private String recordKind = "ALL";
        private String deviceCode;
        @Pattern(regexp = "UP|DOWN|STOP") private String direction;
        private String recordCode;
        @AssertTrue(message = "startTime 必须早于 endTime")
        public boolean isRangeValid() { return increasing(startTime, endTime); }
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class ArchiveQuery extends WinchQuery {
        @NotBlank @Pattern(regexp = "TENSION|SPEED") private String metric;
        @Pattern(regexp = "WINCH_TENSION|TRACTION_ROPE_TENSION|TOTAL_TENSION") private String tensionMetric;
        @NotBlank @Pattern(regexp = "DAY|WEEK|MONTH|QUARTER|HALF_YEAR|YEAR") private String period = "WEEK";
        @NotBlank @Pattern(regexp = "SELECTED_PERIODS|PREVIOUS_RUN|HISTORY_AVERAGE|PREVIOUS_PERIOD|YEAR_ON_YEAR")
        private String comparisonType = "PREVIOUS_RUN";
        private String recordCode;
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate changeStartDate;
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate changeEndDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate currentStartDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate currentEndDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate referenceStartDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate referenceEndDate;
        // 接收旧别名仅用于明确拒绝，绝不猜测两个图的范围。
        private String compareStart;
        private String compareEnd;
        private String cursor;
        @Min(1) @Max(2000) private int maxPointsPerSeries = 500;
        @AssertTrue(message = "拉力查询须提供 tensionMetric")
        public boolean isTensionMetricValid() { return !"TENSION".equals(metric) || tensionMetric != null; }
        @AssertTrue(message = "RANGE_INCOMPLETE: 请按比较方式提交独立且完整的日期范围或 recordCode")
        public boolean isComparisonRangeValid() {
            if (compareStart != null || compareEnd != null || !increasing(changeStartDate, changeEndDate)) return false;
            if (!paired(currentStartDate, currentEndDate) || !increasing(currentStartDate, currentEndDate)
                    || !paired(referenceStartDate, referenceEndDate) || !increasing(referenceStartDate, referenceEndDate)) return false;
            if ("PREVIOUS_RUN".equals(comparisonType) || "HISTORY_AVERAGE".equals(comparisonType)) {
                return recordCode != null && !recordCode.isBlank() && currentStartDate == null
                        && (!"PREVIOUS_RUN".equals(comparisonType) || referenceStartDate == null);
            }
            if (recordCode != null || currentStartDate == null) return false;
            return "SELECTED_PERIODS".equals(comparisonType) ? referenceStartDate != null : referenceStartDate == null;
        }
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class ExportListQuery extends PagedSystemQuery {
        @Pattern(regexp = "RUN_LIST|RUN_SAMPLES|EXCEPTIONS|PERIOD_STATISTICS") private String exportType;
        @Pattern(regexp = "QUEUED|RUNNING|SUCCESS|FAILED") private String state;
        public ExportListQuery() { setPageSize(20); }
    }

    public record EmergencyStopRequest(
            @NotBlank String systemCode,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String requestId,
            @Size(max = 512) String reason) {}

    public record ParameterUpdateRequest(
            @NotBlank String systemCode,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String requestId,
            @NotBlank String expectedVersion,
            @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String startTime,
            @NotNull @Min(1) @Max(35791394) Integer intervalMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10") BigDecimal speedMPerMinute) {}

    public record ThresholdValue(
            @NotBlank String sensorCode,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal alarmThreshold,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal safetyValue) {
        @AssertTrue(message = "预警阈值必须小于保护阈值")
        public boolean isOrderValid() {
            return alarmThreshold == null || safetyValue == null || alarmThreshold.compareTo(safetyValue) < 0;
        }
    }

    public record ThresholdUpdateRequest(
            @NotBlank String systemCode,
            @NotBlank String winchCode,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String requestId,
            @NotBlank String expectedVersion,
            @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid ThresholdValue> thresholds) {
        @AssertTrue(message = "两路传感器编号不能重复")
        public boolean isDistinctSensors() {
            return thresholds == null || thresholds.stream().anyMatch(v -> v == null || v.sensorCode() == null)
                    || thresholds.stream().map(ThresholdValue::sensorCode).distinct().count() == thresholds.size();
        }
    }

    /** 导出只接收已提交筛选条件，不接收 SQL 或页面分页参数。 */
    public record ExportFilters(
            OffsetDateTime startTime, OffsetDateTime endTime,
            String recordCode, @Size(min = 1, max = 100) List<@NotBlank String> recordCodes,
            @Size(min = 1, max = 100) List<@NotBlank String> eventCodes,
            String winchCode, String deviceCode,
            @Pattern(regexp = "ALL_UP|PRODUCTION_UP|RECOVERY_UP|MAINTENANCE_UP|UNCLASSIFIED_UP") String motionRole,
            @Pattern(regexp = "UP|DOWN|STOP") String direction,
            @Pattern(regexp = "NORMAL_COMPLETED|ABNORMAL_COMPLETED|ABNORMAL_ENDED|COMPLETED|INCOMPLETE|FAULT_TERMINATED") String runResult,
            @Pattern(regexp = "(?:(?:NORMAL|SERIOUS)(?:,(?:NORMAL|SERIOUS))*)?") String levels,
            @Pattern(regexp = "DAY|WEEK|MONTH|QUARTER|HALF_YEAR|YEAR") String period,
            LocalDate startDate, LocalDate endDate,
            @Pattern(regexp = "TENSION|SPEED") String metric,
            @Pattern(regexp = "WINCH_TENSION|TRACTION_ROPE_TENSION|TOTAL_TENSION") String tensionMetric) {
        @AssertTrue(message = "导出须选择编号集合或有序范围，不能混用")
        public boolean isSelectionValid() {
            if (!paired(startTime, endTime) || !increasing(startTime, endTime)
                    || !paired(startDate, endDate) || !increasing(startDate, endDate)) return false;
            int selections = (recordCode == null ? 0 : 1) + (recordCodes == null ? 0 : 1)
                    + (eventCodes == null ? 0 : 1) + (startTime == null ? 0 : 1) + (startDate == null ? 0 : 1);
            return selections == 1 && (recordCode == null || !recordCode.isBlank());
        }
    }

    public record ExportCreateRequest(
            @NotBlank String systemCode,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String requestId,
            @NotBlank @Pattern(regexp = "RUN_LIST|RUN_SAMPLES|EXCEPTIONS|PERIOD_STATISTICS") String exportType,
            @Pattern(regexp = "t|kg") String unit,
            @NotNull @Valid ExportFilters filters) {
        public ExportCreateRequest { if (unit == null) unit = "t"; }
        @AssertTrue(message = "导出条件与 exportType 不匹配")
        public boolean isTypeSelectionValid() {
            if (filters == null || exportType == null) return true;
            return switch (exportType) {
                case "RUN_LIST", "RUN_SAMPLES" -> filters.eventCodes() == null && filters.startDate() == null;
                case "EXCEPTIONS" -> filters.recordCode() == null && filters.recordCodes() == null && filters.startDate() == null;
                case "PERIOD_STATISTICS" -> filters.startDate() != null && filters.period() != null;
                default -> true;
            };
        }
    }

    private static boolean paired(Object left, Object right) { return (left == null) == (right == null); }
    private static boolean increasing(OffsetDateTime from, OffsetDateTime to) {
        return from == null || to == null || from.toInstant().isBefore(to.toInstant());
    }
    private static boolean increasing(LocalDate from, LocalDate to) {
        return from == null || to == null || from.isBefore(to);
    }
}
