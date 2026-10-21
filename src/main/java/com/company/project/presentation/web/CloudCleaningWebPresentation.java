package com.company.project.presentation.web;
import com.company.project.dto.cloud.*;
import com.company.project.domain.Result;
import com.company.project.domain.bo.CloudCommandStatusBo;
import com.company.project.service.CloudLobbyService;
import com.company.project.vo.cloud.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 用户网络入口统一调用本表示层，再调用大厅的操作与查询接口。 */
@Component
public class CloudCleaningWebPresentation {
    private final CloudLobbyService lobby;
    private final CloudCleaningWebConverter converter;
    public CloudCleaningWebPresentation(CloudLobbyService lobby, CloudCleaningWebConverter converter) {
        this.lobby = lobby; this.converter = converter;
    }
    public ResponseEntity<Result<CloudCommandVo>> emergencyStop(String boxId, CloudEmergencyStopDto dto, Authentication auth) {
        return operationResponse(lobby.emergencyStop(converter.emergency(boxId, dto, auth)));
    }
    public ResponseEntity<Result<CloudCommandVo>> updateSettings(String boxId, CloudSettingsUpdateDto dto, Authentication auth) {
        return operationResponse(lobby.updateSettings(converter.settings(boxId, dto, auth)));
    }
    private ResponseEntity<Result<CloudCommandVo>> operationResponse(CloudCommandStatusBo status) {
        int http = status.state() == CloudCommandStatusBo.State.BLOCKED ? 503
                : "SEND_QUEUE_FULL".equals(status.code()) ? 429
                : status.state().terminal() || status.state() == CloudCommandStatusBo.State.UNCONFIRMED ? 200 : 202;
        return ResponseEntity.status(http).body(new Result<>(http, status.message(), converter.commandVo(status)));
    }
    public Result<CloudRealtimeVo> realtime(String boxId, Authentication auth) {
        var sample = lobby.queryRealtime(converter.context(boxId, null, auth));
        return Result.success(converter.realtimeVo(boxId, sample));
    }
    public Result<CloudSettingsVo> settings(String boxId, Authentication auth) {
        return Result.success(converter.settingsVo(lobby.querySettings(converter.context(boxId, null, auth))));
    }
    public Result<CloudCommandVo> operation(String boxId, String requestId, Authentication auth) {
        var status = lobby.queryOperation(converter.context(boxId, null, auth), requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "操作记录不存在"));
        return Result.success(converter.commandVo(status));
    }
}
