package com.company.project.domain.bo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 后端整理的设备快照；缺失值保留 null，实时状态不再混用旧清洗阶段 state。 */
public record TelemetrySample(String boxId, String sessionId, long sequence,
        Instant sampledAt, int state, int speedLevel, List<RopeSample> ropes, List<WinchSample> winches,
        BigDecimal groupAverageTension, BigDecimal groupAverageSpeed, BigDecimal groupAverageHeight,
        BigDecimal heightSpread, boolean hasWarning, boolean hasCriticalAlarm,
        SystemRealtimeStatusBo status, BigDecimal ringHeightMeters, boolean heightValid,
        long statusVersion, int validTensionCount, int validSpeedCount, BigDecimal groupSpeedMetersPerMinute) {
    public TelemetrySample {
        ropes = ropes == null ? List.of() : List.copyOf(ropes);
        winches = winches == null ? List.of() : List.copyOf(winches);
    }
    /** 兼容已有调用；未知状态和转矩单位不能自行补算。 */
    public TelemetrySample(String boxId, String sessionId, long sequence, Instant sampledAt, int state,
            int speedLevel, List<RopeSample> ropes, List<WinchSample> winches, BigDecimal tension,
            BigDecimal speed, BigDecimal height, BigDecimal spread, boolean warning, boolean critical) {
        this(boxId, sessionId, sequence, sampledAt, state, speedLevel, ropes, winches,
                tension, speed, height, spread, warning, critical, null, null, false, 0, 0, 0, null);
    }
    public record WinchSample(String winchId, BigDecimal heightMeters, BigDecimal speed,
            BigDecimal tensionAverage, boolean online, boolean running, String faultCode,
            BigDecimal torqueNewtonMeters, Boolean torqueValid, String torqueSource, String calculationVersion,
            BigDecimal torqueKg, boolean heightValid, boolean speedValid, String status,
            String direction, int validTensionCount) {
        public WinchSample(String id, BigDecimal height, BigDecimal speed, BigDecimal tension,
                boolean online, boolean running, String faultCode, BigDecimal torqueNm,
                Boolean torqueValid, String source, String version) {
            this(id, height, speed, tension, online, running, faultCode, torqueNm, torqueValid,
                    source, version, null, height != null, speed != null, "UNKNOWN", "UNKNOWN", 0);
        }
    }
    public record RopeSample(String ropeId, String sensorId, String winchId,
                             BigDecimal tension, boolean valid, int level) { }
}
