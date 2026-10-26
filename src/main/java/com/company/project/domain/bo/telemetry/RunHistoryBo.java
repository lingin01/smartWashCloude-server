package com.company.project.domain.bo.telemetry;

import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 正式历史的不可变写入和查询模型；不依赖HTTP类型。 */
public final class RunHistoryBo {
    private RunHistoryBo() { }
    public static final String RULE = "M106_HEIGHT_05_V1";
    public record Measure(BigDecimal sum, long count, BigDecimal minimum, BigDecimal maximum,
                          Instant minimumAt, Instant maximumAt, BigDecimal minimumHeight, BigDecimal maximumHeight,
                          Instant firstAt,Instant lastAt) {
        public Measure(BigDecimal sum,long count,BigDecimal minimum,BigDecimal maximum,Instant minimumAt,
                       Instant maximumAt,BigDecimal minimumHeight,BigDecimal maximumHeight) {
            this(sum,count,minimum,maximum,minimumAt,maximumAt,minimumHeight,maximumHeight,minimumAt,maximumAt);
        }
        public BigDecimal average() {
            return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 6, java.math.RoundingMode.HALF_UP);
        }
    }
    public record Extremum(Instant at,List<BigDecimal> heights,String captureId,long sequence) {
        public Extremum { heights=Collections.unmodifiableList(new ArrayList<>(heights)); }
    }
    public record Run(String recordCode, String deviceId, Instant startedAt, Instant endedAt,
                      Instant observedThrough, String state, String result, String endReason,
                      boolean hadAbnormal, boolean hadAbnormalEnd, long lastSequence,
                      Map<String, Measure> measures, Map<String, Long> motionMillis,
                      Set<String> eventCodes, Map<String, Object> configuration,Map<String,Extremum> extrema) {
        public Run {
            measures = Map.copyOf(measures); motionMillis = Map.copyOf(motionMillis);
            eventCodes = Set.copyOf(eventCodes); configuration = Map.copyOf(configuration);
            extrema=extrema==null?Map.of():Map.copyOf(extrema);
        }
        public Run(String code,String device,Instant start,Instant end,Instant through,String state,String result,String reason,
                   boolean abnormal,boolean abnormalEnd,long sequence,Map<String,Measure> measures,Map<String,Long> motion,
                   Set<String> events,Map<String,Object> configuration) {
            this(code,device,start,end,through,state,result,reason,abnormal,abnormalEnd,sequence,measures,motion,events,configuration,Map.of());
        }
    }
    public record Selection(BigDecimal targetHeight, boolean heightNode, String segmentCode, int reasonMask) { }
    public record SavedFrame(String sampleKey, String recordCode, long sequence, Instant sampledAt,
                             Map<Integer, Selection> selections, PlcDeviceSnapshotBo raw, TelemetrySample sample) {
        public SavedFrame { selections = Map.copyOf(selections); }
    }
    public record Event(String eventCode, String deviceId, String recordCode, String sourceKey,
                        String sourceKind, Integer faultCode, int winchSlot, String part, String content,
                        boolean alarm, String severity, Instant firstObservedAt, Instant endedAt,
                        long revision, TelemetrySample trigger, TelemetrySample end,Map<String,BigDecimal> faultDetails) {
        public Event {faultDetails=faultDetails==null?Map.of():Map.copyOf(faultDetails);}
        public Event(String code,String device,String record,String key,String kind,Integer fault,int slot,String part,String content,
                     boolean alarm,String severity,Instant start,Instant ended,long revision,TelemetrySample trigger,TelemetrySample end) {
            this(code,device,record,key,kind,fault,slot,part,content,alarm,severity,start,ended,revision,trigger,end,Map.of());
        }
        public String direction() {
            if(trigger==null)return null;
            return winchSlot>0&&trigger.winches().size()>=winchSlot?trigger.winches().get(winchSlot-1).direction():trigger.status().direction();
        }
    }
    public record Write(String captureId, String deviceId, long sequence, Instant observedAt,
                        TelemetrySample latest, Run run, SavedFrame frame, List<Event> events,
                        com.company.project.domain.bo.CleaningCommand command,
                        com.company.project.domain.bo.CleaningCommandStatusBo commandStatus,boolean transportAcknowledgement) {
        public Write { events = List.copyOf(events); }
        public Write(String captureId,String deviceId,long sequence,Instant observedAt,TelemetrySample latest,
                     Run run,SavedFrame frame,List<Event> events,com.company.project.domain.bo.CleaningCommand command,
                     com.company.project.domain.bo.CleaningCommandStatusBo commandStatus) {
            this(captureId,deviceId,sequence,observedAt,latest,run,frame,events,command,commandStatus,true);
        }
        public Write(String captureId,String deviceId,long sequence,Instant observedAt,TelemetrySample latest,
                     Run run,SavedFrame frame,List<Event> events) {
            this(captureId,deviceId,sequence,observedAt,latest,run,frame,events,null,null,false);
        }
    }
    public record Page<T>(List<T> entries, long total) {
        public Page { entries = List.copyOf(entries); }
    }
    public record Period(String systemId, String scopeId, String periodType, Instant start, Instant end,
                         long started, long normalCompleted, long abnormalCompleted, long abnormalEnded,
                         long running, Map<String, Measure> measures, long version, Instant computedAt, long motionMillis) {
        public Period(String systemId,String scopeId,String periodType,Instant start,Instant end,long started,
                      long normalCompleted,long abnormalCompleted,long abnormalEnded,long running,
                      Map<String,Measure> measures,long version,Instant computedAt) {
            this(systemId,scopeId,periodType,start,end,started,normalCompleted,abnormalCompleted,abnormalEnded,running,measures,version,computedAt,0);
        }
    }
    public record Height(String systemId, String scopeId, String source, String periodType, Instant periodStart,
                         BigDecimal targetHeight, Measure value, Measure speed, long contributingRuns) { }
}
