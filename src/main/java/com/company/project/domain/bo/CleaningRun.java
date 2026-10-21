package com.company.project.domain.bo;

import java.time.Instant;

/** 一次连续自动清洗作业或一次单机检修作业的历史主记录。 */
public record CleaningRun(String runId, String systemCode, String boxCode,
                          String mode, String operator, Instant startedAt,
                          Instant finishedAt, String result, int completedCycles,
                          int alarmCount) {
}
