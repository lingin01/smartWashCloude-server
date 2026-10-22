package com.company.project.domain.bo;
import java.time.Instant;
import java.util.Map;
/** 已认证用户提交给指定 PLC 的操作；登记不代表已经执行。 */
public record CleaningCommand(String commandId, String deviceId, String type,
        Map<String, Object> parameters, Instant createdAt, String requestId,
        String operatorId, Instant expiresAt) {
    public CleaningCommand { parameters = parameters == null ? Map.of() : Map.copyOf(parameters); }
}
