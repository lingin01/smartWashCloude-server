package com.company.project.domain.bo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
/** 已解码的实时快照，只输出当前采集链路能够提供的数据。 */
public record TelemetrySample(String deviceId, long sequence, Instant sampledAt,
        List<RopeSample> ropes, List<WinchSample> winches,
        BigDecimal groupAverageTension, BigDecimal groupAverageSpeed, BigDecimal groupAverageHeight,
        BigDecimal heightSpread, SystemRealtimeStatusBo status, BigDecimal ringHeightMeters,
        boolean heightValid, int validTensionCount, int validSpeedCount,
        BigDecimal groupSpeedMetersPerMinute) {
    public TelemetrySample {
        ropes = List.copyOf(ropes);
        winches = List.copyOf(winches);
    }
    public record WinchSample(String winchId, BigDecimal heightMeters, BigDecimal speed,
            BigDecimal tensionAverage, boolean online, boolean running, String faultCode,
            BigDecimal torqueKg, boolean torqueValid, boolean heightValid, boolean speedValid,
            String status, String direction, int validTensionCount) { }
    public record RopeSample(String ropeId, String sensorId, String winchId,
            BigDecimal tension, boolean valid) { }
}
