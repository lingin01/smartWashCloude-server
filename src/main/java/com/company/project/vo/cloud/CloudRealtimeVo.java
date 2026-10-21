package com.company.project.vo.cloud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 页面只展示后端提供的状态、中文、有效性、单位和均值。 */
public record CloudRealtimeVo(String boxId, boolean available, String message, DataVo data) {
    public record StatusVo(String systemStatus, String systemStatusTextZh, boolean online,
            boolean statusValid, boolean emergencyActive, String faultCode, String direction) { }
    public record DataVo(String sessionId, long sequence, Instant sampledAt, int state, int speedLevel,
            BigDecimal groupAverageTension, BigDecimal groupAverageSpeed, BigDecimal groupAverageHeight,
            BigDecimal heightSpread, boolean hasWarning, boolean hasCriticalAlarm,
            List<RopeVo> ropes, List<WinchVo> winches, StatusVo status, BigDecimal ringHeightMeters,
            boolean heightValid, long statusVersion, int validTensionCount, int validSpeedCount,
            BigDecimal groupSpeedMetersPerMinute, String tensionUnit, String speedUnit, String heightUnit) { }
    public record RopeVo(String ropeId, String sensorId, String winchId, BigDecimal tension, boolean valid, int level) { }
    public record WinchVo(String winchId, BigDecimal heightMeters, BigDecimal speed,
            BigDecimal tensionAverage, boolean online, boolean running, String faultCode,
            BigDecimal torqueNewtonMeters, Boolean torqueValid, BigDecimal torqueKg,
            boolean heightValid, boolean speedValid, String status, String statusTextZh,
            String direction, int validTensionCount) { }
}
