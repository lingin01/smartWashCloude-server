package com.company.project.presentation.web;

import com.company.project.domain.exception.PageApiException;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** 明确只读权限和设备归属，等待清洗事件事实与统计查询实现后接入。 */
@Component
public class AlarmEventPresentation {
    private final PageAccess access;
    public AlarmEventPresentation(PageAccess access) { this.access = access; }

    public LatestAlarmResponse latest(LatestAlarmQuery query, Authentication auth) {
        var resolved=verify(query.getSystemCode(),query.getDeviceCode(),auth);var reader=reader(resolved);
        Instant now=Instant.now(),from=now.minus(Duration.ofHours(24));
        Integer slot=slot(resolved,query.getDeviceCode());
        var recent=reader.events(from,now,true,slot,null,0,1001);
        var active=reader.activeEvents().stream().filter(e->e.alarm()&&(slot==null||slot==e.winchSlot())).toList();
        Map<String,com.company.project.domain.bo.telemetry.RunHistoryBo.Event> values=new LinkedHashMap<>();
        active.forEach(e->values.put(e.eventCode(),e));recent.entries().stream().limit(1000).forEach(e->values.put(e.eventCode(),e));
        return LatestAlarmResponse.builder().systemCode(query.getSystemCode()).asOf(now)
                .recent(values.values().stream().map(e->alarm(resolved.system(),e)).toList()).hasAttention(!active.isEmpty())
                .attentionScope("ACTIVE_PLC_SERIOUS").recentWindowHours(24).windowStart(from).windowEnd(now)
                .complete(recent.total()<=1000).demo(false).build();
    }

    public AlarmHistoryResponse history(AlarmHistoryQuery query, Authentication auth) {
        var resolved=verify(query.getSystemCode(),query.getDeviceCode(),auth);var reader=reader(resolved);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate start=switch(query.getRange()) {
            case "TODAY"->today;case "7D"->today.minusDays(6);case "CUSTOM"->query.getStartDate();default->today.minusDays(29);
        };
        LocalDate end="CUSTOM".equals(query.getRange())?query.getEndDate():today;
        Instant from=start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),to=end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        boolean serious=query.getLevels()!=null&&Arrays.asList(query.getLevels().split(",")).contains("SERIOUS");
        var result=serious?reader.events(from,to,true,slot(resolved,query.getDeviceCode()),null,(query.getPage()-1)*query.getPageSize(),query.getPageSize())
                :new com.company.project.domain.bo.telemetry.RunHistoryBo.Page<com.company.project.domain.bo.telemetry.RunHistoryBo.Event>(List.of(),0);
        return AlarmHistoryResponse.builder().systemCode(query.getSystemCode()).asOf(Instant.now()).historyRange(new DateRange(start,end,true))
                .effectiveRange(new TimeRange(from,to,true)).list(result.entries().stream().map(e->alarm(resolved.system(),e)).toList())
                .total(result.total()).page(query.getPage()).pageSize(query.getPageSize()).demo(false).build();
    }

    public EventHistoryResponse events(EventHistoryQuery query, Authentication auth) {
        var resolved=verify(query.getSystemCode(),query.getDeviceCode(),auth);var reader=reader(resolved);
        Boolean alarm="ALARM".equals(query.getRecordKind())?true:"NON_ALARM".equals(query.getRecordKind())?false:null;
        var result=reader.events(query.getStartTime().toInstant(),query.getEndTime().toInstant(),alarm,
                slot(resolved,query.getDeviceCode()),query.getRecordCode(),query.getDirection(),(query.getPage()-1)*query.getPageSize(),query.getPageSize());
        return EventHistoryResponse.builder().systemCode(query.getSystemCode()).asOf(Instant.now())
                .list(result.entries().stream().map(e->HistoryPageQueries.eventSummary(resolved.system(),e)).toList())
                .total(result.total()).page(query.getPage()).pageSize(query.getPageSize())
                .filters(new EventFilters(query.getStartTime().toInstant(),query.getEndTime().toInstant(),query.getRecordKind(),query.getDeviceCode(),query.getDirection(),query.getRecordCode())).build();
    }

    public EventDetailResponse event(String systemCode, String eventCode, Authentication auth) {
        var resolved=verify(systemCode,null,auth);var reader=reader(resolved);
        var e=reader.event(eventCode).orElseThrow(()->HistoryPageQueries.missing("EVENT_NOT_FOUND"));
        List<EventSnapshot> snapshots=new ArrayList<>();
        if(e.trigger()!=null)snapshots.add(new EventSnapshot("TRIGGER",null,null,Long.toString(e.trigger().sequence()),e.firstObservedAt(),null,
                HistoryPageQueries.readings(resolved.system(),e.trigger(),null),"OBSERVED","首次观测到故障"));
        if(e.end()!=null)snapshots.add(new EventSnapshot("ENDED",null,null,Long.toString(e.end().sequence()),e.endedAt(),null,
                HistoryPageQueries.readings(resolved.system(),e.end(),null),"OBSERVED","首次观测到故障结束"));
        return new EventDetailResponse(systemCode,HistoryPageQueries.eventSummary(resolved.system(),e),e.alarm()?alarm(resolved.system(),e):null,List.copyOf(snapshots),Instant.now());
    }

    private PageAccess.Resolved verify(String systemCode, String deviceCode, Authentication auth) {
        var resolved = access.resolve(systemCode, null, "alarm:read", auth);
        if (deviceCode != null) access.winch(resolved.system(), deviceCode);
        return resolved;
    }
    private com.company.project.domain.bo.telemetry.HistoryReader reader(PageAccess.Resolved resolved) {
        try {var reader=access.history(resolved);if(reader==null)throw notReady();return reader;}
        catch(PageApiException unavailable){if(unavailable.status()==503)throw notReady();throw unavailable;}
    }
    private Integer slot(PageAccess.Resolved r,String device) {
        return device==null?null:access.winch(r.system(),device).groupNo();
    }
    private AlarmItem alarm(com.company.project.domain.bo.page.PageSystemBo system,
                            com.company.project.domain.bo.telemetry.RunHistoryBo.Event e) {
        var winch=system.winches().stream().filter(w->w.groupNo()==e.winchSlot()).findFirst().orElse(null);
        return AlarmItem.builder().alarmCode(e.eventCode()).eventCode(e.eventCode()).systemCode(system.systemCode())
                .deviceCode(winch==null?system.systemCode():winch.winchCode()).deviceName(winch==null?system.systemName():winch.winchName())
                .source(e.sourceKind()).type(e.part()).content(e.content()).runCode(e.recordCode()).level("SERIOUS").levelConfirmed(true)
                .occurredAt(e.firstObservedAt()).timeBasis("FIRST_OBSERVED").firstObservedAt(e.firstObservedAt())
                .updatedAt(e.endedAt()==null?e.firstObservedAt():e.endedAt()).revision(e.revision())
                .eventState(e.endedAt()==null?"ACTIVE":"ENDED").endedAt(e.endedAt()).continuity("OBSERVED").unit("t")
                .faultCode(e.faultCode()).faultDetails(e.faultDetails()).direction(e.direction())
                .message("请根据故障部位检修对应动力设备").build();
    }

    private PageApiException notReady() {
        return new PageApiException(503, "EVENTS_NOT_READY", "PLC 告警和异常事件查询尚未接通，不能确认无告警");
    }
}
