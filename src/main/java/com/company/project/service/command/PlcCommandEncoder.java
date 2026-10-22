package com.company.project.service.command;

import com.company.project.config.PlcAccessProperties;
import com.company.project.config.PlcAccessProperties.*;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcOperationBo;
import com.company.project.domain.message.Message;
import com.company.project.infrastructure.plc.ModbusTcpCodec;
import com.company.project.repository.device.DeviceManager;
import java.math.BigDecimal;
import java.util.*;

/** 下行转换集中在类中；业务主流程不散落 PLC 地址、倍率或时间编码。 */
final class PlcCommandEncoder {
    public static final class NotReady extends IllegalStateException {
        private final String code;
        public NotReady(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
    private final DeviceManager devices;
    public PlcCommandEncoder(DeviceManager devices) { this.devices = devices; }
    public int capacity() { return devices.commandCapacity(); }
    public Message<PlcOperationBo> prepare(CleaningCommand command) {
        Device device = devices.activeDevice(command.deviceId())
                .orElseThrow(() -> new NotReady("PLC_DOWNLINK_NOT_READY"));
        if (!device.isWritesEnabled()) throw new NotReady("PLC_WRITES_DISABLED");
        List<PlcOperationBo.Write> writes = new ArrayList<>();
        CleaningSettingsBo settings = null;
        long version = 0;
        if ("emergency_stop".equals(command.type())) {
            Point point = device.getEmergencyStop();
            if (point == null || !point.isEnabled() || point.getValue() == null)
                throw new NotReady("PLC_EMERGENCY_POINT_NOT_READY");
            PlcAccessProperties.validatePoint(point, true);
            writes.add(write(point, point.getValue()));
        } else if ("update_cleaning_parameters".equals(command.type())) {
            Object supplied = command.parameters().get("settings");
            Object expected = command.parameters().get("expectedVersion");
            if (!(supplied instanceof CleaningSettingsBo value) || !(expected instanceof Number number))
                throw new IllegalArgumentException("缺少配置业务对象或版本");
            settings = value; version = number.longValue();
            if (device.getConfigurationVersion() == null) throw new NotReady("PLC_CONFIGURATION_VERSION_NOT_READY");
            for (String field : List.of("startTime", "intervalSeconds", "groupSpeedMetersPerMinute")) {
                Point p = device.getParameters().stream().filter(x -> x.isEnabled() && field.equals(x.getField()))
                        .findFirst().orElseThrow(() -> new NotReady("PLC_SETTINGS_POINT_NOT_READY"));
                PlcAccessProperties.validatePoint(p, true);
                BigDecimal business = switch (field) {
                    case "startTime" -> switch (p.getEncoding() == null ? "" : p.getEncoding()) {
                        case "HHMM" -> BigDecimal.valueOf(value.startTime().getHour() * 100L + value.startTime().getMinute());
                        case "MINUTES_OF_DAY" -> BigDecimal.valueOf(value.startTime().getHour() * 60L + value.startTime().getMinute());
                        default -> throw new NotReady("PLC_TIME_ENCODING_NOT_READY");
                    };
                    case "intervalSeconds" -> switch (p.getEncoding() == null ? "" : p.getEncoding()) {
                        case "SECONDS" -> BigDecimal.valueOf(value.intervalSeconds());
                        case "MILLISECONDS" -> BigDecimal.valueOf(value.intervalSeconds()).multiply(BigDecimal.valueOf(1000));
                        default -> throw new NotReady("PLC_INTERVAL_ENCODING_NOT_READY");
                    };
                    default -> {
                        if (!"IDENTITY".equals(p.getEncoding())) throw new NotReady("PLC_SPEED_ENCODING_NOT_READY");
                        yield value.groupSpeedMetersPerMinute();
                    }
                };
                writes.add(write(p, business));
            }
        } else throw new IllegalArgumentException("当前只支持急停和清洗参数修改");
        PlcOperationBo operation = new PlcOperationBo(command.requestId(), command.commandId(), device.getId(), command.type(), command.expiresAt(), version, device.getConfigurationVersion(), settings, writes);
        return Message.create(command.requestId(), device.getId(), Message.Type.PLC_OPERATION,
                operation.emergency() ? 100 : 10, command.expiresAt(), operation);
    }

    private static PlcOperationBo.Write write(Point p, BigDecimal value) {
        if (p.getFunctionCode() == 5) {
            if (value.compareTo(BigDecimal.ZERO) != 0 && value.compareTo(BigDecimal.ONE) != 0)
                throw new IllegalArgumentException("线圈写值必须明确为 0 或 1");
            return new PlcOperationBo.Write(5, p.getAddress(), List.of(), value.signum() != 0);
        }
        return new PlcOperationBo.Write(p.getFunctionCode(), p.getAddress(), ModbusTcpCodec.encode(p, value), null);
    }
}
