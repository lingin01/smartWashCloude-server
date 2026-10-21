package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 盒子上报的降采样段，三元键 boxId+batchId+telemetrySequence 防重复。 */
public record TelemetrySegmentEntity(String boxId, String batchId, long telemetrySequence,
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
