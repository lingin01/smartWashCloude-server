package com.company.project.network_access_layer.web;
import com.company.project.dto.cloud.*;
import com.company.project.domain.Result;
import com.company.project.presentation.web.CloudCleaningWebPresentation;
import com.company.project.vo.cloud.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 用户侧网络接入：只接收 HTTP 对象并交表示层，不直接读缓冲、DAO 或发送设备报文。 */
@RestController
@RequestMapping("/api/cloud/cleaning/boxes/{boxId}")
public class CleaningWebService {
    private final CloudCleaningWebPresentation presentation;
    public CleaningWebService(CloudCleaningWebPresentation presentation) { this.presentation = presentation; }
    @PostMapping("/emergency-stop")
    public ResponseEntity<Result<CloudCommandVo>> emergencyStop(@PathVariable String boxId,
            @RequestBody CloudEmergencyStopDto request, Authentication auth) {
        return presentation.emergencyStop(boxId, request, auth);
    }
    @PutMapping("/settings")
    public ResponseEntity<Result<CloudCommandVo>> updateSettings(@PathVariable String boxId,
            @RequestBody CloudSettingsUpdateDto request, Authentication auth) {
        return presentation.updateSettings(boxId, request, auth);
    }
    @GetMapping("/realtime") public Result<CloudRealtimeVo> realtime(@PathVariable String boxId, Authentication auth) {
        return presentation.realtime(boxId, auth);
    }
    @GetMapping("/settings") public Result<CloudSettingsVo> settings(@PathVariable String boxId, Authentication auth) {
        return presentation.settings(boxId, auth);
    }
    @GetMapping("/commands/{requestId}") public Result<CloudCommandVo> operation(@PathVariable String boxId,
            @PathVariable String requestId, Authentication auth) {
        return presentation.operation(boxId, requestId, auth);
    }
}
