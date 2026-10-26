package com.company.project.service.statistics;

import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Period;
import com.company.project.repository.cleaning.RuntimeHistoryDao;
import java.math.*;
import java.time.Instant;
import java.util.*;

/** 日周期从本轮/留点构建，较长周期仅合并不重叠日报；重试按版本替换。 */
final class PeriodStatistics {
    private final RuntimeHistoryDao dao;
    PeriodStatistics(RuntimeHistoryDao dao) { this.dao=dao; }
    synchronized void cycle() {
        for(var work:dao.pendingPeriods(20)) {
            List<Period> periods;
            List<Height> heights;
            if("DAY".equals(work.periodType())) {
                var runs=dao.periodRuns(work.systemId(),work.start(),work.end());
                periods=dayPeriods(work,runs,dao.measuredRuns(work.systemId(),work.start(),work.end()));
                try(var frames=dao.periodFrames(work.systemId(),work.start(),work.end())) {
                    heights=dayHeights(work,frames::iterator);
                }
            } else {
                periods=mergePeriods(work,dao.dailyPeriods(work.systemId(),work.start(),work.end()));
                heights=mergeHeights(work,dao.dailyHeights(work.systemId(),work.start(),work.end()));
            }
            dao.storePeriod(work,periods,heights);
        }
    }
    private List<Period> dayPeriods(RuntimeHistoryDao.PeriodWork w,List<Run> runs,List<Run> measured) {
        Map<String,String> scopes=new LinkedHashMap<>();scopes.put(w.systemId(),"SYSTEM");
        dao.winches(w.systemId()).forEach((id,slot)->scopes.put(id,"W"+slot));
        String date=w.start().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        List<Period> output=new ArrayList<>();
        for(var scope:scopes.entrySet()) {
            Map<String,Measure> metrics=new TreeMap<>();
            String prefix=date+"/"+scope.getValue()+"/";
            for(Run run:measured)run.measures().forEach((key,value)->{
                if(key.startsWith(prefix))metrics.merge(key.substring(prefix.length()),value,PeriodStatistics::merge);
            });
            output.add(new Period(w.systemId(),scope.getKey(),w.periodType(),w.start(),w.end(),runs.size(),
                    count(runs,"NORMAL_COMPLETED"),count(runs,"ABNORMAL_COMPLETED"),count(runs,"ABNORMAL_ENDED"),
                    runs.stream().filter(r->r.endedAt()==null).count(),Map.copyOf(metrics),w.version(),Instant.now(),
                    measured.stream().mapToLong(r->r.motionMillis().getOrDefault(date+"/"+scope.getValue(),0L)).sum()));
        }
        return output;
    }
    private static long count(List<Run> runs,String result) { return runs.stream().filter(r->result.equals(r.result())).count(); }
    private record Node(String run,String scope,String source,BigDecimal height) { }
    private record TotalKey(String run,BigDecimal height) { }
    List<Height> dayHeights(RuntimeHistoryDao.PeriodWork work,Iterable<SavedFrame> frames) {
        Map<Node,Measure> inside=new LinkedHashMap<>();
        Map<Node,Measure> speeds=new LinkedHashMap<>();
        for(SavedFrame frame:frames)for(var entry:frame.selections().entrySet()) {
            var selection=entry.getValue();if(!selection.heightNode()||selection.targetHeight()==null)continue;
            int i=entry.getKey()-1;var winch=frame.sample().winches().get(i);
            var values=new LinkedHashMap<String,BigDecimal>();
            values.put("ROPE1",frame.sample().ropes().get(i*2).tension());
            values.put("ROPE2",frame.sample().ropes().get(i*2+1).tension());
            values.put("ROPE_TOTAL",winch.totalTension());values.put("SERVO",winch.torqueKg());values.put("SPEED",winch.speed());
            for(var value:values.entrySet())if(value.getValue()!=null) {
                Node node=new Node(frame.recordCode(),winch.winchId(),value.getKey(),selection.targetHeight().stripTrailingZeros());
                inside.merge(node,single(value.getValue(),frame.sampledAt(),winch.heightMeters()),PeriodStatistics::merge);
                if(winch.speed()!=null)speeds.merge(node,single(winch.speed(),frame.sampledAt(),winch.heightMeters()),PeriodStatistics::merge);
            }
        }
        Map<TotalKey,Map<String,BigDecimal>> totals=new HashMap<>();
        inside.forEach((node,value)->{
            if("ROPE_TOTAL".equals(node.source()))totals.computeIfAbsent(new TotalKey(node.run(),node.height()),k->new HashMap<>())
                    .put(node.scope(),value.average());
        });
        totals.forEach((key,values)->{
            if(values.size()==4)inside.put(new Node(key.run(),work.systemId(),"ROPE_TOTAL",key.height()),
                    single(values.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add),null,key.height()));
        });
        Map<String,Height> groups=new LinkedHashMap<>();
        inside.forEach((node,value)->{
            String key=node.scope()+"/"+node.source()+"/"+node.height().toPlainString();
            Measure representative=new Measure(value.average(),1,value.minimum(),value.maximum(),value.minimumAt(),value.maximumAt(),value.minimumHeight(),value.maximumHeight());
            Measure speed=speeds.get(node);
            if(speed!=null)speed=single(speed.average(),null,node.height());
            Height current=new Height(work.systemId(),node.scope(),node.source(),work.periodType(),work.start(),node.height(),representative,speed,1);
            groups.merge(key,current,PeriodStatistics::mergeHeight);
        });
        return List.copyOf(groups.values());
    }
    private List<Period> mergePeriods(RuntimeHistoryDao.PeriodWork work,List<Period> days) {
        Map<String,Period> scopes=new LinkedHashMap<>();
        for(Period day:days) {
            Period old=scopes.get(day.scopeId());
            Map<String,Measure> metrics=new TreeMap<>();
            if(old!=null)metrics.putAll(old.measures());
            day.measures().forEach((key,value)->metrics.merge(key,value,PeriodStatistics::merge));
            scopes.put(day.scopeId(),new Period(work.systemId(),day.scopeId(),work.periodType(),work.start(),work.end(),
                    day.started()+(old==null?0:old.started()),day.normalCompleted()+(old==null?0:old.normalCompleted()),
                    day.abnormalCompleted()+(old==null?0:old.abnormalCompleted()),day.abnormalEnded()+(old==null?0:old.abnormalEnded()),
                    day.running()+(old==null?0:old.running()),Map.copyOf(metrics),work.version(),Instant.now(),
                    day.motionMillis()+(old==null?0:old.motionMillis())));
        }
        if(scopes.isEmpty())scopes.put(work.systemId(),new Period(work.systemId(),work.systemId(),work.periodType(),work.start(),work.end(),
                0,0,0,0,0,Map.of(),work.version(),Instant.now()));
        return List.copyOf(scopes.values());
    }
    private List<Height> mergeHeights(RuntimeHistoryDao.PeriodWork work,List<Height> days) {
        Map<String,Height> groups=new LinkedHashMap<>();
        for(Height day:days) {
            Height value=new Height(work.systemId(),day.scopeId(),day.source(),work.periodType(),work.start(),
                    day.targetHeight(),day.value(),day.speed(),day.contributingRuns());
            groups.merge(day.scopeId()+"/"+day.source()+"/"+day.targetHeight().stripTrailingZeros().toPlainString(),value,PeriodStatistics::mergeHeight);
        }
        return List.copyOf(groups.values());
    }
    private static Height mergeHeight(Height a,Height b) {
        return new Height(a.systemId(),a.scopeId(),a.source(),a.periodType(),a.periodStart(),a.targetHeight(),
                merge(a.value(),b.value()),a.speed()==null?b.speed():b.speed()==null?a.speed():merge(a.speed(),b.speed()),
                a.contributingRuns()+b.contributingRuns());
    }
    private static Measure single(BigDecimal value,Instant at,BigDecimal height) {
        return new Measure(value,1,value,value,at,at,height,height);
    }
    static Measure merge(Measure a,Measure b) {
        boolean min=b.minimum().compareTo(a.minimum())<0,max=b.maximum().compareTo(a.maximum())>0;
        return new Measure(a.sum().add(b.sum()),a.count()+b.count(),min?b.minimum():a.minimum(),max?b.maximum():a.maximum(),
                min?b.minimumAt():a.minimumAt(),max?b.maximumAt():a.maximumAt(),
                min?b.minimumHeight():a.minimumHeight(),max?b.maximumHeight():a.maximumHeight(),
                earlier(a.firstAt(),b.firstAt()),later(a.lastAt(),b.lastAt()));
    }
    private static Instant earlier(Instant a,Instant b){return a==null?b:b==null?a:a.isBefore(b)?a:b;}
    private static Instant later(Instant a,Instant b){return a==null?b:b==null?a:a.isAfter(b)?a:b;}
}
