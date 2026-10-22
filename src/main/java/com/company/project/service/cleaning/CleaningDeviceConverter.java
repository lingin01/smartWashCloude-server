package com.company.project.service.cleaning;

import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo.Point;
import java.math.*;
import java.util.*;
import static com.company.project.domain.bo.SystemRealtimeStatusBo.Status;

/** 业务转换：不解码报文、不读寄存器。缺失为 null／无效，不补零参加均值。 */
final class CleaningDeviceConverter {
    public TelemetrySample toRealtime(PlcDeviceSnapshotBo snapshot, TelemetrySample previous) {
        List<Point> points = snapshot.points();
        Boolean manual = flag(points, "manualMode", 1), running = flag(points, "running", 1);
        Boolean emergency = flag(points, "emergency", 1), fault = flag(points, "fault", 1);
        BigDecimal faultValue = number(points, "faultCode", 1);
        String faultCode = faultValue != null && faultValue.signum() != 0 ? faultValue.stripTrailingZeros().toPlainString() : "";
        boolean stopped = emergency != null ? emergency
                : previous != null && previous.status() != null && previous.status().emergencyActive();
        if (Boolean.FALSE.equals(fault)) faultCode = "";
        else if (Boolean.TRUE.equals(fault) && faultCode.isEmpty()) faultCode = "DEVICE_FAULT";
        else if (fault == null && faultCode.isEmpty() && previous != null && previous.status() != null)
            faultCode = text(previous.status().faultCode());
        boolean known = snapshot.online() && ((manual != null && (manual || running != null))
                || Boolean.TRUE.equals(emergency) || Boolean.TRUE.equals(fault) || !faultCode.isEmpty());
        Status status = !snapshot.online() || !known ? Status.OFFLINE : stopped || !faultCode.isEmpty()
                ? Status.FAULT : Boolean.TRUE.equals(manual) ? Status.MAINTENANCE
                : Boolean.TRUE.equals(running) ? Status.RUNNING : Status.STANDBY;
        Boolean up = flag(points, "up", 1), down = flag(points, "down", 1);
        String direction = Boolean.TRUE.equals(up) && !Boolean.TRUE.equals(down) ? "UP"
                : Boolean.TRUE.equals(down) && !Boolean.TRUE.equals(up) ? "DOWN"
                : up != null && down != null && !up && !down ? "STOP" : "UNKNOWN";
        var state = new SystemRealtimeStatusBo(status, snapshot.online(), known, stopped, faultCode, direction);
        List<TelemetrySample.RopeSample> ropes = new ArrayList<>();
        List<TelemetrySample.WinchSample> winches = new ArrayList<>();
        List<BigDecimal> tensions = new ArrayList<>(), speeds = new ArrayList<>(), heights = new ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            Point point = point(points, "ropeTension", index);
            BigDecimal tension = usable(snapshot, point);
            if (tension != null) tensions.add(tension);
            ropes.add(new TelemetrySample.RopeSample(point == null ? "" : text(point.ropeId()),
                    point == null ? "" : text(point.sensorId()), point == null ? "" : text(point.winchId()),
                    tension, tension != null));
        }
        for (int index = 1; index <= 4; index++) {
            final int slot = index;
            Point identity = points.stream().filter(p -> !text(p.winchId()).isEmpty()
                    && (p.field() != null && (p.field().startsWith("winch") || p.field().equals("encoderFault")) && p.index() == slot
                    || "ropeTension".equals(p.field()) && (p.index() - 1) / 2 + 1 == slot)).findFirst().orElse(null);
            BigDecimal height = usable(snapshot, point(points, "winchHeight", index));
            BigDecimal speed = usable(snapshot, point(points, "winchSpeed", index));
            BigDecimal torque = usable(snapshot, point(points, "winchTorque", index));
            if (height != null) heights.add(height);
            if (speed != null) speeds.add(speed);
            BigDecimal first = ropes.get((index - 1) * 2).tension(), second = ropes.get((index - 1) * 2 + 1).tension();
            int pairCount = (first == null ? 0 : 1) + (second == null ? 0 : 1);
            BigDecimal average = pairCount == 2 ? first.add(second).divide(BigDecimal.valueOf(2), MathContext.DECIMAL128) : null;
            boolean localFault = Boolean.TRUE.equals(flag(points, "winchFault", index));
            boolean encoderFault = Boolean.TRUE.equals(flag(points, "encoderFault", index));
            Boolean localRunning = flag(points, "winchRunning", index);
            boolean moving = snapshot.online() && (localRunning != null ? localRunning : Boolean.TRUE.equals(up) || Boolean.TRUE.equals(down));
            Status winchStatus = !snapshot.online() || !known ? Status.OFFLINE : stopped || localFault || encoderFault
                    ? Status.FAULT : Boolean.TRUE.equals(manual) ? Status.MAINTENANCE
                    : localRunning != null ? localRunning ? Status.RUNNING : Status.STANDBY
                    : Boolean.TRUE.equals(running) ? Status.RUNNING : Status.STANDBY;
            String localCode = stopped ? "EMERGENCY_STOP" : localFault ? "WINCH_FAULT" : encoderFault ? "ENCODER_FAULT" : "";
            winches.add(new TelemetrySample.WinchSample(identity == null ? "" : text(identity.winchId()),
                    height, speed, average, snapshot.online(), moving, localCode, torque, torque != null, height != null, speed != null,
                    winchStatus.name(), direction, pairCount));
        }
        BigDecimal ring = usable(snapshot, point(points, "ringHeight", 1));
        BigDecimal spread = heights.isEmpty() ? null : Collections.max(heights).subtract(Collections.min(heights));
        return new TelemetrySample(snapshot.deviceId(), snapshot.sequence(), snapshot.sampledAt(),
                ropes, winches, average(tensions), average(speeds), average(heights), spread,
                state, ring, ring != null, tensions.size(), speeds.size(),
                usable(snapshot, point(points, "groupSpeed", 1)));
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static Point point(List<Point> points, String field, int index) {
        return points.stream().filter(p -> field.equals(p.field()) && p.index() == index).findFirst().orElse(null);
    }
    private static BigDecimal number(List<Point> points, String field, int index) {
        Point p = point(points, field, index); return p != null && p.valid() ? p.value() : null;
    }
    private static Boolean flag(List<Point> points, String field, int index) {
        Point p = point(points, field, index);
        return p == null || !p.valid() || p.value() == null || p.activeValue() == null
                ? null : p.value().compareTo(p.activeValue()) == 0;
    }
    private static BigDecimal usable(PlcDeviceSnapshotBo snapshot, Point point) {
        return snapshot.online() && point != null && point.valid() ? point.value() : null;
    }
    private static BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128);
    }
}
