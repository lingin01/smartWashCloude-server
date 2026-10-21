package com.company.project.service.impl;

import com.company.project.domain.bo.*;
import com.company.project.domain.param.cloud.*;
import com.company.project.info.buffer.cleaning.CloudRuntimeDataCenter;
import com.company.project.domain.bo.plc.*;
import java.util.concurrent.ConcurrentHashMap;
import com.company.project.service.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class CloudCleaningServiceImpl implements CloudCleaningService {
    private final CloudCommandService commands;
    private final CloudRuntimeDataCenter realtime;
    private final CleaningDeviceConverter deviceConverter = new CleaningDeviceConverter();
    private final Map<String, CloudSettingsViewBo> writtenSettings = new ConcurrentHashMap<>();
    public CloudCleaningServiceImpl(CloudCommandService commands, CloudRuntimeDataCenter realtime) {
        this.commands = commands; this.realtime = realtime;
    }
    @Override public CloudCommandStatusBo emergencyStop(CloudEmergencyStopParam request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.sessionId() != null) payload.put("sessionId", request.sessionId());
        payload.put("reason", request.reason() == null || request.reason().isBlank() ? "用户急停" : request.reason());
        return commands.submit(command(request.context(), "emergency_stop", payload));
    }
    @Override public CloudCommandStatusBo updateSettings(CloudSettingsUpdateParam request) {
        var settings = request.settings();
        if (request.expectedVersion() < 0 || settings == null || settings.startTime() == null
                || settings.intervalSeconds() == null || settings.intervalSeconds() <= 0)
            throw new IllegalArgumentException("请提供当前配置版本、开始时间及有效运行间隔");
        if (settings.speedLevel() == null && settings.groupSpeedMetersPerMinute() == null)
            throw new IllegalArgumentException("请提供整组速度或挡位");
        if ((settings.speedLevel() != null && settings.speedLevel() < 0)
                || (settings.groupSpeedMetersPerMinute() != null && settings.groupSpeedMetersPerMinute().signum() <= 0))
            throw new IllegalArgumentException("整组速度或挡位无效");
        // 当前业务使用明确的整组速度；挡位映射尚未提供，不能自行猜测或伪造速度。
        if (settings.groupSpeedMetersPerMinute() == null)
            throw new IllegalArgumentException("挡位与速度映射尚未配置，请提供整组速度");
        if (settings.groupSpeedMetersPerMinute().compareTo(java.math.BigDecimal.TEN) > 0)
            throw new IllegalArgumentException("整组速度不得超过 10m/min");
        if (settings.startTime().getSecond() != 0 || settings.startTime().getNano() != 0)
            throw new IllegalArgumentException("启动时间仅支持小时和分钟");
        // 业务参数不再编码成传输协议 JSON；PLC 点位编码留在后续设备控制层。
        return commands.submit(command(request.context(), "update_cleaning_parameters",
                Map.of("expectedVersion", request.expectedVersion(), "settings", settings)));
    }
    private static CloudCommand command(CloudOperationContext context, String type, Map<String, Object> payload) {
        Instant now = Instant.now();
        return new CloudCommand(UUID.randomUUID().toString(), null, context.boxId(), type,
                payload, now, context.requestId(), context.operatorId(), now.plusSeconds(30));
    }
    @Override public Optional<TelemetrySample> latest(String boxId) { return realtime.latestSnapshot(boxId); }
    @Override public CloudSettingsViewBo settings(String boxId) {
        // 申请值不能冒充实际生效配置；正式配置 DAO 与 PLC 下行确认在后续阶段接入。
        return writtenSettings.getOrDefault(boxId, new CloudSettingsViewBo(boxId, "NOT_SYNCHRONIZED", null, null));
    }
    @Override public Optional<CloudCommandStatusBo> operation(String boxId, String requestId) {
        return commands.find(boxId, requestId);
    }
    @Override public void acceptDeviceSnapshot(PlcDeviceSnapshotBo snapshot) {
        var previous = realtime.latestSnapshot(snapshot.boxId()).orElse(null);
        realtime.updateLatest(deviceConverter.toRealtime(snapshot, previous));
    }
    @Override public void acceptDeviceWriteResult(PlcOperationResultBo result) {
        if (result.success() && result.operation().settings() != null) {
            // 协议写入确认不冒充持久化提交或设备运行生效；版本未提交保持为空。
            writtenSettings.put(result.operation().boxId(), new CloudSettingsViewBo(result.operation().boxId(),
                    "WRITTEN_NOT_PERSISTED", null, result.operation().settings()));
        }
    }
}
