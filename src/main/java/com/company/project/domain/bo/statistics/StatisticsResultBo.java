package com.company.project.domain.bo.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 统计结果连同后端计算的坐标轴和比较值返回；前端不再补算。 */
public record StatisticsResultBo(String boxId, String metric, String periodType,
                                 Instant rangeStart, Instant rangeEnd, String groupBy,
                                 String requestedUnit, String resolvedUnit,
                                 XAxisBo xAxis, YAxisBo yAxis,
                                 MetricAccumulatorBo overall, BigDecimal overallAverage,
                                 long completedRoundCount, long abortedRoundCount,
                                 long failedRoundCount, long emergencyStoppedRoundCount,
                                 ComparisonBo comparison, List<BucketBo> buckets) {
    public StatisticsResultBo {
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }

    public record XAxisBo(String title, String type, String timezone,
                          Instant minimum, Instant maximum, List<TickBo> ticks) {
        public XAxisBo {
            ticks = ticks == null ? List.of() : List.copyOf(ticks);
        }
    }

    public record TickBo(Instant value, String label, Instant bucketStart, Instant bucketEnd) {
    }

    public record YAxisBo(String title, String unit, BigDecimal minimum,
                          BigDecimal maximum, BigDecimal tickInterval, int decimalPlaces) {
    }

    public record BucketBo(Instant start, Instant end, String label,
                           MetricAccumulatorBo value, ComparisonBo comparison) {
    }

    public record ComparisonBo(String scope, boolean configurationChanged,
                               ChangeBo previousPeriod, ChangeBo samePeriodLastYear) {
    }

    public record ChangeBo(boolean comparable, BigDecimal baselineValue,
                           BigDecimal changeValue, BigDecimal changeRate,
                           String direction, String reason) {
    }
}
