package com.company.project.vo.cleaning;
import java.time.Instant;
public record CleaningCommandVo(String requestId, String commandId, String deviceId,
        String type, String state, String code, String message,
        Instant createdAt, Instant expiresAt, Instant updatedAt) { }
