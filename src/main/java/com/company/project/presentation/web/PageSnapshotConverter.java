package com.company.project.presentation.web;

import com.company.project.domain.bo.SystemRealtimeStatusBo;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 只做公开身份、单位和页面字段转换；不生成设备状态或故障判定。 */
@Component
public class PageSnapshotConverter {
    public HomeSnapshot home(PageSystemBo system, Optional<TelemetrySample> latest) {
        TelemetrySample sample = latest.orElse(null);
        SystemRealtimeStatusBo state = sample == null ? null : sample.status();
        Boolean online = state == null ? null : state.online();
        boolean offline = Boolean.FALSE.equals(online);
        boolean statusValid = state != null && (offline || state.statusValid());
        String systemStatus = statusValid ? offline && state.systemStatus()!=SystemRealtimeStatusBo.Status.MAINTENANCE ? "OFFLINE"
                : state.systemStatus() == null ? null : pageStatus(state.systemStatus().name()) : null;
        List<Winch> winches = new ArrayList<>();
        List<Sensor> sensors = new ArrayList<>();
        List<FaultSource> faults = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();

        for (PageSystemBo.Winch configured : system.winches()) {
            TelemetrySample.WinchSample reading = sample == null ? null : sample.winches().stream()
                    .filter(value -> Objects.equals(configured.winchId(), value.winchId())).findFirst().orElse(null);
            String operatingStatus = reading == null ? null : offline ? "MAINTENANCE".equals(systemStatus)?"MAINTENANCE":"OFFLINE"
                    : statusValid ? pageStatus(reading.status()) : null;
            String direction = reading == null || offline ? null : direction(reading.direction());
            BigDecimal speed = reading != null && !offline && reading.speedValid() ? reading.speed() : null;
            winches.add(Winch.builder().winchCode(configured.winchCode()).winchName(configured.winchName())
                    .groupNo(configured.groupNo()).operatingStatus(operatingStatus).statusText(statusText(operatingStatus))
                    .statusValid(operatingStatus != null).direction(direction).directionText(directionText(direction))
                    .speed(speed).speedSource(speed == null ? "UNCONFIRMED" : "AXIS")
                    .sensorCodes(configured.sensors().stream().map(PageSystemBo.Sensor::sensorCode).toList())
                    .totalTension(reading==null||offline?null:tonnes(reading.totalTension()))
                    .currentHeight(reading==null||offline?null:reading.heightMeters())
                    .torqueForce(reading==null||offline?null:tonnes(reading.torqueKg())).build());
            if (reading != null && !blank(reading.faultCode())) {
                faults.add(FaultSource.builder().objectType("WINCH").objectCode(configured.winchCode())
                        .objectName(configured.winchName()).faultCode(reading.faultCode())
                        .reasonCode("EMERGENCY_STOP".equals(reading.faultCode()) ? "SYSTEM_EMERGENCY" : reading.faultCode())
                        .message("已采集设备故障来源；具体原因以现场状态映射为准").build());
            }
            for (PageSystemBo.Sensor configuredSensor : configured.sensors()) {
                TelemetrySample.RopeSample rope = sample == null ? null : sample.ropes().stream()
                        .filter(value -> Objects.equals(configured.winchId(), value.winchId())
                                && Objects.equals(configuredSensor.sensorId(), value.sensorId())
                                && Objects.equals(configuredSensor.ropeId(), value.ropeId()))
                        .findFirst().orElse(null);
                BigDecimal pressure = rope != null && !offline && rope.valid() ? tonnes(rope.tension()) : null;
                String missingReason = pressure != null ? null : offline ? "PLC_OFFLINE"
                        : rope == null ? "NO_MEASUREMENT" : "INVALID_MEASUREMENT";
                sensors.add(Sensor.builder().sensorCode(configuredSensor.sensorCode()).sensorName(configuredSensor.sensorName())
                        .winchCode(configured.winchCode()).groupNo(configured.groupNo()).ropeNo(configuredSensor.ropeNo())
                        .metric(null).position(null).pressure(pressure).unit("t")
                        .status(offline ? "MAINTENANCE".equals(systemStatus)?"MAINTENANCE":"OFFLINE" : null)
                        .statusText(offline ? "MAINTENANCE".equals(systemStatus)?"检修":"离线" : "测点状态未确认")
                        .statusValid(offline).quality(offline ? "OFFLINE" : pressure == null ? "INVALID" : "GOOD")
                        .missingReason(missingReason).alarmThreshold(null).safetyValue(null).build());
            }
        }
        if (state != null && (state.emergencyActive() || !blank(state.faultCode()))) {
            faults.add(FaultSource.builder().objectType("SYSTEM").objectCode(system.systemCode())
                    .objectName(system.systemName()).faultCode(blank(state.faultCode()) ? null : state.faultCode())
                    .reasonCode(state.emergencyActive() ? "SYSTEM_EMERGENCY" : "PLC_FAULT")
                    .message(state.emergencyActive() ? "已采集急停状态，不代表四台电机本体均故障" : "已采集系统故障状态").build());
        }
        if(state==null||!"D201".equals(state.source()))
            notices.add(new Notice("PLC_STATE_MAPPING_PENDING", "当前使用兼容状态字段，需配置D201及单机状态点", "SYSTEM", system.systemCode()));
        notices.add(new Notice("THRESHOLD_NOT_CONFIGURED", "尚无已确认生效的页面阈值快照", "SYSTEM", system.systemCode()));
        notices.add(new Notice("SENSOR_STATE_UNCONFIRMED", "当前采集对象未提供各传感器的PLC告警等级，不按拉力值推断", "SYSTEM", system.systemCode()));
        boolean movementKnown=sample!=null&&state!=null&&"D201".equals(state.source())&&!offline
                && sample.winches().stream().allMatch(w->!"UNKNOWN".equals(w.direction()));
        if(!movementKnown)notices.add(new Notice("MOVEMENT_NOT_CONFIRMED", "尚无可用于判断运动的方向位", "SYSTEM", system.systemCode()));
        if(sample==null||sample.captureId()==null)notices.add(new Notice("CAPTURE_ID_UNAVAILABLE", "当前采集快照尚未提供采集实例编号", "SYSTEM", system.systemCode()));
        if (sample == null) notices.add(new Notice("NO_SNAPSHOT", "尚未取得该系统的实时采集快照", "SYSTEM", system.systemCode()));

        return HomeSnapshot.builder().systemCode(system.systemCode()).systemName(system.systemName())
                .available(sample != null).systemStatus(systemStatus).systemStatusText(statusText(systemStatus))
                .statusValid(statusValid && systemStatus != null).online(online).systemHeight(null)
                .systemCurrentHeight(sample != null && !offline && sample.heightValid() ? sample.ringHeightMeters() : null)
                .isMoving(movementKnown?sample.winches().stream().anyMatch(TelemetrySample.WinchSample::running):null)
                .movementDirection(state == null || offline ? null : direction(state.direction()))
                .averageTension(sample != null && !offline && sample.validTensionCount() > 0 ? tonnes(sample.groupAverageTension()) : null)
                .averageSpeed(sample != null && !offline && sample.validSpeedCount() > 0 ? sample.groupAverageSpeed() : null)
                .captureId(sample==null?null:sample.captureId()).sequence(sample == null ? null : Long.toString(sample.sequence()))
                .plcValues(state==null||!state.online()?null:state.plcValues())
                .updatedAt(sample == null ? null : sample.sampledAt()).asOf(Instant.now()).pushedAt(null)
                .winches(List.copyOf(winches)).sensors(List.copyOf(sensors)).faultSources(List.copyOf(faults))
                .notices(List.copyOf(notices)).totalTension(sample==null||offline?null:tonnes(sample.systemTotalTension())).build();
    }

    private static BigDecimal tonnes(BigDecimal kilograms) { return kilograms == null ? null : kilograms.movePointLeft(3); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String pageStatus(String value) {
        if (value == null) return null;
        return switch (value) {
            case "STANDBY", "IDLE" -> "IDLE";
            case "RUNNING", "MAINTENANCE", "FAULT", "OFFLINE" -> value;
            default -> null;
        };
    }
    private static String statusText(String value) {
        if (value == null) return "状态未确认";
        return switch (value) {
            case "IDLE" -> "待机"; case "RUNNING" -> "运行"; case "MAINTENANCE" -> "检修";
            case "FAULT" -> "故障"; case "OFFLINE" -> "离线"; default -> "状态未确认";
        };
    }
    private static String direction(String value) {
        return "UP".equals(value) || "DOWN".equals(value) || "STOP".equals(value) ? value : null;
    }
    private static String directionText(String value) {
        if (value == null) return "方向未确认";
        return switch (value) { case "UP" -> "上行"; case "DOWN" -> "下行"; case "STOP" -> "停止"; default -> "方向未确认"; };
    }
}
