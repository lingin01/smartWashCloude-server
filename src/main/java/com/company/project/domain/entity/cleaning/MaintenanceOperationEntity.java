package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;
import java.time.Instant;

/** 单台卷扬机手动检修记录，不能混入自动清洗轮次和清洗统计。 */
public record MaintenanceOperationEntity(String operationId, String boxId, String winchId,
                                         String operatorId, String action, String direction,
                                         int speedLevel, Instant startedAt, Instant finishedAt,
                                         BigDecimal heightChangeMeters, String commandId,
                                         String result, String endReason) {
}
