package com.company.project.service;

import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.statistics.CleaningRoundSummaryBo;
import com.company.project.domain.bo.statistics.StatisticsQueryBo;
import com.company.project.domain.bo.statistics.StatisticsResultBo;
import com.company.project.domain.bo.statistics.TelemetryBatchBo;

/** 统计服务只描述业务操作；采样消费线程、定时调度放在技术层。 */
public interface CloudStatisticsService {
    void acceptSample(TelemetrySample sample);
    void acceptHistoryBatch(TelemetryBatchBo batch);
    void acceptCompletedRound(CleaningRoundSummaryBo round);
    StatisticsResultBo query(StatisticsQueryBo query);
    void runRealtimePersistenceCycle();
    void runRoundAggregationCycle();
    void runDirtyBucketRebuildCycle();
    void runRetentionCycle();
    void closeRun(String runId);
    void rebuildDay(String systemCode, java.time.LocalDate day);
    void rebuildMonth(String systemCode, java.time.YearMonth month);
    void rebuildYear(String systemCode, int year);
}
