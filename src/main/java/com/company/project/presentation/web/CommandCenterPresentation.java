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

    public CommandCenterPresentation(CleaningLobbyService lobby, PageAccess access, PageSnapshotConverter snapshots) {
        this.lobby = lobby;
        this.access = access;
        this.snapshots = snapshots;
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
        requireWinch(query, auth);
        throw new PageApiException(409, "CONFIGURATION_NOT_READY", "尚未取得本设备已确认生效的设定速度及配置版本");
    }

    public WinchTrendResponse winchTrend(WinchTrendQuery query, Authentication auth) {
        requireWinch(query, auth);
        throw historyNotReady();
    }

    public ArchiveResponse archive(ArchiveQuery query, Authentication auth) {
        requireWinch(query, auth);
        throw new PageApiException(503, "STATISTICS_NOT_READY", "历史周期统计及归档查询尚未接通");
    }

    public RunListResponse historyList(RunListQuery query, Authentication auth) {
        var resolved = access.resolve(query.getSystemCode(), null, "device:read", auth);
        if (query.getWinchCode() != null) access.winch(resolved.system(), query.getWinchCode());
        throw historyNotReady();
    }

    public RunDetailResponse historyDetail(RunDetailQuery query, Authentication auth) {
        var resolved = access.resolve(query.getSystemCode(), null, "device:read", auth);
        if (query.getWinchCode() != null) access.winch(resolved.system(), query.getWinchCode());
        throw historyNotReady();
    }

    public SamplePointResponse historyPoint(SamplePointQuery query, Authentication auth) {
        var resolved = access.resolve(query.getSystemCode(), null, "device:read", auth);
        if (query.getWinchCode() != null) access.winch(resolved.system(), query.getWinchCode());
        throw historyNotReady();
    }

    public ParameterSettingsResponse parameterSettings(String systemCode, Authentication auth) {
        var resolved = access.resolve(systemCode, null, "device:read", auth);
        var settings = lobby.querySettings(resolved.context());
        boolean writtenOnly = "WRITTEN_NOT_PERSISTED".equals(settings.state());
        // 旧状态只有写入副本，没有 saved/applied 持久版本；不得投影为已生效参数。
        return ParameterSettingsResponse.builder().systemCode(systemCode)
                .applyStatus(writtenOnly ? "UNCONFIRMED" : "NOT_READY")
                .message(writtenOnly ? "已有写应答，但保存版本及现场生效尚未确认" : "参数保存及现场回读尚未接通")
                .asOf(Instant.now()).build();
    }

    public ParameterSettingsResponse updateParameters(ParameterUpdateRequest request, Authentication auth) {
        access.resolve(request.systemCode(), request.requestId(), "device:write", auth);
        throw new PageApiException(409, "CONFIGURATION_NOT_READY", "尚无可用于并发修改的真实保存版本，参数未下发");
    }

    public ThresholdApplyResponse updateThresholds(ThresholdUpdateRequest request, Authentication auth) {
        var resolved = access.resolve(request.systemCode(), request.requestId(), "device:write", auth);
        var winch = access.winch(resolved.system(), request.winchCode());
        Set<String> configured = winch.sensors().stream().map(s -> s.sensorCode()).collect(Collectors.toSet());
        Set<String> requested = request.thresholds().stream().map(ThresholdValue::sensorCode).collect(Collectors.toSet());
        if (!configured.equals(requested)) throw new PageApiException(400, "SENSOR_MISMATCH", "必须提交该设备配置的两路传感器阈值");
        throw new PageApiException(503, "THRESHOLD_MAPPING_NOT_READY", "阈值写入及两路回读协议尚未确认，本次未下发");
    }

    private void requireWinch(WinchQuery query, Authentication auth) {
        var resolved = access.resolve(query.getSystemCode(), null, "device:read", auth);
        access.winch(resolved.system(), query.getWinchCode());
    }

    private PageApiException historyNotReady() {
        return new PageApiException(503, "HISTORY_NOT_READY", "生产上行记录、曲线及游标查询尚未接通");
    }
}
