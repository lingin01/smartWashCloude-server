package com.company.project.domain.bo.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 后端降采样历史分段；幂等键为 deviceId+batchId+telemetrySequence。 */
public record TelemetrySegmentBo(String deviceId, String batchId, long telemetrySequence,
                                 String sessionId, int roundNo, String direction,
                                 Instant startedAt, Instant finishedAt,
                                 BigDecimal startHeightMeters, BigDecimal endHeightMeters,
                                 String triggerReason, String policyVersion,
                                 List<MeasurementAggregateBo> ropeTensions,
                                 List<MeasurementAggregateBo> winchSpeeds,
                                 List<MeasurementAggregateBo> winchTorqueForcesKg) {
    public TelemetrySegmentBo {
        ropeTensions = ropeTensions == null ? List.of() : List.copyOf(ropeTensions);
        winchSpeeds = winchSpeeds == null ? List.of() : List.copyOf(winchSpeeds);
        winchTorqueForcesKg = winchTorqueForcesKg == null ? List.of() : List.copyOf(winchTorqueForcesKg);
    }
}
