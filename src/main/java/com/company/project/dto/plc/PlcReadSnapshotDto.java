package com.company.project.dto.plc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 网络采集 DTO：每点独立质量，倍率只在网络测点映射阶段使用一次。 */
public record PlcReadSnapshotDto(String deviceId, boolean online, long sequence,
                                 Instant sampledAt, List<PointValue> points) {
    public PlcReadSnapshotDto { points = List.copyOf(points); }
    public record PointValue(String pointId, String field, int index, String ropeId, String sensorId,
            String winchId, String unit, BigDecimal activeValue, BigDecimal value, boolean valid) { }
}
