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
        BigDecimal groupSpeedMetersPerMinute,String captureId) {
    public TelemetrySample {
        ropes = List.copyOf(ropes);
        winches = List.copyOf(winches);
    }
    public TelemetrySample(String device,long sequence,Instant at,List<RopeSample> ropes,List<WinchSample> winches,
                           BigDecimal tension,BigDecimal speed,BigDecimal height,BigDecimal spread,SystemRealtimeStatusBo status,
                           BigDecimal ring,boolean valid,int tensionCount,int speedCount,BigDecimal setSpeed) {
        this(device,sequence,at,ropes,winches,tension,speed,height,spread,status,ring,valid,tensionCount,speedCount,setSpeed,null);
    }
    public TelemetrySample withCaptureId(String id) {
        return new TelemetrySample(deviceId,sequence,sampledAt,ropes,winches,groupAverageTension,groupAverageSpeed,groupAverageHeight,
                heightSpread,status,ringHeightMeters,heightValid,validTensionCount,validSpeedCount,groupSpeedMetersPerMinute,id);
    }
    public BigDecimal systemTotalTension() {
        return ropes.size() == 8 && ropes.stream().allMatch(r -> r.valid() && r.tension() != null)
                ? ropes.stream().map(RopeSample::tension).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
    }
    public record WinchSample(String winchId, BigDecimal heightMeters, BigDecimal speed,
            BigDecimal tensionAverage, boolean online, boolean running, String faultCode,
            BigDecimal torqueKg, boolean torqueValid, boolean heightValid, boolean speedValid,
            String status, String direction, int validTensionCount) {
        public BigDecimal totalTension() {
            return validTensionCount == 2 && tensionAverage != null
                    ? tensionAverage.multiply(BigDecimal.valueOf(2)) : null;
        }
    }
    public record RopeSample(String ropeId, String sensorId, String winchId,
            BigDecimal tension, boolean valid) { }
}
