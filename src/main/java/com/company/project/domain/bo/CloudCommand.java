package com.company.project.domain.bo;

import java.time.Instant;
import java.util.Map;

/** 用户设备操作请求；登记不代表已发送或已执行，既有归属键保持兼容。 */
public record CloudCommand(String commandId, String systemCode, String boxCode,
                           String type, Map<String, Object> parameters, Instant createdAt,
                           String requestId, String operatorId, Instant expiresAt) {
    public CloudCommand {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
    public CloudCommand(String commandId, String systemCode, String boxCode, String type,
                        Map<String, Object> parameters, Instant createdAt) {
        this(commandId, systemCode, boxCode, type, parameters, createdAt, commandId, null,
                createdAt.plusSeconds(30));
    }
}
