package com.company.project.domain.entity.cleaning;

import java.time.Instant;

/** 一次连续自动清洗会话，可跨日并包含多轮，不用于单机检修。 */
public record CleaningSessionEntity(String sessionId, String cleaningSystemId, String boxId,
                                    String planId, long planVersion, String safetyPolicyVersion,
                                    String startedBy, Instant startedAt, Instant finishedAt,
                                    String status, String stopReason, int completedRounds,
                                    int abnormalRounds, long rawSampleCount, int boxAlarmCount) {
}
