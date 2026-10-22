package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 后端整理的历史分段，三元键 deviceId+batchId+telemetrySequence 防重复。 */
public record TelemetrySegmentEntity(String deviceId, String batchId, long telemetrySequence,
                                     String sessionId, int roundNo, String direction,
                                     Instant startedAt, Instant finishedAt,
                                     BigDecimal startHeightMeters, BigDecimal endHeightMeters,
                                     String triggerReason, String policyVersion,
                                     List<MeasurementAggregateEntity> ropeTensions,
                                     List<MeasurementAggregateEntity> winchSpeeds,
                                     List<MeasurementAggregateEntity> winchTorqueForcesKg) {
    public TelemetrySegmentEntity {
        ropeTensions = ropeTensions == null ? List.of() : List.copyOf(ropeTensions);
        winchSpeeds = winchSpeeds == null ? List.of() : List.copyOf(winchSpeeds);
        winchTorqueForcesKg = winchTorqueForcesKg == null ? List.of() : List.copyOf(winchTorqueForcesKg);
    }
}
