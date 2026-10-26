package com.company.project.presentation.web;

import com.company.project.domain.exception.PageApiException;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 明确只读权限和设备归属，等待清洗事件事实与统计查询实现后接入。 */
@Component
public class AlarmEventPresentation {
    private final PageAccess access;
    public AlarmEventPresentation(PageAccess access) { this.access = access; }

    public LatestAlarmResponse latest(LatestAlarmQuery query, Authentication auth) {
        verify(query.getSystemCode(), query.getDeviceCode(), auth);
        throw notReady();
    }

    public AlarmHistoryResponse history(AlarmHistoryQuery query, Authentication auth) {
        verify(query.getSystemCode(), query.getDeviceCode(), auth);
        throw notReady();
    }

    public EventHistoryResponse events(EventHistoryQuery query, Authentication auth) {
        verify(query.getSystemCode(), query.getDeviceCode(), auth);
        throw notReady();
    }

    public EventDetailResponse event(String systemCode, String eventCode, Authentication auth) {
        verify(systemCode, null, auth);
        throw notReady();
    }

    private void verify(String systemCode, String deviceCode, Authentication auth) {
        var resolved = access.resolve(systemCode, null, "alarm:read", auth);
        if (deviceCode != null) access.winch(resolved.system(), deviceCode);
    }

    private PageApiException notReady() {
        return new PageApiException(503, "EVENTS_NOT_READY", "PLC 告警和异常事件查询尚未接通，不能确认无告警");
    }
}
