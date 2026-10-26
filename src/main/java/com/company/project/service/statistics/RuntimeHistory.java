package com.company.project.service.statistics;

import com.company.project.domain.bo.*;
import com.company.project.domain.bo.telemetry.HistoryReader;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Period;
import com.company.project.info.buffer.cleaning.DurableHistoryInbox;
import com.company.project.repository.cleaning.RuntimeHistoryDao;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 统计模块私有的历史保存/查询协作对象，保留DAO事务代理边界。 */
final class RuntimeHistory {
    private final RuntimeHistoryDao dao;
    private final DurableHistoryInbox inbox;
    private final PeriodStatistics periods;
    private final ExportJobs exports;
    private final com.company.project.info.buffer.plc.PlcMessageBuffer messages;
    private final Map<String,TelemetrySample> restored=new ConcurrentHashMap<>();
    private final Map<String,Map<String,Object>> configurations=new ConcurrentHashMap<>();
    private final Map<String,List<Event>> activeEvents=new ConcurrentHashMap<>();
    RuntimeHistory(RuntimeHistoryDao dao,DurableHistoryInbox inbox,com.company.project.info.buffer.plc.PlcMessageBuffer messages,
                   com.company.project.repository.device.PageSystemRegistry systems,String exportDirectory) {
        this.dao=dao;this.inbox=inbox;this.messages=messages;periods=new PeriodStatistics(dao);
        exports=new ExportJobs(dao,systems,exportDirectory);
    }
    void initialize() {
        dao.prepare();
        persist(Integer.MAX_VALUE);
        dao.recoverInterruptedRuns(Instant.now());
        for(String device:dao.deviceIds()) {
            dao.lastState(device).ifPresent(s->restored.put(device,s));
            refreshConfiguration(device);
            activeEvents.put(device,dao.activeEvents(device));
        }
        exports.initialize();
    }
    void accept(Write write) { inbox.append(write); }
    synchronized void persist(int maximum) {
        for(int i=0;i<maximum;i++) {
            var next=inbox.peek();if(next.isEmpty())return;
            dao.save(next.get().value());
            if(next.get().value().command()!=null&&next.get().value().transportAcknowledgement())messages.auditCommitted(next.get().value().command().requestId());
            inbox.acknowledge(next.get());
        }
    }
    void periodCycle() { periods.cycle(); }
    void exportCycle() { periods.cycle();exports.cycle(); }
    ExportAccess exports(com.company.project.domain.param.cleaning.CleaningOperationContext context) { return exports.access(context); }
    void close() {try{exports.close();}catch(java.io.IOException failure){throw new IllegalStateException(failure);} }
    Optional<TelemetrySample> restored(String device) { return Optional.ofNullable(restored.get(device)); }
    List<Event> activeEvents(String device) { return activeEvents.getOrDefault(device,List.of()); }
    Map<String,Object> configuration(String device) { return configurations.getOrDefault(device,Map.of()); }
    private void refreshConfiguration(String device) {
        Map<String,Object> snapshot=new LinkedHashMap<>(dao.configuration(device));
        var saved=dao.settings(device);
        snapshot.put("settingsSavedVersion",saved.savedVersion());
        if(saved.saved()!=null)snapshot.put("settingsSaved",saved.saved());
        if(saved.appliedVersion()!=null)snapshot.put("settingsAppliedVersion",saved.appliedVersion());
        if(saved.applied()!=null)snapshot.put("settingsApplied",saved.applied());
        configurations.put(device,Map.copyOf(snapshot));
    }
    StoredSettingsBo settings(String device) { return dao.settings(device); }
    StoredThresholdsBo thresholds(String device,String winch){return dao.thresholds(device,winch);}
    StoredThresholdsBo saveThresholds(CleaningCommand command,String winch,long version,List<StoredThresholdsBo.Value> values) {
        return dao.saveThresholds(command,winch,version,values);
    }
    StoredSettingsBo saveSettings(CleaningCommand command,long version,CleaningSettingsBo value) {
        var saved=dao.saveSettings(command,version,value);refreshConfiguration(command.deviceId());return saved;
    }
    HistoryReader reader(String device) {
        return new HistoryReader() {
            public Page<Run> runs(Instant f,Instant t,String r,int o,int l) { return dao.runs(device,f,t,r,o,l); }
            public Optional<Run> run(String code) { return dao.run(device,code); }
            public List<SavedFrame> frames(String code,int o,int l) { return dao.frames(device,code,o,l); }
            public Optional<SavedFrame> frame(String code,String key) { return dao.frame(device,code,key); }
            public List<LocalDate> dates(int o,int l) { return dao.dates(device,o,l); }
            public Page<Event> events(Instant f,Instant t,Boolean a,Integer w,String r,int o,int l) { return dao.events(device,f,t,a,w,r,o,l); }
            public Page<Event> events(Instant f,Instant t,Boolean a,Integer w,String r,String direction,int o,int l) { return dao.events(device,f,t,a,w,r,direction,o,l); }
            public Optional<Event> event(String code) { return dao.event(device,code); }
            public List<Period> periods(String type,Instant f,Instant t,String scope) { return dao.periods(device,type,f,t,scope); }
            public List<Height> heights(String type,Instant start,String scope) { return dao.heights(device,type,start,scope); }
            public List<Event> activeEvents() { return dao.activeEvents(device); }
            public com.company.project.domain.bo.telemetry.ArchiveMetricBo archive(String scope,String source,Instant from,Instant to,String grouping) {
                var days=dao.periods(device,"DAY",from,to,scope);
                Map<String,Measure> groups=new TreeMap<>();Measure full=null;
                for(Period day:days) {
                    Measure value=day.measures().get(source);if(value==null)continue;
                    String key=day.start().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
                    if("MONTH".equals(grouping))key=key.substring(0,7);
                    groups.merge(key,value,PeriodStatistics::merge);
                    full=full==null?value:PeriodStatistics.merge(full,value);
                }
                var points=groups.entrySet().stream().map(e->new com.company.project.domain.bo.telemetry.ArchiveMetricBo.Point(e.getKey(),
                        java.time.LocalDate.parse(e.getKey().length()==7?e.getKey()+"-01":e.getKey()).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),e.getValue())).toList();
                return new com.company.project.domain.bo.telemetry.ArchiveMetricBo(points,full,dao.statisticsReady(device,"DAY",from,to));
            }
            public List<Height> runHeights(String code,String scope) {
                return profile(code,scope,null,null);
            }
            public List<Height> runHeights(String code,String scope,Instant from,Instant to) {
                return profile(code,scope,from,to);
            }
            private List<Height> profile(String code,String scope,Instant from,Instant to) {
                Run run=dao.run(device,code).orElseThrow(()->new IllegalArgumentException("运行记录不存在"));
                String system=dao.systemId(device);
                var cached=from==null&&to==null?dao.runHeightCache(device,code):Optional.<List<Height>>empty();
                if(cached.isPresent())return cached.get().stream().filter(h->h.scopeId().equals(scope==null?system:scope)).toList();
                Iterable<SavedFrame> frames=()->new Iterator<>() {
                    List<SavedFrame> batch=List.of();int index,offset;boolean end;
                    public boolean hasNext() {
                        if(index<batch.size())return true;if(end)return false;
                        batch=dao.frames(device,code,offset,500);offset+=batch.size();index=0;end=batch.size()<500;
                        return !batch.isEmpty();
                    }
                    public SavedFrame next(){if(!hasNext())throw new NoSuchElementException();return batch.get(index++);}
                };
                var work=new RuntimeHistoryDao.PeriodWork(system,"DAY",run.startedAt(),run.observedThrough(),run.lastSequence());
                Iterable<SavedFrame> selected=()->java.util.stream.StreamSupport.stream(frames.spliterator(),false)
                        .filter(f->from==null||!f.sampledAt().isBefore(from)).filter(f->to==null||f.sampledAt().isBefore(to)).iterator();
                var profile=periods.dayHeights(work,selected);
                if(run.endedAt()!=null&&from==null&&to==null)dao.cacheRunHeights(device,code,run.lastSequence(),profile);
                return profile.stream().filter(h->h.scopeId().equals(scope==null?system:scope)).toList();
            }
        };
    }
}
