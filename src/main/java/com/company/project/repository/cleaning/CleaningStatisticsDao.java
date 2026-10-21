package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.MetricAggregateEntity;
import java.time.Instant;
import java.util.List;

/** 聚合桶唯一键包含盒子、周期、起点、维度和指标。 */
public interface CleaningStatisticsDao {
    void upsert(MetricAggregateEntity bucket);
    List<MetricAggregateEntity> find(String boxId, String bucketType,
                                     Instant fromInclusive, Instant toExclusive,
                                     String metricType, String dimensionType);
    void markDirty(String boxId, Instant finishedAt);
    List<MetricAggregateEntity> findDirty(int limit);
}
