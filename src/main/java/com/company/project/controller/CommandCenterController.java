package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.presentation.web.CommandCenterPresentation;
import com.company.project.presentation.web.HomeStreamPresentation;
import com.company.project.vo.page.PageResponses.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 页面入口只负责 HTTP 绑定；测量、状态与操作规则不放进 Controller。 */
@RestController
@RequestMapping("/api/command-center")
public class CommandCenterController {
    private final CommandCenterPresentation presentation;
    private final HomeStreamPresentation stream;

    public CommandCenterController(CommandCenterPresentation presentation, HomeStreamPresentation stream) {
        this.presentation = presentation;
        this.stream = stream;
    }

    @GetMapping("/home")
    public Result<HomeSnapshot> home(@Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return Result.success(presentation.homeSnapshot(query.getSystemCode(), auth));
    }

    @GetMapping(value = "/home/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter homeStream(@Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return stream.subscribe(query.getSystemCode(), auth);
    }

    @PostMapping("/emergency-stop")
    public ResponseEntity<Result<CommandReceipt>> emergencyStop(
            @Valid @RequestBody EmergencyStopRequest request, Authentication auth) {
        return presentation.emergencyStop(request, auth);
    }

    @GetMapping("/commands/{commandCode}")
    public Result<CommandReceipt> command(@PathVariable String commandCode,
            @Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return Result.success(presentation.command(query.getSystemCode(), commandCode, auth));
    }

    @GetMapping("/winch/detail")
    public Result<WinchDetailResponse> winchDetail(@Valid @ModelAttribute WinchQuery query, Authentication auth) {
        return Result.success(presentation.winchDetail(query, auth));
    }

    @GetMapping("/winch/tension/trend")
    public Result<WinchTrendResponse> winchTrend(@Valid @ModelAttribute WinchTrendQuery query, Authentication auth) {
        return Result.success(presentation.winchTrend(query, auth));
    }

    @PutMapping("/winch/tension-thresholds")
    public Result<ThresholdApplyResponse> updateThresholds(
            @Valid @RequestBody ThresholdUpdateRequest request, Authentication auth) {
        return Result.success(presentation.updateThresholds(request, auth));
    }

    @GetMapping("/archive")
    public Result<ArchiveResponse> archive(@Valid @ModelAttribute ArchiveQuery query, Authentication auth) {
        return Result.success(presentation.archive(query, auth));
    }

    @GetMapping("/history/list")
    public Result<RunListResponse> historyList(@Valid @ModelAttribute RunListQuery query, Authentication auth) {
        return Result.success(presentation.historyList(query, auth));
    }

    @GetMapping("/history/detail")
    public Result<RunDetailResponse> historyDetail(@Valid @ModelAttribute RunDetailQuery query, Authentication auth) {
        return Result.success(presentation.historyDetail(query, auth));
    }

    @GetMapping("/history/point")
    public Result<SamplePointResponse> historyPoint(@Valid @ModelAttribute SamplePointQuery query, Authentication auth) {
        return Result.success(presentation.historyPoint(query, auth));
    }

    @GetMapping("/parameter-settings")
    public Result<ParameterSettingsResponse> parameterSettings(@Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return Result.success(presentation.parameterSettings(query.getSystemCode(), auth));
    }

    @PutMapping("/parameter-settings")
    public Result<ParameterSettingsResponse> updateParameters(
            @Valid @RequestBody ParameterUpdateRequest request, Authentication auth) {
        return Result.success(presentation.updateParameters(request, auth));
    }
}
