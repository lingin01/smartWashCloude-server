package com.company.project.domain.bo.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 一轮=完整下降+上升；异常中断轮次保留，但默认不进入正常完成轮次平均。 */
public record CleaningRoundSummaryBo(String boxId, String sessionId, int roundNo,
                                     Instant startedAt, Instant finishedAt,
                                     BigDecimal descentMeters, BigDecimal ascentMeters,
                                     BigDecimal maximumHeightMeters, int speedLevel,
                                     long rawSamples, long validSamples, long invalidSamples,
                                     MetricAccumulatorBo groupTensionKilograms,
                                     MetricAccumulatorBo groupSpeedMetersPerMinute,
                                     BigDecimal maximumSingleRopeTensionKilograms,
                                     String maximumRopeId, BigDecimal maximumHeightSpreadMeters,
                                     BigDecimal maximumSpeedSpreadMeters,
                                     int alarmCount, String result,
                                     List<RopeRoundStatisticBo> ropes,
                                     List<WinchRoundStatisticBo> winches) {
    public CleaningRoundSummaryBo {
        ropes = ropes == null ? List.of() : List.copyOf(ropes);
        winches = winches == null ? List.of() : List.copyOf(winches);
    }
}
