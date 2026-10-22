package com.company.project.domain.message;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 内部不可变消息；载荷类型由各有界通道限定。 */
public record Message<T>(String messageId, String correlationId, String deviceId, Type type,
                         int priority, Instant createdAt, Instant expiresAt, T payload) {
    public enum Type { PLC_OPERATION, PLC_WRITE_RESULT, DEVICE_SNAPSHOT }
    public Message {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(payload);
    }
    public static <T> Message<T> create(String correlationId, String deviceId, Type type,
                                       int priority, Instant expiresAt, T payload) {
        return new Message<>(UUID.randomUUID().toString(), correlationId, deviceId, type,
                priority, Instant.now(), expiresAt, payload);
    }
}
