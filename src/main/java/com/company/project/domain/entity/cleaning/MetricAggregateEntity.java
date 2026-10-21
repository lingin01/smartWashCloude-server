package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;
import java.time.Instant;

/** 日/月/季度/半年/年聚合桶，保存 sum/count 而非仅保存平均数。 */
public record MetricAggregateEntity(String boxId, String bucketType, Instant bucketStart,
                                    Instant bucketEnd, String dimensionType, String dimensionId,
                                    String metricType, BigDecimal sumValue, long sampleCount,
                                    BigDecimal minimumValue, BigDecimal maximumValue,
                                    int completedRounds, int abnormalRounds,
                                    int emergencyStoppedRounds, int alarmCount,
                                    long revision, Instant calculatedAt) {
}
