package com.company.project.service.cleaning;

import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo.Point;
import java.math.*;
import java.util.*;
import static com.company.project.domain.bo.SystemRealtimeStatusBo.Status;

/** 业务转换：不解码报文、不读寄存器。缺失为 null／无效，不补零参加均值。 */
final class CleaningDeviceConverter {
    private record SpeedBase(BigDecimal height, BigDecimal seconds, String direction) { }
    private final Map<String, SpeedBase> speedBases = new HashMap<>();

    public TelemetrySample toRealtime(PlcDeviceSnapshotBo snapshot, TelemetrySample previous) {
        return toRealtime(snapshot, previous, null);
    }
    public TelemetrySample toRealtime(PlcDeviceSnapshotBo snapshot, TelemetrySample previous,
            com.company.project.config.PlcAccessProperties.Device configuration) {
        if (!snapshot.online()) return disconnected(snapshot, previous);
        if (point(snapshot.points(), "systemState", 1) == null) return legacy(snapshot, previous);
        var points = snapshot.points();
        BigDecimal stateCode = number(points, "systemState", 1);
        int code = stateCode == null ? 0 : stateCode.intValue();
        Status status = switch (code) {
            case 1 -> Status.RUNNING; case 2 -> Status.STANDBY; case 3 -> Status.MAINTENANCE;
            case 4 -> Status.FAULT; default -> Status.STANDBY;
        };
        var fault = number(points, "faultCode", 1);
        String faultCode = fault == null || fault.signum() == 0 ? "" : fault.toPlainString();
        List<TelemetrySample.RopeSample> ropes = new ArrayList<>();
        List<TelemetrySample.WinchSample> winches = new ArrayList<>();
        List<BigDecimal> tensions = new ArrayList<>(), speeds = new ArrayList<>(), heights = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Point p = point(points, "ropeTension", i);
            BigDecimal tension = p == null || !p.valid() ? null : p.value();
            if (tension != null && !"kg".equals(p.unit()))
                tension = tension.subtract(BigDecimal.TEN).multiply(new BigDecimal("0.1019716"));
            if (tension != null) tensions.add(tension);
            String sensor = p == null ? "" : text(p.sensorId());
            ropes.add(new TelemetrySample.RopeSample(p == null ? "" : text(p.ropeId()).isBlank() ? sensor : p.ropeId(),
                    sensor, p == null ? "" : text(p.winchId()), tension, tension != null));
        }
        for (int i = 1; i <= 4; i++) {
            final int slot = i;
            Point identity = points.stream().filter(p -> p.index() == slot && !text(p.winchId()).isBlank()
                    && !"ropeTension".equals(p.field())).findFirst().orElse(null);
            String id = identity == null ? ropes.get((i - 1) * 2).winchId() : identity.winchId();
            String direction = localDirection(points, i);
            BigDecimal encoder = number(points, "encoderCount", i);
            BigDecimal bottom = configuration == null ? null : configuration.getEncoderBottomCounts().get(i);
            BigDecimal height = encoder != null && bottom != null ? height(encoder, bottom)
                    : number(points, "winchHeight", i);
            BigDecimal speed = speed(snapshot.deviceId() + "/" + i, height,
                    number(points, "axisRunSeconds", i), direction);
            if (speed == null && point(points, "axisRunSeconds", i) == null) speed = number(points, "winchSpeed", i);
            BigDecimal motor = number(points, "winchTorque", i);
            Point motorPoint = point(points, "winchTorque", i);
            if (motor != null && !"kg".equals(motorPoint.unit())) motor = motor.multiply(new BigDecimal("0.35"))
                    .multiply(new BigDecimal("163.0883")).divide(new BigDecimal("9.80665").multiply(new BigDecimal("0.16")), MathContext.DECIMAL128);
            BigDecimal a = ropes.get((i - 1) * 2).tension(), b = ropes.get((i - 1) * 2 + 1).tension();
            int count = (a == null ? 0 : 1) + (b == null ? 0 : 1);
            BigDecimal average = count == 2 ? a.add(b).divide(BigDecimal.valueOf(2), MathContext.DECIMAL128) : null;
            BigDecimal local = number(points, "winchState", i);
            String localStatus = status == Status.MAINTENANCE ? "MAINTENANCE" : local == null ? null
                    : switch (local.intValue()) { case 0 -> "STANDBY"; case 1 -> "RUNNING"; case 2 -> "FAULT"; default -> null; };
            String localFault = fault != null && faultWinch(fault.intValue()) == i ? faultCode : "";
            if (height != null) heights.add(height);
            if (speed != null) speeds.add(speed);
            winches.add(new TelemetrySample.WinchSample(id, height, speed, average, true,
                    "UP".equals(direction) || "DOWN".equals(direction), localFault, motor, motor != null,
                    height != null, speed != null, localStatus, direction, count));
        }
        String groupDirection = winches.stream().map(TelemetrySample.WinchSample::direction)
                .filter(d -> "UP".equals(d) || "DOWN".equals(d)).distinct().reduce((a, b) -> "MIXED").orElse("STOP");
        return new TelemetrySample(snapshot.deviceId(), snapshot.sequence(), snapshot.sampledAt(), ropes, winches,
                average(tensions), average(speeds), average(heights),
                heights.isEmpty() ? null : Collections.max(heights).subtract(Collections.min(heights)),
                new SystemRealtimeStatusBo(status, true, code >= 1 && code <= 4,
                        Boolean.TRUE.equals(flag(points, "emergency", 1)), faultCode, groupDirection,"D201").withPlcValues(plcFacts(points)),
                null, false, tensions.size(), speeds.size(), number(points, "groupSpeed", 1));
    }
    private static Map<String,BigDecimal> plcFacts(List<Point> points) {
        Map<String,BigDecimal> facts=new LinkedHashMap<>();
        Set<String> fields=Set.of("systemState","systemDirectionRaw","fault","faultCode","automaticCycleActive","cycleAbnormal","cycleAbnormalEnd",
                "upwardCompletedCount","downwardCompletedCount","upwardFaultCount","downwardFaultCount","automaticRunTimeRaw");
        points.stream().filter(p->p.index()==1&&p.valid()&&p.value()!=null&&fields.contains(p.field()))
                .forEach(p->facts.put(p.field(),p.rawValue()==null?p.value():p.rawValue()));
        return Map.copyOf(facts);
    }
    static BigDecimal height(BigDecimal count, BigDecimal bottom) {
        BigDecimal r = count.subtract(bottom).divide(BigDecimal.valueOf(8192), MathContext.DECIMAL128);
        if (r.compareTo(BigDecimal.valueOf(12)) <= 0) return r.multiply(new BigDecimal("1.043"));
        if (r.compareTo(BigDecimal.valueOf(24)) <= 0)
            return new BigDecimal("12.516").add(r.subtract(BigDecimal.valueOf(12)).multiply(new BigDecimal("1.1069")));
        return new BigDecimal("25.7988").add(r.subtract(BigDecimal.valueOf(24)).multiply(new BigDecimal("1.171")));
    }
    private BigDecimal speed(String key, BigDecimal height, BigDecimal seconds, String direction) {
        if (height == null || seconds == null || !Set.of("UP", "DOWN").contains(direction)) {
            speedBases.remove(key); return null;
        }
        SpeedBase old = speedBases.get(key);
        if (old == null || !old.direction().equals(direction) || seconds.compareTo(old.seconds()) < 0) {
            speedBases.put(key, new SpeedBase(height, seconds, direction)); return null;
        }
        if (seconds.compareTo(old.seconds()) == 0) return null;
        speedBases.put(key, new SpeedBase(height, seconds, direction));
        return height.subtract(old.height()).abs().multiply(BigDecimal.valueOf(60))
                .divide(seconds.subtract(old.seconds()), MathContext.DECIMAL128);
    }
    private static String localDirection(List<Point> points, int index) {
        Boolean up = flag(points, "winchUp", index), down = flag(points, "winchDown", index);
        if (up == null || down == null || up && down) return "UNKNOWN";
        return up ? "UP" : down ? "DOWN" : "STOP";
    }
    static int faultWinch(int code) {
        if (code >= 10 && code <= 13) return code - 9;
        if (code >= 15 && code <= 18) return code - 14;
        if (code >= 20 && code <= 27) return (code - 20) / 2 + 1;
        if (code >= 28 && code <= 35) return (code - 28) / 2 + 1;
        return code >= 40 && code <= 43 ? code - 39 : 0;
    }
    private TelemetrySample disconnected(PlcDeviceSnapshotBo snapshot, TelemetrySample previous) {
        speedBases.keySet().removeIf(k -> k.startsWith(snapshot.deviceId() + "/"));
        boolean maintenance = previous != null && previous.status() != null
                && previous.status().systemStatus() == Status.MAINTENANCE;
        List<TelemetrySample.RopeSample> ropes = previous == null ? List.of() : previous.ropes().stream()
                .map(p -> new TelemetrySample.RopeSample(p.ropeId(), p.sensorId(), p.winchId(), p.tension(), false)).toList();
        List<TelemetrySample.WinchSample> winches = previous == null ? List.of() : previous.winches().stream()
                .map(p -> new TelemetrySample.WinchSample(p.winchId(), p.heightMeters(), null, p.tensionAverage(),
                        false, false, p.faultCode(), p.torqueKg(), false, false, false,
                        maintenance ? "MAINTENANCE" : "OFFLINE", "UNKNOWN", p.validTensionCount())).toList();
        return new TelemetrySample(snapshot.deviceId(), snapshot.sequence(), previous == null ? snapshot.sampledAt() : previous.sampledAt(),
                ropes, winches, null, null, null, null,
                new SystemRealtimeStatusBo(maintenance ? Status.MAINTENANCE : Status.OFFLINE, false, true,
                        false, "", "UNKNOWN",previous==null||previous.status()==null?"D201":previous.status().source()), null, false, 0, 0, null);
    }
    private TelemetrySample legacy(PlcDeviceSnapshotBo snapshot, TelemetrySample previous) {
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
