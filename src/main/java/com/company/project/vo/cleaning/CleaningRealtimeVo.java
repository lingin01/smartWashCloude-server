package com.company.project.vo.cleaning;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
/** 页面展示真实状态、有效性和明确单位，不返回尚未识别的轮次或告警占位值。 */
public record CleaningRealtimeVo(String deviceId, boolean available, String message, DataVo data) {
    public record StatusVo(String systemStatus, String systemStatusTextZh, boolean online,
            boolean statusValid, boolean emergencyActive, String faultCode, String direction) { }
    public record DataVo(long sequence, Instant sampledAt,
            BigDecimal groupAverageTension, BigDecimal groupAverageSpeed, BigDecimal groupAverageHeight,
            BigDecimal heightSpread, List<RopeVo> ropes, List<WinchVo> winches, StatusVo status,
            BigDecimal ringHeightMeters, boolean heightValid, int validTensionCount, int validSpeedCount,
            BigDecimal groupSpeedMetersPerMinute, String tensionUnit, String speedUnit, String heightUnit) { }
    public record RopeVo(String ropeId, String sensorId, String winchId, BigDecimal tension, boolean valid) { }
    public record WinchVo(String winchId, BigDecimal heightMeters, BigDecimal speed,
            BigDecimal tensionAverage, boolean online, boolean running, String faultCode,
            BigDecimal torqueKg, boolean torqueValid, boolean heightValid, boolean speedValid,
            String status, String statusTextZh, String direction, int validTensionCount) { }
}
