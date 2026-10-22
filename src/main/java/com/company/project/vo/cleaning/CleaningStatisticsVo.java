package com.company.project.vo.cleaning;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record CleaningStatisticsVo(String deviceId,String scope,long acceptedSamples,
        Instant firstSampleAt,Instant lastSampleAt,List<MetricVo> metrics) {
    public record MetricVo(String metric,String targetId,String unit,long validSamples,
            BigDecimal minimum,BigDecimal maximum,BigDecimal sampleAverage) { }
}
