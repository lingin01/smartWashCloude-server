package com.company.project.presentation.web;

import com.company.project.domain.Result;
import com.company.project.domain.bo.CleaningCommandStatusBo;
import com.company.project.domain.exception.PageApiException;
import com.company.project.domain.param.cleaning.EmergencyStopParam;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.service.CleaningLobbyService;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/** 页面协议适配。已有业务经过大厅；尚未落地的历史/配置能力明确报告未就绪。 */
@Component
public class CommandCenterPresentation {
    private final CleaningLobbyService lobby;
    private final PageAccess access;
    private final PageSnapshotConverter snapshots;
    private final HistoryPageQueries historyQueries;

    public CommandCenterPresentation(CleaningLobbyService lobby, PageAccess access, PageSnapshotConverter snapshots) {
        this.lobby = lobby;
        this.access = access;
        this.snapshots = snapshots;
        this.historyQueries=new HistoryPageQueries(lobby,access);
    }

    public HomeSnapshot homeSnapshot(String systemCode, Authentication auth) {
        var resolved = access.resolve(systemCode, null, "device:read", auth);
        return snapshots.home(resolved.system(), lobby.queryRealtime(resolved.context()));
    }

    public ResponseEntity<Result<CommandReceipt>> emergencyStop(EmergencyStopRequest request, Authentication auth) {
        var resolved = access.resolve(request.systemCode(), request.requestId(), "device:write", auth);
        var result = lobby.emergencyStop(new EmergencyStopParam(resolved.context(), request.reason()));
        if ("SEND_QUEUE_FULL".equals(result.code())) throw new PageApiException(429, result.code(), result.message());
        if (result.state() == CleaningCommandStatusBo.State.BLOCKED || result.state() == CleaningCommandStatusBo.State.FAILED)
            throw new PageApiException(503, result.code(), result.message());
        if (result.state() == CleaningCommandStatusBo.State.REJECTED)
            throw new PageApiException(409, result.code(), result.message());
        boolean accepted = result.state() == CleaningCommandStatusBo.State.CREATED || result.state() == CleaningCommandStatusBo.State.QUEUED;
        return ResponseEntity.status(accepted ? 202 : 200).body(Result.success(receipt(request.systemCode(), result)));
    }

    public CommandReceipt command(String systemCode, String commandCode, Authentication auth) {
        var resolved = access.resolve(systemCode, null, "device:read", auth);
        var command = lobby.queryOperationByCommandCode(resolved.context(), commandCode)
                .orElseThrow(() -> new PageApiException(404, "COMMAND_NOT_FOUND", "该系统中不存在此命令"));
        return receipt(systemCode, command);
    }

    private CommandReceipt receipt(String systemCode, CleaningCommandStatusBo command) {
        String phase = switch (command.state()) {
            case CREATED -> "ACCEPTED";
            case SUCCEEDED -> "WRITE_CONFIRMED";
            default -> command.state().name();
        };
        String message = command.message();
        if (command.state() == CleaningCommandStatusBo.State.SUCCEEDED)
            message += "；仅确认协议写入，尚未确认现场设备已停止或参数已生效";
        return new CommandReceipt(systemCode, command.requestId(), command.commandId(), command.state().name(),
                phase, false, message, command.createdAt(), command.updatedAt());
    }

