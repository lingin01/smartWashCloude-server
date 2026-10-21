package com.company.project.domain.bo.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 聚合只合并 sum/count/min/max，禁止对已有平均值再次求平均。 */
public record MetricAccumulatorBo(BigDecimal sum, long count, BigDecimal minimum, BigDecimal maximum) {
    public MetricAccumulatorBo {
        if (count < 0 || (count > 0 && (sum == null || minimum == null || maximum == null))) {
            throw new IllegalArgumentException("统计量不完整");
        }
    }

    public static MetricAccumulatorBo empty() {
        return new MetricAccumulatorBo(BigDecimal.ZERO, 0, null, null);
    }

    public MetricAccumulatorBo merge(MetricAccumulatorBo other) {
        if (other.count == 0) return this;
        if (count == 0) return other;
        return new MetricAccumulatorBo(sum.add(other.sum), count + other.count,
                minimum.min(other.minimum), maximum.max(other.maximum));
    }

    public BigDecimal average() {
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }
}
