package com.company.project.presentation.web;

import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.domain.bo.telemetry.HistoryReader;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.exception.PageApiException;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.service.CleaningLobbyService;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** 历史HTTP查询组织与单位/身份转换；生命周期和统计由业务模块提供。 */
final class HistoryPageQueries {
    private final CleaningLobbyService lobby;
    private final PageAccess access;
    HistoryPageQueries(CleaningLobbyService lobby,PageAccess access){this.lobby=lobby;this.access=access;}
    private HistoryReader reader(PageAccess.Resolved resolved) {
        HistoryReader value=lobby.history(resolved.context());
        if(value==null)throw new PageApiException(503,"HISTORY_NOT_READY","正式历史尚未启用");
        return value;
    }
    RunListResponse list(RunListQuery q,Authentication auth) {
        var resolved=access.resolve(q.getSystemCode(),null,"device:read",auth);
        if(q.getWinchCode()!=null)access.winch(resolved.system(),q.getWinchCode());
        var reader=reader(resolved);
        var page=reader.runs(q.getStartTime().toInstant(),q.getEndTime().toInstant(),q.getRunResult(),
                Math.multiplyExact(q.getPage()-1,q.getPageSize()),q.getPageSize());
        List<LocalDate> dates=List.of();
        if(q.isIncludeAvailableDates())dates=reader.dates(cursor(q.getAvailableDatesCursor(),q.getSystemCode()+"/dates"),501);
        boolean datesComplete=dates.size()<=500;
        int dateOffset=cursor(q.getAvailableDatesCursor(),q.getSystemCode()+"/dates");
        return RunListResponse.builder().systemCode(q.getSystemCode()).entries(page.entries().stream().map(this::entry).toList())
                .total(page.total()).page(q.getPage()).pageSize(q.getPageSize())
                .filters(new RunFilters("RUN",q.getStartTime().toInstant(),q.getEndTime().toInstant(),true,"STARTED",q.getRunResult(),q.getWinchCode()))
                .availableDates(dates.stream().limit(500).toList()).availableDatesComplete(datesComplete)
                .availableDatesNextCursor(datesComplete?null:encode(dateOffset+500,q.getSystemCode()+"/dates"))
                .datesBasis("STARTED").asOf(Instant.now()).build();
    }
    RunDetailResponse detail(RunDetailQuery q,Authentication auth) {
        var resolved=access.resolve(q.getSystemCode(),null,"device:read",auth);var reader=reader(resolved);
        if(q.getWinchCode()!=null)access.winch(resolved.system(),q.getWinchCode());
        Run run=reader.run(q.getRecordCode()).orElseThrow(()->missing("RECORD_NOT_FOUND"));
        String identity=q.getSystemCode()+"/"+q.getRecordCode()+"/"+q.getWinchCode()+"/"+q.getResolution()+"/"+q.getFromAt()+"/"+q.getToAt()+"/"+q.getHeightMin()+"/"+q.getHeightMax()+"/"+q.getMaxPointsPerSeries();
        int offset=cursor(q.getCursor(),identity),limit=q.getMaxPointsPerSeries();
        List<SavedFrame> loaded=reader.frames(q.getRecordCode(),offset,limit+1);
        boolean complete=loaded.size()<=limit;
        List<SavedFrame> frames=loaded.stream().limit(limit).filter(f->q.getFromAt()==null||!f.sampledAt().isBefore(q.getFromAt().toInstant()))
                .filter(f->q.getToAt()==null||f.sampledAt().isBefore(q.getToAt().toInstant())).toList();
        List<RunSeries> sensors=new ArrayList<>(),motors=new ArrayList<>(),totals=new ArrayList<>(),speeds=new ArrayList<>();
        for(var winch:resolved.system().winches()) {
            if(q.getWinchCode()!=null&&!q.getWinchCode().equals(winch.winchCode()))continue;
            for(String source:List.of("ROPE1","ROPE2","SERVO","ROPE_TOTAL","SPEED")) {
                var points=points(frames,winch,source,q.getHeightMin(),q.getHeightMax(),"RAW".equals(q.getResolution()));
                String sensor=source.equals("ROPE1")||source.equals("ROPE2")?winch.sensors().stream()
                        .filter(s->s.ropeNo()==(source.equals("ROPE1")?1:2)).map(PageSystemBo.Sensor::sensorCode).findFirst().orElse(null):null;
                var series=RunSeries.builder().id(winch.winchCode()+"/"+source).name(name(source)).winchCode(winch.winchCode())
                        .groupNo(winch.groupNo()).sensorCode(sensor).sourceRole(source).unit(source.equals("SPEED")?"m/min":"t")
                        .empty(points.isEmpty()).points(points).segments(segments(points)).build();
                if(source.startsWith("ROPE")&&!source.equals("ROPE_TOTAL"))sensors.add(series);
                else if(source.equals("SERVO"))motors.add(series);else if(source.equals("SPEED"))speeds.add(series);else totals.add(series);
            }
        }
        int eventOffset=cursor(q.getEventCursor(),q.getSystemCode()+"/"+q.getRecordCode()+"/events");
        var eventPage=reader.events(Instant.EPOCH,Instant.now().plusSeconds(1),null,null,q.getRecordCode(),eventOffset,101);
        List<EventSummary> events=eventPage.entries().stream().limit(100).map(e->eventSummary(resolved.system(),e)).toList();
        List<ChartPoint> systemPoints=reader.runHeights(q.getRecordCode(),null,q.getFromAt()==null?null:q.getFromAt().toInstant(),q.getToAt()==null?null:q.getToAt().toInstant()).stream()
                .filter(h->h.source().equals("ROPE_TOTAL"))
                .filter(h->q.getHeightMin()==null||h.targetHeight().compareTo(q.getHeightMin())>=0)
                .filter(h->q.getHeightMax()==null||h.targetHeight().compareTo(q.getHeightMax())<=0)
                .skip(offset).limit(limit).map(h->heightPoint(h,false)).toList();
        var systemSeries=RunSeries.builder().id("SYSTEM/ROPE_TOTAL").name("八传感器高度对齐总拉力").sourceRole("SYSTEM_ROPE_TOTAL")
                .unit("t").empty(systemPoints.isEmpty()).points(systemPoints).segments(segments(systemPoints)).build();
        return RunDetailResponse.builder().systemCode(q.getSystemCode()).entry(entry(run)).sensors(sensors).servos(motors)
                .winchTotals(totals).speeds(speeds).systemTotals(List.of(systemSeries)).heightMin(bound(run,"heightMin")).heightMax(bound(run,"heightMax"))
                .heightLimits(new HeightLimits(bound(run,"heightMin"),bound(run,"heightMax"),"CONFIGURATION",null))
                .asOf(Instant.now()).dataRange(new TimeRange(run.startedAt(),run.observedThrough(),true))
                .resolution(q.getResolution()).complete(complete).nextCursor(complete?null:encode(offset+limit,identity))
                .loadedPointCount(frames.size()).aggregation(new Aggregation("SAVED_HEIGHT_NODES",new BigDecimal("0.5"),
                        com.company.project.domain.bo.telemetry.RunHistoryBo.RULE,Long.toString(run.lastSequence()),"真实保存高度点；actualHeight保留实际高度"))
                .exceptionEvents(events).eventTotal(eventPage.total()).eventsComplete(eventOffset+events.size()>=eventPage.total())
                .eventNextCursor(eventOffset+events.size()>=eventPage.total()?null:encode(eventOffset+events.size(),q.getSystemCode()+"/"+q.getRecordCode()+"/events"))
                .notices(List.of()).build();
    }
    SamplePointResponse point(SamplePointQuery q,Authentication auth) {
        var resolved=access.resolve(q.getSystemCode(),null,"device:read",auth);
        if(q.getWinchCode()!=null)access.winch(resolved.system(),q.getWinchCode());
        var frame=reader(resolved).frame(q.getRecordCode(),q.getSampleKey()).orElseThrow(()->missing("SAMPLE_NOT_FOUND"));
        return SamplePointResponse.builder().systemCode(q.getSystemCode()).recordCode(q.getRecordCode()).sampleKey(frame.sampleKey())
                .sampleFrameId(frame.sampleKey()).time(frame.sampledAt()).winchReadings(readings(resolved.system(),frame.sample(),q.getWinchCode())).build();
    }
    private List<ChartPoint> points(List<SavedFrame> frames,PageSystemBo.Winch winch,String source,BigDecimal min,BigDecimal max,boolean raw) {
        List<ChartPoint> output=new ArrayList<>();
        for(var frame:frames) {
            var selection=frame.selections().get(winch.groupNo());
            if(selection==null||!raw&&(!selection.heightNode()||selection.targetHeight()==null))continue;
            var reading=frame.sample().winches().stream().filter(w->winch.winchId().equals(w.winchId())).findFirst().orElse(null);
            if(reading==null)continue;
            BigDecimal axis=selection.heightNode()?selection.targetHeight():reading.heightMeters();
            if(min!=null&&(axis==null||axis.compareTo(min)<0)||max!=null&&(axis==null||axis.compareTo(max)>0))continue;
            BigDecimal value=value(frame.sample(),winch,source);
            output.add(ChartPoint.builder().sampleKey(frame.sampleKey()).sampleFrameId(frame.sampleKey()).sequence(Long.toString(frame.sequence()))
                    .time(frame.sampledAt()).height(axis).actualHeight(reading.heightMeters())
                    .value(source.equals("SPEED")?value:tonnes(value)).speed(reading.speed()).direction(reading.direction())
                    .motionRole("PRODUCTION_UP").sampleCount(value==null?0L:1L).speedSampleCount(reading.speed()==null?0L:1L)
                    .heightRuleVersion(com.company.project.domain.bo.telemetry.RunHistoryBo.RULE).segmentCode(selection.segmentCode())
                    .computedVersion(Long.toString(frame.sequence())).build());
        }
        return List.copyOf(output);
    }
    static BigDecimal value(com.company.project.domain.bo.TelemetrySample frame,PageSystemBo.Winch winch,String source) {
        var w=frame.winches().stream().filter(v->v.winchId().equals(winch.winchId())).findFirst().orElse(null);
        if(w==null)return null;
        if("SERVO".equals(source))return w.torqueKg();if("SPEED".equals(source))return w.speed();if("ROPE_TOTAL".equals(source))return w.totalTension();
        int rope="ROPE1".equals(source)?1:2;
        var sensor=winch.sensors().stream().filter(s->s.ropeNo()==rope).findFirst().orElse(null);
        return sensor==null?null:frame.ropes().stream().filter(s->sensor.sensorId().equals(s.sensorId())).map(com.company.project.domain.bo.TelemetrySample.RopeSample::tension).filter(Objects::nonNull).findFirst().orElse(null);
    }
    static List<ChartSegment> segments(List<ChartPoint> points) {
        List<ChartSegment> segments=new ArrayList<>();List<ChartPoint> current=new ArrayList<>();String key=null;
        for(var p:points) {
            String segment=p.segmentCode()==null?"HEIGHT_PROFILE":p.segmentCode();
            if(key!=null&&!Objects.equals(key,segment)) { segments.add(new ChartSegment(key,"PRODUCTION_UP",!segments.isEmpty(),List.copyOf(current)));current.clear(); }
            key=segment;current.add(p);
        }
        if(!current.isEmpty())segments.add(new ChartSegment(key,"PRODUCTION_UP",!segments.isEmpty(),List.copyOf(current)));
        return List.copyOf(segments);
    }
    RunEntry entry(Run run) {
        String text=run.result()==null?"进行中":switch(run.result()) {
            case "NORMAL_COMPLETED"->"正常完成";case "ABNORMAL_COMPLETED"->"异常完成";default->"异常结束";
        };
        Measure total=run.measures().get("SYSTEM/ROPE_TOTAL");
        long duration=Math.max(0,Duration.between(run.startedAt(),run.endedAt()==null?run.observedThrough():run.endedAt()).getSeconds());
        return RunEntry.builder().id(run.recordCode()).kind("RUN").name("自动运行").startDate(run.startedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
                .endDate(run.endedAt()==null?null:run.endedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()).startedAt(run.startedAt()).endedAt(run.endedAt())
                .firstObservedAt(run.startedAt()).lastObservedAt(run.observedThrough()).state(run.state()).runResult(run.result())
                .completionKind(run.result()).runResultText(text).statusText(text).endReason(run.endReason()).startMethod("AUTOMATIC").startMethodText("PLC自动运行")
                .durationSeconds(duration).durationText(duration+"秒").maxTension(total==null?null:tonnes(total.maximum()))
                .minTension(total==null?null:tonnes(total.minimum())).unit("t").extremaScope("SYSTEM_SIMULTANEOUS_UP_WINDOW")
                .abnormalCount(run.eventCodes().size()).runCount(1).completedCount(run.endedAt()!=null&&!"ABNORMAL_ENDED".equals(run.result())?1:0)
                .normalCompletedCount("NORMAL_COMPLETED".equals(run.result())?1:0).recoveredCount("ABNORMAL_COMPLETED".equals(run.result())?1:0)
                .faultTerminatedCount("ABNORMAL_ENDED".equals(run.result())?1:0).incompleteCount(run.endedAt()==null?1:0)
                .exceptionEvents(List.of()).exceptionEventsComplete(false).eventTotal((long)run.eventCodes().size())
                .boundaryConfirmed(true).hasDataGap(Set.of("PLC_DISCONNECTED","BACKEND_RESTART").contains(run.endReason()==null?"":run.endReason())).dataCoverage(List.of()).build();
    }
    WinchTrendResponse trend(WinchTrendQuery q,Authentication auth) {
        var resolved=access.resolve(q.getSystemCode(),null,"device:read",auth);
        var winch=access.winch(resolved.system(),q.getWinchCode());var reader=reader(resolved);
        String identity=q.getSystemCode()+"/"+q.getWinchCode()+"/"+q.getComparisonView()+"/"+q.getPeriodStartDate()+"/"+q.getResolution()+"/"+q.getMaxPointsPerSeries()+"/"+q.getUnitPageSize();
        int offset=cursor(q.getCursor(),identity);
        List<ChartUnit> units=new ArrayList<>();List<TrendSeries> series=new ArrayList<>();
        boolean complete=true;
        if("COUNT".equals(q.getComparisonView())) {
            var records=reader.runs(Instant.EPOCH,Instant.now().plusSeconds(1),null,offset,q.getUnitPageSize()+1);
            complete=records.entries().size()<=q.getUnitPageSize();
            for(Run run:records.entries().stream().limit(q.getUnitPageSize()).toList()) {
                units.add(ChartUnit.builder().id(run.recordCode()).name(entry(run).runResultText()).recordCode(run.recordCode())
                        .startAt(run.startedAt()).endAt(run.endedAt()).dataThrough(run.observedThrough()).runState(run.state()).recordState(run.state())
                        .runResult(run.result()).runResultText(entry(run).runResultText()).abnormal(run.hadAbnormal()||run.hadAbnormalEnd()).includedRunCount(1L).build());
                addHeightSeries(series,reader.runHeights(run.recordCode(),winch.winchId()),winch,run.recordCode(),q.getMaxPointsPerSeries());
                addSystemHeightSeries(series,reader.runHeights(run.recordCode(),null),run.recordCode(),q.getMaxPointsPerSeries());
            }
        } else {
            String type=q.getComparisonView();
            LocalDate selected=q.getPeriodStartDate()==null?LocalDate.now(ZoneId.of("Asia/Shanghai")):q.getPeriodStartDate();
            LocalDate start=periodStart(selected,type),end=periodEnd(start,type);
            Instant from=start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),to=end.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            var statistics=reader.periods(type,from,to,null);
            var p=statistics.stream().findFirst().orElse(null);
            String id=type+"/"+start;
            units.add(ChartUnit.builder().id(id).name(start.toString()).periodType(type).startAt(from).endAt(to).endExclusive(true)
                    .empty(p==null).emptyReason(p==null?"STATISTICS_NOT_BUILT":null).includedRunCount(p==null?null:p.started())
                    .counts(p==null?null:new PeriodCounts(p.started(),p.normalCompleted(),p.abnormalCompleted(),p.abnormalEnded(),
                            p.normalCompleted()+p.abnormalCompleted(),p.normalCompleted()+p.abnormalCompleted()+p.abnormalEnded(),p.running())).build());
            addHeightSeries(series,reader.heights(type,from,winch.winchId()),winch,id,q.getMaxPointsPerSeries());
            addSystemHeightSeries(series,reader.heights(type,from,null),id,q.getMaxPointsPerSeries());
        }
        return WinchTrendResponse.builder().systemCode(q.getSystemCode()).winchCode(q.getWinchCode()).kind("FORCE_COMPARISON")
                .comparisonView(q.getComparisonView()).units(List.copyOf(units)).series(List.copyOf(series)).asOf(Instant.now())
                .resolution(q.getResolution()).complete(complete).nextCursor(complete?null:encode(offset+q.getUnitPageSize(),identity))
                .loadedUnits(units.size()).loadedPointCount(series.stream().mapToInt(s->s.points().size()).sum())
                .aggregation(new Aggregation("EQUAL_RUN_WEIGHT_BY_HEIGHT",new BigDecimal("0.5"),com.company.project.domain.bo.telemetry.RunHistoryBo.RULE,null,
                        "各轮同高度先合并；系统按同轮同高度四机合计")).notices(List.of()).build();
    }
    private void addHeightSeries(List<TrendSeries> output,List<Height> heights,PageSystemBo.Winch winch,String unit,int budget) {
        for(String source:List.of("ROPE1","ROPE2","SERVO","ROPE_TOTAL","SPEED")) {
            var values=heights.stream().filter(h->source.equals(h.source())).map(h->heightPoint(h,source.equals("SPEED"))).toList();
            if(values.size()>budget)throw new PageApiException(400,"POINT_BUDGET_TOO_SMALL","所选高度节点超过点数预算，请增大maxPointsPerSeries");
            output.add(TrendSeries.builder().id(unit+"/"+winch.winchCode()+"/"+source).unitId(unit).winchCode(winch.winchCode())
                    .sourceRole(source).sourceCode(winch.winchCode()+"/"+source).name(name(source)).metric(source.equals("SPEED")?"SPEED":"TENSION")
                    .unit(source.equals("SPEED")?"m/min":"t").empty(values.isEmpty()).points(values).segments(segments(values)).build());
        }
    }
    private void addSystemHeightSeries(List<TrendSeries> output,List<Height> heights,String unit,int budget) {
        var values=heights.stream().filter(h->"ROPE_TOTAL".equals(h.source())).map(h->heightPoint(h,false)).toList();
        if(values.size()>budget)throw new PageApiException(400,"POINT_BUDGET_TOO_SMALL","系统高度节点超过点数预算");
        output.add(TrendSeries.builder().id(unit+"/SYSTEM/ROPE_TOTAL").unitId(unit).sourceRole("SYSTEM_ROPE_TOTAL").name("八传感器高度对齐总拉力")
                .metric("TENSION").unit("t").empty(values.isEmpty()).points(values).segments(segments(values)).build());
    }
    static ChartPoint heightPoint(Height h,boolean speed) {
        return ChartPoint.builder().height(h.targetHeight()).value(speed?h.value().average():tonnes(h.value().average()))
                .minimum(speed?h.value().minimum():tonnes(h.value().minimum())).maximum(speed?h.value().maximum():tonnes(h.value().maximum()))
                .speed(h.speed()==null?null:h.speed().average()).sampleCount(h.value().count()).contributingRuns(h.contributingRuns())
                .heightRuleVersion(com.company.project.domain.bo.telemetry.RunHistoryBo.RULE).build();
    }
    static LocalDate periodStart(LocalDate d,String type) {
        return switch(type){case "DAY"->d;case "MONTH"->d.withDayOfMonth(1);case "QUARTER"->LocalDate.of(d.getYear(),((d.getMonthValue()-1)/3)*3+1,1);
            case "HALF_YEAR"->LocalDate.of(d.getYear(),d.getMonthValue()<=6?1:7,1);case "YEAR"->LocalDate.of(d.getYear(),1,1);default->throw new PageApiException(400,"INVALID_PERIOD","周期无效");};
    }
    static LocalDate periodEnd(LocalDate d,String type) {
        return switch(type){case "DAY"->d.plusDays(1);case "MONTH"->d.plusMonths(1);case "QUARTER"->d.plusMonths(3);case "HALF_YEAR"->d.plusMonths(6);case "YEAR"->d.plusYears(1);default->throw new PageApiException(400,"INVALID_PERIOD","周期无效");};
    }
    static List<WinchReadings> readings(PageSystemBo system,com.company.project.domain.bo.TelemetrySample sample,String winchCode) {
        List<WinchReadings> output=new ArrayList<>();
        for(var w:system.winches()) {
            if(winchCode!=null&&!winchCode.equals(w.winchCode()))continue;
            var reading=sample.winches().stream().filter(v->v.winchId().equals(w.winchId())).findFirst().orElse(null);
            if(reading==null)continue;
            List<SourceReading> sources=new ArrayList<>();
            for(String source:List.of("ROPE1","ROPE2","SERVO","ROPE_TOTAL")) {
                int rope="ROPE1".equals(source)?1:"ROPE2".equals(source)?2:0;
                String code=w.sensors().stream().filter(s->s.ropeNo()==rope).map(PageSystemBo.Sensor::sensorCode).findFirst().orElse(null);
                sources.add(new SourceReading(source,code,tonnes(value(sample,w,source)),"t",null,null));
            }
            output.add(new WinchReadings(w.winchCode(),reading.heightMeters(),reading.speed(),reading.direction(),List.copyOf(sources)));
        }
        return List.copyOf(output);
    }
    static EventSummary eventSummary(PageSystemBo system,Event event) {
        var winch=system.winches().stream().filter(w->w.groupNo()==event.winchSlot()).findFirst().orElse(null);
        return EventSummary.builder().eventCode(event.eventCode()).alarmCode(event.alarm()?event.eventCode():null).isAlarm(event.alarm()).recordCode(event.recordCode())
                .objectType(winch==null?"SYSTEM":"WINCH").objectCode(winch==null?system.systemCode():winch.winchCode())
                .objectName(winch==null?system.systemName():winch.winchName()).occurredAt(event.firstObservedAt()).firstObservedAt(event.firstObservedAt())
                .timeBasis("FIRST_OBSERVED").outcome(event.endedAt()==null?"ACTIVE":"ENDED")
                .direction(event.direction()).reason(event.content()).hasSnapshots(event.trigger()!=null).build();
    }
    static BigDecimal tonnes(BigDecimal value){return value==null?null:value.movePointLeft(3);}
    static BigDecimal bound(Run run,String name){Object value=run.configuration().get(name);return value==null?null:new BigDecimal(value.toString());}
    static String name(String source){return switch(source){case "ROPE1"->"传感器1拉力";case "ROPE2"->"传感器2拉力";case "ROPE_TOTAL"->"传感器总拉力";case "SERVO"->"电机等效拉力";case "SPEED"->"运行速度";default->source;};}
    static PageApiException missing(String code){return new PageApiException(404,code,"该系统中不存在此记录");}
    static int cursor(String cursor,String identity) {
        if(cursor==null||cursor.isBlank())return 0;
        try {
            String[] values=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split(":");
            int offset=Integer.parseInt(values[1]);
            if(values.length!=2||!values[0].equals(fingerprint(identity))||offset<0||offset>10000000)throw new IllegalArgumentException();
            return offset;
        }catch(Exception invalid){throw new PageApiException(400,"INVALID_CURSOR","游标无效或查询条件已经改变");}
    }
    static String encode(int offset,String identity) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((fingerprint(identity)+":"+offset).getBytes(StandardCharsets.UTF_8));
    }
    private static String fingerprint(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
}
