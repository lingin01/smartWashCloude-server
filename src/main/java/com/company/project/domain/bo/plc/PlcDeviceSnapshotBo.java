package com.company.project.domain.bo.plc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Runtime 发布的不可变业务可读设备快照，不暴露 Socket 或协议字节。 */
public record PlcDeviceSnapshotBo(String deviceId, String boxId, boolean online, long sequence,
        Instant sampledAt, List<Point> points) {
    public PlcDeviceSnapshotBo { points = List.copyOf(points); }
    public record Point(String pointId, String field, int index, String ropeId, String sensorId,
                        String winchId, String unit, BigDecimal activeValue, BigDecimal value, boolean valid) { }
}
