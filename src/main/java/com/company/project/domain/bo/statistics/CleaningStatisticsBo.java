package com.company.project.domain.bo.statistics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
/** 本进程实际观测样本的统计，不冒充完整轮次或时间加权统计。 */
public record CleaningStatisticsBo(String deviceId,String scope,long acceptedSamples,Instant firstSampleAt,
        Instant lastSampleAt,List<Metric> metrics) {
    public CleaningStatisticsBo { metrics=List.copyOf(metrics); }
    public record Metric(String metric,String targetId,String unit,long validSamples,
            BigDecimal minimum,BigDecimal maximum,BigDecimal sampleAverage) { }
}