    public WinchDetailResponse winchDetail(WinchQuery query, Authentication auth) {
        var resolved=access.resolve(query.getSystemCode(),null,"device:read",auth);
        var configured=access.winch(resolved.system(),query.getWinchCode());
        var settings=lobby.storedSettings(resolved.context());
        if(settings==null)throw new PageApiException(409,"CONFIGURATION_NOT_READY","配置存储尚未启用");
        var home=snapshots.home(resolved.system(),lobby.queryRealtime(resolved.context()));
        var winch=home.winches().stream().filter(w->w.winchCode().equals(query.getWinchCode())).findFirst().orElseThrow();
        var reader=lobby.history(resolved.context());
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var start=today.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        var periods=reader.periods("DAY",start,start.plusSeconds(86400),configured.winchId());
        var period=periods.stream().findFirst().orElse(null);
        var threshold=lobby.thresholds(resolved.context(),configured.winchId());
        var thresholdSettings=threshold==null?null:new ThresholdSettings(Long.toString(threshold.savedVersion()),
                threshold.saved().stream().map(v->new Threshold(v.sensorCode(),v.alarmThresholdKg().movePointLeft(3),v.safetyValueKg().movePointLeft(3))).toList(),
                threshold.appliedVersion()==null?null:threshold.appliedVersion().toString(),
                threshold.appliedVersion()==null?null:threshold.applied().stream().map(v->new Threshold(v.sensorCode(),v.alarmThresholdKg().movePointLeft(3),v.safetyValueKg().movePointLeft(3))).toList(),
                threshold.state()==null?"NOT_READY":threshold.state(),null,"平台保存与现场生效分别确认");
        return WinchDetailResponse.builder().systemCode(query.getSystemCode()).winch(winch)
                .sensors(home.sensors().stream().filter(s->query.getWinchCode().equals(s.winchCode())).toList()).currentHeight(winch.currentHeight())
                .currentMode(home.systemStatus()).setSpeedMPerMinute(settings.applied()==null?null:settings.applied().groupSpeedMetersPerMinute())
                .configurationVersion(settings.appliedVersion()==null?null:settings.appliedVersion().toString())
                .todayCompletedCount(period==null?null:period.normalCompleted()+period.abnormalCompleted()).countBasis("M106_STARTED_DAY")
                .countText("按本轮开始日统计").runningHours(period==null?null:java.math.BigDecimal.valueOf(period.motionMillis())
                        .divide(java.math.BigDecimal.valueOf(3600000),4,java.math.RoundingMode.HALF_UP))
                .motionCoverage("OBSERVED_DIRECTION_INTERVALS").torqueForce(new Measurement(winch.torqueForce(),"t","电机等效拉力",null,null))
                .thresholdSettings(thresholdSettings).statisticsState(period==null?"NOT_BUILT":"READY").notices(home.notices())
                .updatedAt(home.updatedAt()).asOf(Instant.now()).build();
    }

    public WinchTrendResponse winchTrend(WinchTrendQuery query, Authentication auth) {
        return historyQueries.trend(query,auth);
    }

    public ArchiveResponse archive(ArchiveQuery query, Authentication auth) {
        return new ArchivePageQueries(lobby,access).query(query,auth);
    }

    public RunListResponse historyList(RunListQuery query, Authentication auth) {
        return historyQueries.list(query,auth);
    }

    public RunDetailResponse historyDetail(RunDetailQuery query, Authentication auth) {
        return historyQueries.detail(query,auth);
    }

    public SamplePointResponse historyPoint(SamplePointQuery query, Authentication auth) {
        return historyQueries.point(query,auth);
    }

    public ParameterSettingsResponse parameterSettings(String systemCode, Authentication auth) {
        var resolved = access.resolve(systemCode, null, "device:read", auth);
        var stored=lobby.storedSettings(resolved.context());
        if(stored!=null)return settingsResponse(systemCode,stored);
        var settings = lobby.querySettings(resolved.context());
        boolean writtenOnly = "WRITTEN_NOT_PERSISTED".equals(settings.state());
        // 旧状态只有写入副本，没有 saved/applied 持久版本；不得投影为已生效参数。
        return ParameterSettingsResponse.builder().systemCode(systemCode)
                .applyStatus(writtenOnly ? "UNCONFIRMED" : "NOT_READY")
                .message(writtenOnly ? "已有写应答，但保存版本及现场生效尚未确认" : "参数保存及现场回读尚未接通")
                .asOf(Instant.now()).build();
    }

