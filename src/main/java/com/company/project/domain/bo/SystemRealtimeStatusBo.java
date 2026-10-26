package com.company.project.domain.bo;

/** 用户业务状态与运动方向分开；手动模式统一显示检修，通信在线不等于状态有效。 */
public record SystemRealtimeStatusBo(Status systemStatus, boolean online, boolean statusValid,
        boolean emergencyActive, String faultCode, String direction, String source,java.util.Map<String,java.math.BigDecimal> plcValues) {
    public SystemRealtimeStatusBo {plcValues=plcValues==null?java.util.Map.of():java.util.Map.copyOf(plcValues);}
    public SystemRealtimeStatusBo(Status status,boolean online,boolean valid,boolean emergency,String fault,String direction,String source) {
        this(status,online,valid,emergency,fault,direction,source,java.util.Map.of());
    }
    public SystemRealtimeStatusBo(Status status,boolean online,boolean valid,boolean emergency,String fault,String direction) {
        this(status,online,valid,emergency,fault,direction,"LEGACY");
    }
    public SystemRealtimeStatusBo withPlcValues(java.util.Map<String,java.math.BigDecimal> values) {
        return new SystemRealtimeStatusBo(systemStatus,online,statusValid,emergencyActive,faultCode,direction,source,values);
    }
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
