package com.company.project.domain.bo;

import java.time.Instant;

/** PLC 操作状态；协议写入确认与实际设备动作分别核对。 */
public record CleaningCommandStatusBo(String requestId, String commandId,
        String deviceId, String type, State state, String code, String message,
        Instant createdAt, Instant expiresAt, Instant updatedAt) {
    public enum State {
        CREATED, QUEUED,
        SUCCEEDED, REJECTED, FAILED, UNCONFIRMED, BLOCKED;

        public boolean terminal() {
            return this == SUCCEEDED || this == REJECTED || this == FAILED || this == BLOCKED;
        }

        /** 状态顺序显式表达，避免移除传输状态后 ordinal 比较改变原业务含义。 */
        public boolean canAdvanceTo(State next) {
            if (next == null) return false;
            if (next == this) return true;
            if (terminal()) return false;
            return switch (this) {
                case CREATED -> next == QUEUED || next == REJECTED || next == BLOCKED
                        || next == FAILED || next == UNCONFIRMED;
                case QUEUED -> next == SUCCEEDED
                        || next == REJECTED || next == FAILED || next == UNCONFIRMED;
                case UNCONFIRMED -> next == SUCCEEDED || next == FAILED || next == REJECTED;
                default -> false;
            };
        }
    }
}
