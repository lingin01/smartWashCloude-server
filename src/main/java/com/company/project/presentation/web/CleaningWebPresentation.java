package com.company.project.presentation.web;
import com.company.project.dto.cleaning.*;
import com.company.project.domain.Result;
import com.company.project.domain.bo.CleaningCommandStatusBo;
import com.company.project.service.CleaningLobbyService;
import com.company.project.vo.cleaning.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 用户网络入口统一调用本表示层，再调用大厅的操作与查询接口。 */
@Component
public class CleaningWebPresentation {
    private final CleaningLobbyService lobby;
    private final CleaningWebConverter converter;
    public CleaningWebPresentation(CleaningLobbyService lobby, CleaningWebConverter converter) {
        this.lobby = lobby; this.converter = converter;
    }
    public ResponseEntity<Result<CleaningCommandVo>> emergencyStop(String deviceId, EmergencyStopDto dto, Authentication auth) {
        return operationResponse(lobby.emergencyStop(converter.emergency(deviceId, dto, auth)));
    }
    public ResponseEntity<Result<CleaningCommandVo>> updateSettings(String deviceId, SettingsUpdateDto dto, Authentication auth) {
        return operationResponse(lobby.updateSettings(converter.settings(deviceId, dto, auth)));
    }
    private ResponseEntity<Result<CleaningCommandVo>> operationResponse(CleaningCommandStatusBo status) {
        int http = status.state() == CleaningCommandStatusBo.State.BLOCKED ? 503
                : "SEND_QUEUE_FULL".equals(status.code()) ? 429
                : status.state().terminal() || status.state() == CleaningCommandStatusBo.State.UNCONFIRMED ? 200 : 202;
        return ResponseEntity.status(http).body(new Result<>(http, status.message(), converter.commandVo(status)));
    }
    public Result<CleaningRealtimeVo> realtime(String deviceId, Authentication auth) {
        var sample = lobby.queryRealtime(converter.context(deviceId, null, auth));
        return Result.success(converter.realtimeVo(deviceId, sample));
    }
    public Result<CleaningSettingsVo> settings(String deviceId, Authentication auth) {
        return Result.success(converter.settingsVo(lobby.querySettings(converter.context(deviceId, null, auth))));
    }
    public Result<CleaningCommandVo> operation(String deviceId, String requestId, Authentication auth) {
        var status = lobby.queryOperation(converter.context(deviceId, null, auth), requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "操作记录不存在"));
        return Result.success(converter.commandVo(status));
    }
    public Result<CleaningStatisticsVo> statistics(String deviceId,Authentication auth) {
        return Result.success(converter.statisticsVo(lobby.queryStatistics(converter.context(deviceId,null,auth))));
    }
}
