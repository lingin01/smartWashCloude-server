package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.presentation.web.AlarmEventPresentation;
import com.company.project.vo.page.PageResponses.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 告警及独立异常的只读入口；不在 HTTP 层判定 PLC 告警或执行恢复。 */
@RestController
@RequestMapping("/api/command-center")
public class AlarmEventController {
    private final AlarmEventPresentation presentation;

    public AlarmEventController(AlarmEventPresentation presentation) { this.presentation = presentation; }

    @GetMapping("/alarms/latest")
    public Result<LatestAlarmResponse> latest(@Valid @ModelAttribute LatestAlarmQuery query, Authentication auth) {
        return Result.success(presentation.latest(query, auth));
    }

    @GetMapping("/alarms/history")
    public Result<AlarmHistoryResponse> history(@Valid @ModelAttribute AlarmHistoryQuery query, Authentication auth) {
        return Result.success(presentation.history(query, auth));
    }

    @GetMapping("/events/history")
    public Result<EventHistoryResponse> events(@Valid @ModelAttribute EventHistoryQuery query, Authentication auth) {
        return Result.success(presentation.events(query, auth));
    }

    @GetMapping("/events/{eventCode}")
    public Result<EventDetailResponse> event(@PathVariable String eventCode,
            @Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return Result.success(presentation.event(query.getSystemCode(), eventCode, auth));
    }
}
