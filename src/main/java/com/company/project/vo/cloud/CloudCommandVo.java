package com.company.project.vo.cloud;
import java.time.Instant;
public record CloudCommandVo(String requestId, String cloudCommandId, String boxCommandId,
        String boxId, String type, String state, String code, String message,
        Instant createdAt, Instant expiresAt, Instant updatedAt, long resourceVersion) { }
