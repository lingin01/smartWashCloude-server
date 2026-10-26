package com.company.project.presentation.web;

import com.company.project.domain.bo.telemetry.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.exception.PageApiException;
import com.company.project.dto.page.PageRequests.ArchiveQuery;
import com.company.project.service.CleaningLobbyService;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.security.core.Authentication;
import java.math.*;
import java.time.*;
import java.util.*;

/** 归档只组织已经保存的摘要；日期区间均为上海时间左闭右开。 */
final class ArchivePageQueries {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final CleaningLobbyService lobby;
    private final PageAccess access;
    ArchivePageQueries(CleaningLobbyService lobby,PageAccess access){this.lobby=lobby;this.access=access;}
    ArchiveResponse query(ArchiveQuery q,Authentication auth) {
        var resolved=access.resolve(q.getSystemCode(),null,"device:read",auth);
        var winch=access.winch(resolved.system(),q.getWinchCode());
        var reader=lobby.history(resolved.context());
        if(reader==null)throw new PageApiException(503,"STATISTICS_NOT_READY","正式历史统计尚未启用");
        String source="SPEED".equals(q.getMetric())?"SPEED":switch(q.getTensionMetric()) {
            case "WINCH_TENSION"->"ROPE1";case "TOTAL_TENSION"->"ROPE_TOTAL";default->"ROPE2";
        };
        String grouping=Set.of("YEAR","HALF_YEAR","QUARTER").contains(q.getPeriod())?"MONTH":"DAY";
        var change=reader.archive(winch.winchId(),source,at(q.getChangeStartDate()),at(q.getChangeEndDate()),grouping);
        List<ArchivePoint> current,reference;
        Measure currentMetric,referenceMetric;
        List<String> referenceCodes=new ArrayList<>();
        ArchiveDateRange currentRange=null,referenceRange=null;
        boolean ready=change.ready();
        boolean byRun=Set.of("PREVIOUS_RUN","HISTORY_AVERAGE").contains(q.getComparisonType());
        if(byRun) {
            Run selected=reader.run(q.getRecordCode()).orElseThrow(()->HistoryPageQueries.missing("RECORD_NOT_FOUND"));
            current=heightPoints(reader.runHeights(selected.recordCode(),winch.winchId()),source,q.getMetric());
            currentMetric=selected.measures().get("W"+winch.groupNo()+"/"+source);
            LocalDate day=selected.startedAt().atZone(ZONE).toLocalDate();
            currentRange=range(day,day.plusDays(1));
            LocalDate from=q.getReferenceStartDate()==null?day:q.getReferenceStartDate();
            LocalDate to=q.getReferenceEndDate()==null?day.plusDays(1):q.getReferenceEndDate();
            referenceRange=range(from,to);
            Map<BigDecimal,List<Measure>> buckets=new TreeMap<>();referenceMetric=null;
            boolean previous="PREVIOUS_RUN".equals(q.getComparisonType()),found=false;
            for(int offset=0;!found;offset+=500) {
                var runs=reader.runs(at(from),at(to),"COMPLETED",offset,500).entries();
                for(Run run:runs) {
                    if(!run.startedAt().isBefore(selected.startedAt()))continue;
                    referenceCodes.add(run.recordCode());
                    referenceMetric=merge(referenceMetric,run.measures().get("W"+winch.groupNo()+"/"+source));
                    for(Height h:reader.runHeights(run.recordCode(),winch.winchId()))if(source.equals(h.source()))
                        buckets.computeIfAbsent(h.targetHeight(),ignored->new ArrayList<>()).add(h.value());
                    if(previous){found=true;break;}
                }
                if(runs.size()<500)break;
            }
            reference=new ArrayList<>();
            for(var e:buckets.entrySet()) {
                BigDecimal sum=e.getValue().stream().map(Measure::average).reduce(BigDecimal.ZERO,BigDecimal::add);
                reference.add(point(e.getKey().toPlainString(),null,e.getKey(),sum.divide(BigDecimal.valueOf(e.getValue().size()),6,RoundingMode.HALF_UP),
                        q.getMetric(),(long)e.getValue().size(),(long)e.getValue().size()));
            }
        } else {
            LocalDate from=q.getCurrentStartDate(),to=q.getCurrentEndDate();
            LocalDate rf=q.getReferenceStartDate(),rt=q.getReferenceEndDate();
            if("PREVIOUS_PERIOD".equals(q.getComparisonType())) {rt=from;rf=from.minusDays(java.time.temporal.ChronoUnit.DAYS.between(from,to));}
            if("YEAR_ON_YEAR".equals(q.getComparisonType())) {rf=from.minusYears(1);rt=to.minusYears(1);}
            currentRange=range(from,to);referenceRange=range(rf,rt);
            var a=reader.archive(winch.winchId(),source,at(from),at(to),grouping);
            var b=reader.archive(winch.winchId(),source,at(rf),at(rt),grouping);
            current=timePoints(a,q.getMetric());reference=timePoints(b,q.getMetric());
            currentMetric=a.summary();referenceMetric=b.summary();ready&=a.ready()&&b.ready();
        }
        var changePoints=timePoints(change,q.getMetric());
        String identity=q.getSystemCode()+"/"+q.getWinchCode()+"/"+q.getMetric()+"/"+source+"/"+q.getPeriod()+"/"+q.getComparisonType()+"/"+q.getRecordCode()+"/"+q.getChangeStartDate()+"/"+q.getChangeEndDate()+"/"+currentRange+"/"+referenceRange+"/"+q.getMaxPointsPerSeries();
        int offset=HistoryPageQueries.cursor(q.getCursor(),identity),limit=q.getMaxPointsPerSeries();
        boolean complete=Math.max(changePoints.size(),Math.max(current.size(),reference.size()))<=offset+limit;
        String reason=currentMetric==null||currentMetric.count()==0?"CURRENT_EMPTY":referenceMetric==null||referenceMetric.count()==0?"REFERENCE_EMPTY":referenceMetric.average().signum()==0?"REFERENCE_ZERO":null;
        BigDecimal rate=reason==null?currentMetric.average().subtract(referenceMetric.average()).divide(referenceMetric.average().abs(),6,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)):null;
        var effective=new ArchiveRanges(range(q.getChangeStartDate(),q.getChangeEndDate()),currentRange,referenceRange);
        return ArchiveResponse.builder().systemCode(q.getSystemCode()).winchCode(q.getWinchCode()).metric(q.getMetric()).tensionMetric(q.getTensionMetric())
                .period(q.getPeriod()).comparisonType(q.getComparisonType()).comparisonTitle(byRun?"上行高度比较":"周期比较").comparisonAxis(byRun?"HEIGHT":"TIME")
                .historySampleCount(referenceMetric==null?0:referenceMetric.count()).changePoints(slice(changePoints,offset,limit))
                .comparisonCurrent(slice(current,offset,limit)).comparisonPrevious(slice(reference,offset,limit))
                .requestedRanges(new ArchiveRanges(range(q.getChangeStartDate(),q.getChangeEndDate()),range(q.getCurrentStartDate(),q.getCurrentEndDate()),range(q.getReferenceStartDate(),q.getReferenceEndDate())))
                .effectiveRanges(effective).referenceRecordCodes(referenceCodes).dataRange(new TimeRange(at(q.getChangeStartDate()),at(q.getChangeEndDate()),true)).asOf(Instant.now())
                .summary(new ArchiveSummary(convert(currentMetric==null?null:currentMetric.average(),q.getMetric()),convert(currentMetric==null?null:currentMetric.maximum(),q.getMetric()),
                        convert(currentMetric==null?null:currentMetric.minimum(),q.getMetric()),unit(q.getMetric()),rate,reason==null,reason,stats(currentMetric,q.getMetric()),stats(referenceMetric,q.getMetric())))
                .statisticsState(ready?"READY":"UPDATING").complete(complete).nextCursor(complete?null:HistoryPageQueries.encode(offset+limit,identity))
                .aggregation(new Aggregation("SAVED_SUM_COUNT_AND_EQUAL_RUN_HEIGHT",new BigDecimal("0.5"),RunHistoryBo.RULE,null,"概要按完整测量sum/count；高度曲线每轮等权"))
                .notices(List.of()).build();
    }
    private static List<ArchivePoint> heightPoints(List<Height> values,String source,String metric) {
        return values.stream().filter(h->source.equals(h.source())).map(h->point(h.targetHeight().toPlainString(),null,h.targetHeight(),h.value().average(),metric,h.value().count(),h.contributingRuns())).toList();
    }
    private static List<ArchivePoint> timePoints(ArchiveMetricBo values,String metric) {
        return values.points().stream().map(p->point(p.key(),p.at(),null,p.value().average(),metric,p.value().count(),null)).toList();
    }
    private static ArchivePoint point(String key,Instant time,BigDecimal height,BigDecimal value,String metric,Long count,Long runs) {
        return ArchivePoint.builder().axisKey(key).label(key).time(time).height(height).tension("SPEED".equals(metric)?null:convert(value,metric))
                .speed("SPEED".equals(metric)?value:null).sampleCount(count).contributingRuns(runs).build();
    }
    private static MetricStatistics stats(Measure value,String metric) {
        return value==null?new MetricStatistics(BigDecimal.ZERO,0L,null,null,null,unit(metric)):
                new MetricStatistics(convert(value.sum(),metric),value.count(),convert(value.average(),metric),convert(value.maximum(),metric),convert(value.minimum(),metric),unit(metric));
    }
    private static Measure merge(Measure a,Measure b) {
        if(a==null)return b;if(b==null)return a;
        return new Measure(a.sum().add(b.sum()),a.count()+b.count(),a.minimum().min(b.minimum()),a.maximum().max(b.maximum()),null,null,null,null);
    }
    private static BigDecimal convert(BigDecimal value,String metric){return value==null?null:"SPEED".equals(metric)?value:value.movePointLeft(3);}
    private static String unit(String metric){return "SPEED".equals(metric)?"m/min":"t";}
    private static Instant at(LocalDate d){return d.atStartOfDay(ZONE).toInstant();}
    private static ArchiveDateRange range(LocalDate a,LocalDate b){return a==null?null:new ArchiveDateRange(a,b,true);}
    private static <T> List<T> slice(List<T> values,int from,int count){return values.subList(Math.min(from,values.size()),Math.min(from+count,values.size()));}
}
