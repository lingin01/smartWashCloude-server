package com.company.project.domain.bo;

/** 用户业务状态与运动方向分开；手动模式统一显示检修，通信在线不等于状态有效。 */
public record SystemRealtimeStatusBo(Status systemStatus, boolean online, boolean statusValid,
        boolean emergencyActive, String faultCode, String direction) {
    public enum Status {
        STANDBY, RUNNING, MAINTENANCE, FAULT, OFFLINE;
        public String textZh() {
            return switch (this) {
                case STANDBY -> "待机"; case RUNNING -> "运行"; case MAINTENANCE -> "检修";
                case FAULT -> "故障"; case OFFLINE -> "离线";
            };
        }
    }
}
