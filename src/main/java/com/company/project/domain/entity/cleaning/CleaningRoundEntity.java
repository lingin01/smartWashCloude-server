package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;
import java.time.Instant;

/** 冷数据唯一键为 sessionId+roundNo；完成时间决定日/月统计归属。 */
public record CleaningRoundEntity(String sessionId, int roundNo, String boxId,
                                  Instant startedAt, Instant finishedAt, String result,
                                  BigDecimal descentMeters, BigDecimal ascentMeters,
                                  int speedLevel, int alarmCount, long validSampleCount) {
}
