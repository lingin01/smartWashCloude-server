package com.company.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/** 本地通信配置。未知点位默认禁用；只在设备层解释地址，页面不能任意指定地址。 */
@Data
@Component
@ConfigurationProperties(prefix = "cleaning.plc")
public class PlcAccessProperties {
    private static final Set<String> WINCH_FIELDS = Set.of(
            "winchHeight", "winchSpeed", "winchTorque", "winchRunning", "winchFault", "encoderFault",
            "winchState", "winchUp", "winchDown", "encoderCount", "axisRunSeconds",
            "motorEnableFaultCode", "motorRunningFaultCode");
    private boolean enabled;
    private long workerIntervalMs = 50;
    private int queueCapacity = 256;
    private List<Device> devices = new ArrayList<>();

    public Optional<Device> activeDevice(String deviceId) {
        if (!enabled) return Optional.empty();
        return devices.stream().filter(d -> d.enabled && Objects.equals(d.id, deviceId)).findFirst();
    }

    public void validate() {
        if (workerIntervalMs < 10 || queueCapacity < 1 || queueCapacity > 4096 || devices.size() > 64)
            throw new IllegalArgumentException("PLC 工作周期、队列容量或设备数量无效");
        Set<String> ids = new HashSet<>();
        for (Device d : devices) {
            if (!d.enabled) continue;
            if (blank(d.id) || !ids.add(d.id)
                    || blank(d.host) || d.port < 1 || d.port > 65535 || d.unitId < 0 || d.unitId > 255
                    || d.pollIntervalMs < 50 || d.timeoutMs < 50 || d.timeoutMs > 10000
                    || d.maxReadRegisters < 1 || d.maxReadRegisters > 125
                    || d.maxWriteRegisters < 1 || d.maxWriteRegisters > 123)
                throw new IllegalArgumentException("PLC 连接或归属配置无效");
            Set<String> points = new HashSet<>(), fields = new HashSet<>();
            if(d.heightMin!=null&&d.heightMax!=null&&d.heightMin.compareTo(d.heightMax)>=0)
                throw new IllegalArgumentException("统一最低高度须小于最高高度");
            if(d.encoderBottomCounts==null||d.encoderBottomCounts.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey()<1||e.getKey()>4||e.getValue()==null
                    ||e.getValue().scale()>0&&e.getValue().stripTrailingZeros().scale()>0||e.getValue().compareTo(BigDecimal.valueOf(Integer.MIN_VALUE))<0||e.getValue().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))>0))
                throw new IllegalArgumentException("底部编码器值须对应1至4号动力设备的DINT计数");
            Map<Integer, String> winches = new HashMap<>();
            Set<String> ropes = new HashSet<>(), sensors = new HashSet<>();
            for (Point p : d.points) {
                if (!p.enabled) continue;
                validatePoint(p, false);
                if (p.functionCode >= 3 && p.dataType.words() > d.maxReadRegisters)
                    throw new IllegalArgumentException("PLC 单个测点超过设备读取寄存器上限");
                if (blank(p.id) || !points.add(p.id) || (!blank(p.field) && !fields.add(p.field + "/" + p.index)))
                    throw new IllegalArgumentException("PLC 测点或业务字段重复");
                int count = switch (p.field == null ? "" : p.field) {
                    case "ropeTension" -> 8;
                    default -> WINCH_FIELDS.contains(p.field == null ? "" : p.field) ? 4 : 1;
                };
                if (p.index < 1 || p.index > count) throw new IllegalArgumentException("PLC 业务测点槽位无效");
                String unit = switch (p.field == null ? "" : p.field) {
                    case "ropeTension", "winchTorque" -> "kg";
                    case "winchSpeed", "groupSpeed" -> "m/min";
                    case "winchHeight", "ringHeight" -> "m";
                    default -> "";
                };
                if (!unit.isEmpty() && !unit.equals(p.unit)) throw new IllegalArgumentException("测点业务单位未确认");
                if (isWinchField(p.field)
                        && blank(p.winchId)) throw new IllegalArgumentException("本机测点缺少统一业务编号");
                if ("ropeTension".equals(p.field) && blank(p.ropeId) && !blank(p.sensorId)) p.ropeId = p.sensorId;
                if ("ropeTension".equals(p.field) && (blank(p.sensorId) || blank(p.winchId)))
                    throw new IllegalArgumentException("索测点缺少统一业务编号");
                if ("ropeTension".equals(p.field) && (!ropes.add(p.ropeId) || !sensors.add(p.sensorId)))
                    throw new IllegalArgumentException("不同索槽位不能复用同一索或传感器编号");
                if ("ropeTension".equals(p.field) || isWinchField(p.field)) {
                    int slot = "ropeTension".equals(p.field) ? (p.index - 1) / 2 + 1 : p.index;
                    String previous = winches.putIfAbsent(slot, p.winchId);
                    if (previous != null && !previous.equals(p.winchId))
                        throw new IllegalArgumentException("同一绞机槽位的测点归属不一致");
                }
            }
            if (new HashSet<>(winches.values()).size() != winches.size())
                throw new IllegalArgumentException("不同绞机槽位不能复用同一业务编号");
            if (d.emergencyStop != null && d.emergencyStop.enabled) validatePoint(d.emergencyStop, true);
            Set<String> parameters = new HashSet<>();
            for (Point p : d.parameters) if (p.enabled) {
                validatePoint(p, true);
                if (p.functionCode == 16 && p.dataType.words() > d.maxWriteRegisters)
                    throw new IllegalArgumentException("PLC 单个参数超过设备写入寄存器上限");
                if (!Set.of("startTime", "intervalSeconds", "groupSpeedMetersPerMinute").contains(p.field)
                        || !parameters.add(p.field) || blank(p.encoding))
                    throw new IllegalArgumentException("三参数映射或编码无效");
            }
        }
    }

    public static void validatePoint(Point p, boolean write) {
        if (p.address == null || p.address < 0 || p.address > 65535 || p.dataType == null
                || p.byteOrder == null || p.scale == null || p.scale.signum() <= 0 || p.offset == null
                || p.address + p.dataType.words() > 65536)
            throw new IllegalArgumentException("PLC 点位地址、类型、倍率未确认");
        if (write) {
            if (p.functionCode != 5 && p.functionCode != 6 && p.functionCode != 16)
                throw new IllegalArgumentException("不支持的 PLC 写功能码");
            if ((p.functionCode == 5 && p.dataType != DataType.BOOLEAN)
                    || (p.functionCode == 6 && p.dataType.words() != 1)
                    || (p.functionCode != 5 && p.dataType == DataType.BOOLEAN))
                throw new IllegalArgumentException("PLC 写功能码与类型不一致");
        } else if (p.functionCode < 1 || p.functionCode > 4
                || ((p.functionCode <= 2) && p.dataType != DataType.BOOLEAN)) {
            throw new IllegalArgumentException("不支持的 PLC 读点配置");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean isWinchField(String field) {
        return field != null && (field.startsWith("winch") || WINCH_FIELDS.contains(field));
    }

    @Data
    public static class Device {
        private String id;
        private boolean enabled;
        private boolean writesEnabled;
        private String host;
        private int port = 502;
        private int unitId = 1;
        private long pollIntervalMs = 500;
        private int timeoutMs = 1000;
        /** 信捷文档规定单次寄存器读写最多120个；其他设备可按实际能力分别配置。 */
        private int maxReadRegisters = 120;
        private int maxWriteRegisters = 120;
        /** 必须人工核对，不能根据进程重启时间生成配置版本。 */
        private Long configurationVersion;
        /** 每台独立实测底部计数；未填写时不把首次读数或零冒充标定值。 */
        private Map<Integer, BigDecimal> encoderBottomCounts = new HashMap<>();
        private BigDecimal heightMin;
        private BigDecimal heightMax;
        private List<Point> points = new ArrayList<>();
        private Point emergencyStop;
        private List<Point> parameters = new ArrayList<>();
    }

    @Data
    public static class Point {
        private boolean enabled;
        private String id;
        private String symbol;
        /** Modbus 零基协议地址，不将 m7/d40 的数字直接作为地址。 */
        private Integer address;
        private int functionCode = 3;
        private DataType dataType;
        private ByteOrder byteOrder = ByteOrder.ABCD;
        private BigDecimal scale = BigDecimal.ONE;
        private BigDecimal offset = BigDecimal.ZERO;
        private BigDecimal invalidValue;
        private String field;
        private int index = 1;
        private String unit;
        private String rawUnit;
        private String ropeId;
        private String sensorId;
        private String winchId;
        private BigDecimal activeValue = BigDecimal.ONE;
        private BigDecimal value;
        private String encoding;
    }

    public enum DataType {
        BOOLEAN, INT16, UINT16, INT32, UINT32, FLOAT32, FLOAT64;
        public int words() { return switch (this) { case INT32, UINT32, FLOAT32 -> 2; case FLOAT64 -> 4; default -> 1; }; }
    }
    public enum ByteOrder { ABCD, BADC, CDAB, DCBA }
}