    public ParameterSettingsResponse updateParameters(ParameterUpdateRequest request, Authentication auth) {
        var resolved=access.resolve(request.systemCode(), request.requestId(), "device:write", auth);
        long version;
        try {version=Long.parseLong(request.expectedVersion());}
        catch(NumberFormatException invalid){throw new PageApiException(400,"INVALID_VERSION","配置版本格式无效");}
        var value=new com.company.project.domain.bo.CleaningSettingsBo(java.time.LocalTime.parse(request.startTime()),
                Math.multiplyExact(request.intervalMinutes().longValue(),60L),request.speedMPerMinute());
        var result=lobby.saveSettings(resolved.context(),version,value);
        if(result==null)throw new PageApiException(409,"CONFIGURATION_NOT_READY","配置持久化尚未启用");
        return settingsResponse(request.systemCode(),result);
    }
    private ParameterSettingsResponse settingsResponse(String systemCode,com.company.project.domain.bo.StoredSettingsBo stored) {
        var saved=stored.saved();var applied=stored.applied();
        Settings savedView=saved==null?null:new Settings(saved.startTime().toString(),Math.toIntExact(saved.intervalSeconds()/60),saved.groupSpeedMetersPerMinute());
        Settings appliedView=applied==null?null:new Settings(applied.startTime().toString(),Math.toIntExact(applied.intervalSeconds()/60),applied.groupSpeedMetersPerMinute());
        return ParameterSettingsResponse.builder().systemCode(systemCode).savedVersion(Long.toString(stored.savedVersion()))
                .savedSettings(savedView).appliedVersion(stored.appliedVersion()==null?null:stored.appliedVersion().toString())
                .appliedSettings(appliedView).startTime(appliedView==null?null:appliedView.startTime())
                .intervalMinutes(appliedView==null?null:appliedView.intervalMinutes()).speedMPerMinute(appliedView==null?null:appliedView.speedMPerMinute())
                .requestId(stored.requestId()).commandCode(stored.commandCode())
                .applyStatus(stored.state()==null?"NOT_READY":stored.state()).message(stored.message()).asOf(Instant.now()).build();
    }

    public ThresholdApplyResponse updateThresholds(ThresholdUpdateRequest request, Authentication auth) {
        var resolved = access.resolve(request.systemCode(), request.requestId(), "device:write", auth);
        var winch = access.winch(resolved.system(), request.winchCode());
        Set<String> configured = winch.sensors().stream().map(s -> s.sensorCode()).collect(Collectors.toSet());
        Set<String> requested = request.thresholds().stream().map(ThresholdValue::sensorCode).collect(Collectors.toSet());
        if (!configured.equals(requested)) throw new PageApiException(400, "SENSOR_MISMATCH", "必须提交该设备配置的两路传感器阈值");
        long version;
        try {version=Long.parseLong(request.expectedVersion());}catch(NumberFormatException invalid){throw new PageApiException(400,"INVALID_VERSION","阈值版本无效");}
        var values=request.thresholds().stream().map(v->new com.company.project.domain.bo.StoredThresholdsBo.Value(v.sensorCode(),
                v.alarmThreshold().movePointRight(3),v.safetyValue().movePointRight(3))).toList();
        var saved=lobby.saveThresholds(resolved.context(),winch.winchId(),version,values);
        if(saved==null)throw new PageApiException(503,"THRESHOLD_MAPPING_NOT_READY","阈值存储尚未启用");
        return new ThresholdApplyResponse(request.systemCode(),request.winchCode(),request.requestId(),saved.commandCode(),"SAVED",
                Long.toString(saved.savedVersion()),saved.appliedVersion()==null?null:saved.appliedVersion().toString(),
                saved.appliedVersion()==null?null:saved.applied().stream().map(v->new Threshold(v.sensorCode(),
                        v.alarmThresholdKg().movePointLeft(3),v.safetyValueKg().movePointLeft(3))).toList(),
                saved.appliedVersion()==null?"UNCONFIRMED":"LAST_CONFIRMED","平台已保存，现场点位未配置时不下发",false);
    }

    private void requireWinch(WinchQuery query, Authentication auth) {
        var resolved = access.resolve(query.getSystemCode(), null, "device:read", auth);
        access.winch(resolved.system(), query.getWinchCode());
    }

    private PageApiException historyNotReady() {
        return new PageApiException(503, "HISTORY_NOT_READY", "生产上行记录、曲线及游标查询尚未接通");
    }
}
