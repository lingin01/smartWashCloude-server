package com.company.project.domain.bo;

import java.math.BigDecimal;
import java.time.Instant;

/** 一次自动清洗作业内完整的上下往返；单机检修不生成该记录。 */
public record CleaningCycle(String cycleId, String runId, int ordinal,
                            Instant startedAt, Instant finishedAt,
                            BigDecimal travelMeters, BigDecimal averageTensionKilograms,
                            BigDecimal maximumSingleRopeTensionKilograms,
                            String result) {
}
