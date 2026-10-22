package com.company.project.controller;
import com.company.project.dto.cleaning.*;
import com.company.project.domain.Result;
import com.company.project.presentation.web.CleaningWebPresentation;
import com.company.project.vo.cleaning.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 用户侧网络接入：只接收 HTTP 对象并交表示层，不直接读缓冲、DAO 或发送设备报文。 */
@RestController
@RequestMapping("/api/cleaning/devices/{deviceId}")
public class CleaningController {
    private final CleaningWebPresentation presentation;
    private final com.company.project.presentation.web.RealtimePushPresentation push;
    public CleaningController(CleaningWebPresentation presentation, com.company.project.presentation.web.RealtimePushPresentation push) { this.presentation = presentation; this.push=push; }
    @PostMapping("/emergency-stop")
    public ResponseEntity<Result<CleaningCommandVo>> emergencyStop(@PathVariable String deviceId,
            @RequestBody EmergencyStopDto request, Authentication auth) {
        return presentation.emergencyStop(deviceId, request, auth);
    }
    @PutMapping("/settings")
    public ResponseEntity<Result<CleaningCommandVo>> updateSettings(@PathVariable String deviceId,
            @RequestBody SettingsUpdateDto request, Authentication auth) {
        return presentation.updateSettings(deviceId, request, auth);
    }
    @GetMapping("/realtime") public Result<CleaningRealtimeVo> realtime(@PathVariable String deviceId, Authentication auth) {
        return presentation.realtime(deviceId, auth);
    }
    @GetMapping("/settings") public Result<CleaningSettingsVo> settings(@PathVariable String deviceId, Authentication auth) {
        return presentation.settings(deviceId, auth);
    }
    @GetMapping("/commands/{requestId}") public Result<CleaningCommandVo> operation(@PathVariable String deviceId,
            @PathVariable String requestId, Authentication auth) {
        return presentation.operation(deviceId, requestId, auth);
    }
    @GetMapping("/statistics") public Result<CleaningStatisticsVo> statistics(@PathVariable String deviceId,Authentication auth) {
        return presentation.statistics(deviceId,auth);
    }
    @GetMapping(value="/realtime/stream",produces=org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@PathVariable String deviceId,Authentication auth) {
        return push.subscribe(deviceId,auth);
    }
}
