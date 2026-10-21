package com.company.project.domain.bo;

import java.time.Instant;

/** 设备操作结果；boxId/boxCommandId 暂保留既有 API 形状，本地通道不生成盒子编号。 */
public record CloudCommandStatusBo(String requestId, String cloudCommandId, String boxCommandId,
        String boxId, String type, State state, String code, String message,
        Instant createdAt, Instant expiresAt, Instant updatedAt, long resourceVersion) {
    public enum State {
        CREATED, QUEUED, ACCEPTED, RUNNING,
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
                case QUEUED -> next == ACCEPTED || next == RUNNING || next == SUCCEEDED
                        || next == REJECTED || next == FAILED || next == UNCONFIRMED;
                case ACCEPTED -> next == RUNNING || next == SUCCEEDED || next == REJECTED
                        || next == FAILED || next == UNCONFIRMED;
                case RUNNING -> next == SUCCEEDED || next == FAILED || next == UNCONFIRMED;
                case UNCONFIRMED -> next == SUCCEEDED || next == FAILED || next == REJECTED;
                default -> false;
            };
        }
    }
}
