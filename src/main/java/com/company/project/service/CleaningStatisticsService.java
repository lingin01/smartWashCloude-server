package com.company.project.service;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.statistics.CleaningStatisticsBo;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
/** 统计服务聚合实际样本分析和既有历史保存，不声明未实现的轮次/周期能力。 */
public interface CleaningStatisticsService {
    void acceptSample(TelemetrySample sample);
    CleaningStatisticsBo statistics(String deviceId);
    void acceptHistoryBatch(TelemetryBatchBo batch);
    void runPersistenceCycle();
}
