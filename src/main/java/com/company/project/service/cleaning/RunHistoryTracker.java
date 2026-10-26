package com.company.project.service.cleaning;

import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.SystemRealtimeStatusBo.Status;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** 一个系统顺序处理一批事实；失败重试使用同一不可变写任务，不重复推进状态。 */
final class RunHistoryTracker {
    private static final BigDecimal STEP = new BigDecimal("0.5");
    private final String captureId = UUID.randomUUID().toString();
    String captureId(){return captureId;}
    private final Map<String, Device> devices = new HashMap<>();
    private static final class Device {
        long sequence = Long.MIN_VALUE;
        Boolean previousActive;
        boolean observedZero;
        Active run;
        final Map<String, Event> events = new LinkedHashMap<>();
        Write pending;
    }
    private static final class Active {
        final String code = "RUN-" + UUID.randomUUID();
        final Instant started;
        boolean abnormal, terminated;
        final Map<String, Measure> measures = new TreeMap<>();
        final Map<String, Extremum> extrema = new TreeMap<>();
        final Map<String, Long> motion = new TreeMap<>();
        final Map<Integer, BigDecimal> buckets = new HashMap<>();
        final Map<Integer, String> segments = new HashMap<>();
        final Set<String> eventCodes = new HashSet<>();
        final Map<String, Object> configuration;
        TelemetrySample previous;
        Active(Instant started, Map<String,Object> configuration) { this.started=started; this.configuration=configuration; }
    }
    synchronized void restoreEvents(String deviceId,List<Event> events) {
        Device device=devices.computeIfAbsent(deviceId,k->new Device());
        for(Event event:events)if(event.endedAt()==null)device.events.put(event.sourceKey(),event);
    }
    synchronized TelemetrySample pending(String deviceId,long sequence) {
        Device device=devices.get(deviceId);
        return device!=null&&device.pending!=null&&device.pending.sequence()==sequence?device.pending.latest():null;
    }
    synchronized void accept(PlcDeviceSnapshotBo raw, TelemetrySample sample, PlcAccessProperties.Device config,
                             Map<String,Object> parameters, Consumer<Write> sink) {
        Device device = devices.computeIfAbsent(raw.deviceId(), id -> new Device());
        if (device.pending != null) {
            if (device.pending.sequence() != raw.sequence()) throw new IllegalStateException("必须先确认前一采集批次");
            sink.accept(device.pending); device.sequence=raw.sequence(); device.pending=null;
            device.events.values().removeIf(e->e.endedAt()!=null); return;
        }
        if (raw.sequence() <= device.sequence) return;
        Boolean active = flag(raw, "automaticCycleActive", "cycleActive", "runActive", "m106");
        boolean maintenance = sample.status()!=null && sample.status().systemStatus()==Status.MAINTENANCE;
        if (raw.online() && !maintenance && Boolean.TRUE.equals(active) && device.observedZero
                && Boolean.FALSE.equals(device.previousActive) && device.run==null) {
            var facts = new LinkedHashMap<>(parameters);
            facts.put("captureId",captureId);
            if (config != null) {
                facts.put("encoderBottomCounts", new TreeMap<>(config.getEncoderBottomCounts()));
                if(config.getHeightMin()!=null)facts.put("heightMin",config.getHeightMin());
                if(config.getHeightMax()!=null)facts.put("heightMax",config.getHeightMax());
            }
            device.run=new Active(raw.sampledAt(),facts);
        }
        updateEvents(device, raw, sample);
        Active run=device.run;
        SavedFrame frame=null;
        Run result=null;
        if(run!=null) {
            run.eventCodes.addAll(device.events.values().stream().filter(e -> e.endedAt()==null)
                    .map(Event::eventCode).toList());
            run.abnormal |= Boolean.TRUE.equals(flag(raw,"cycleAbnormal","runAbnormal","m60"));
            run.terminated |= Boolean.TRUE.equals(flag(raw,"cycleAbnormalEnd","runAbnormalEnd","m61"));
            if(raw.online() && !maintenance) frame=measure(run,raw,sample,config);
            String endReason=!raw.online()?"PLC_DISCONNECTED":maintenance?"MAINTENANCE_SWITCH"
                    :Boolean.FALSE.equals(active)?"M106_ENDED":null;
            String outcome=endReason==null?null:!"M106_ENDED".equals(endReason)||run.terminated?"ABNORMAL_ENDED"
                    :run.abnormal?"ABNORMAL_COMPLETED":"NORMAL_COMPLETED";
            result=new Run(run.code,raw.deviceId(),run.started,endReason==null?null:raw.sampledAt(),
                    raw.sampledAt(),endReason==null?"RUNNING":"ENDED",outcome,endReason,
                    run.abnormal,run.terminated,raw.sequence(),run.measures,run.motion,run.eventCodes,run.configuration,run.extrema);
            if(endReason!=null)device.run=null;
        }
        if(!raw.online()) { device.previousActive=null; device.observedZero=false; }
        else if(active!=null) {
            device.previousActive=active;
            if(!active) device.observedZero=true;
        }
        device.pending=new Write(captureId,raw.deviceId(),raw.sequence(),raw.sampledAt(),sample,result,frame,
                new ArrayList<>(device.events.values()));
        sink.accept(device.pending);
        device.sequence=raw.sequence();device.pending=null;
        device.events.values().removeIf(e->e.endedAt()!=null);
    }
    private SavedFrame measure(Active run,PlcDeviceSnapshotBo raw,TelemetrySample sample,PlcAccessProperties.Device config) {
        var selected=new LinkedHashMap<Integer,Selection>();
        boolean anyUp=false;
        BigDecimal minimum=config==null?null:config.getHeightMin(), maximum=config==null?null:config.getHeightMax();
        for(int i=0;i<sample.winches().size();i++) {
            var w=sample.winches().get(i); int slot=i+1;
            if(!"UP".equals(w.direction())) {
                if(run.previous!=null&&i<run.previous.winches().size()&&"UP".equals(run.previous.winches().get(i).direction()))
                    selected.put(slot,new Selection(null,false,run.segments.get(slot),4));
                run.segments.remove(slot);run.buckets.remove(slot);continue;
            }
            anyUp=true;
            add(run.measures,"W"+slot+"/BATCH",BigDecimal.ONE,raw.sampledAt(),w.heightMeters());
            String segment=run.segments.computeIfAbsent(slot,k->UUID.randomUUID().toString());
            BigDecimal h=w.heightMeters();
            add(run.measures,"W"+slot+"/HEIGHT",h,raw.sampledAt(),h);
            BigDecimal a=sample.ropes().size()>i*2?sample.ropes().get(i*2).tension():null;
            BigDecimal b=sample.ropes().size()>i*2+1?sample.ropes().get(i*2+1).tension():null;
            add(run.measures,"W"+slot+"/ROPE1",a,raw.sampledAt(),h);
            add(run.measures,"W"+slot+"/ROPE2",b,raw.sampledAt(),h);
            boolean extreme=add(run.measures,"W"+slot+"/ROPE_TOTAL",w.totalTension(),raw.sampledAt(),h);
            add(run.measures,"W"+slot+"/SERVO",w.torqueKg(),raw.sampledAt(),h);
            add(run.measures,"W"+slot+"/SPEED",w.speed(),raw.sampledAt(),h);
            if(h==null) {
                if(extreme)selected.put(slot,new Selection(null,false,segment,16));
                continue;
            }
            BigDecimal origin=minimum==null?BigDecimal.ZERO:minimum;
            boolean inRange=minimum==null||h.compareTo(minimum)>=0;
            BigDecimal bucket=h.subtract(origin).divide(STEP,0,RoundingMode.FLOOR).multiply(STEP).add(origin);
            if(maximum!=null&&bucket.compareTo(maximum)>0||maximum!=null&&h.compareTo(maximum)>=0)bucket=maximum;
            BigDecimal previous=run.buckets.get(slot);
            boolean node=inRange&&(previous==null||bucket.compareTo(previous)>0);
            if(node)run.buckets.put(slot,bucket);
            if(node||extreme)selected.put(slot,new Selection(bucket,node,segment,(node?1:0)|(extreme?16:0)));
            if(Boolean.FALSE.equals(flag(raw,"automaticCycleActive","cycleActive","runActive","m106"))) {
                var prior=selected.get(slot);
                selected.put(slot,prior==null?new Selection(null,false,segment,4)
                        :new Selection(prior.targetHeight(),prior.heightNode(),segment,prior.reasonMask()|4));
            }
            if(run.previous!=null && i<run.previous.winches().size()) {
                var old=run.previous.winches().get(i);
                if("UP".equals(old.direction())) {
                    long elapsed=Math.max(0,Duration.between(run.previous.sampledAt(),sample.sampledAt()).toMillis());
                    splitMotion(run.motion,"W"+slot,run.previous.sampledAt(),sample.sampledAt(),elapsed);
                }
            }
        }
        if(anyUp) {
            add(run.measures,"SYSTEM/BATCH",BigDecimal.ONE,raw.sampledAt(),null);
            BigDecimal total=sample.systemTotalTension();
            boolean extreme=false;
            if(total!=null) {
                var provenance=new Extremum(raw.sampledAt(),sample.winches().stream().map(TelemetrySample.WinchSample::heightMeters).toList(),captureId,raw.sequence());
                for(String scope:List.of("",raw.sampledAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()+"/")) {
                    Measure old=run.measures.get(scope+"SYSTEM/ROPE_TOTAL");
                    if(old==null||total.compareTo(old.minimum())<0) {run.extrema.put(scope+"SYSTEM_MIN",provenance);extreme=true;}
                    if(old==null||total.compareTo(old.maximum())>0) {run.extrema.put(scope+"SYSTEM_MAX",provenance);extreme=true;}
                }
                add(run.measures,"SYSTEM/ROPE_TOTAL",total,raw.sampledAt(),null);
            }
            if(extreme)for(int i=0;i<sample.winches().size();i++)if("UP".equals(sample.winches().get(i).direction())) {
                var old=selected.get(i+1);
                selected.put(i+1,old==null?new Selection(null,false,run.segments.get(i+1),16)
                        :new Selection(old.targetHeight(),old.heightNode(),old.segmentCode(),old.reasonMask()|16));
                break;
            }
            if(run.previous!=null&&run.previous.winches().stream().anyMatch(w->"UP".equals(w.direction())))
                splitMotion(run.motion,"SYSTEM",run.previous.sampledAt(),sample.sampledAt(),
                        Math.max(0,Duration.between(run.previous.sampledAt(),sample.sampledAt()).toMillis()));
        }
        run.previous=sample;
        return selected.isEmpty()?null:new SavedFrame(captureId+":"+raw.sequence(),run.code,raw.sequence(),
                raw.sampledAt(),selected,raw,sample);
    }
    private static void splitMotion(Map<String,Long> values,String key,Instant start,Instant end,long millis) {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        Instant cursor=start;
        while(cursor.isBefore(end)) {
            var date=cursor.atZone(zone).toLocalDate();
            Instant until=date.plusDays(1).atStartOfDay(zone).toInstant();
            if(until.isAfter(end))until=end;
            long part=Duration.between(cursor,until).toMillis();
            values.merge(date+"/"+key,part,Long::sum);cursor=until;
        }
    }
    static boolean add(Map<String,Measure> values,String key,BigDecimal value,Instant at,BigDecimal height) {
        if(value==null)return false;
        if(at!=null&&(key.startsWith("W")||key.startsWith("SYSTEM/")))
            accumulate(values,at.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()+"/"+key,value,at,height);
        return accumulate(values,key,value,at,height);
    }
    private static boolean accumulate(Map<String,Measure> values,String key,BigDecimal value,Instant at,BigDecimal height) {
        if(value==null)return false;
        Measure old=values.get(key);
        boolean min=old==null||value.compareTo(old.minimum())<0,max=old==null||value.compareTo(old.maximum())>0;
        values.put(key,new Measure((old==null?BigDecimal.ZERO:old.sum()).add(value),old==null?1:old.count()+1,
                min?value:old.minimum(),max?value:old.maximum(),min?at:old.minimumAt(),max?at:old.maximumAt(),
                min?height:old.minimumHeight(),max?height:old.maximumHeight(),
                old==null||old.firstAt()==null||at.isBefore(old.firstAt())?at:old.firstAt(),
                old==null||old.lastAt()==null||at.isAfter(old.lastAt())?at:old.lastAt()));
        return min||max;
    }
    private void updateEvents(Device device,PlcDeviceSnapshotBo raw,TelemetrySample sample) {
        Set<String> wanted=new HashSet<>();
        if(raw.online()) {
            BigDecimal code=number(raw,"faultCode",1);
            if((code==null||code.signum()==0) && (Boolean.TRUE.equals(flag(raw,"fault"))||sample.status().systemStatus()==Status.FAULT))
                event(device,wanted,raw,sample,"FAULT/TOTAL","PLC",null,0,"系统","PLC总故障，具体设备原因尚未提供",true);
            if(code!=null&&code.signum()!=0) {
                int value=code.intValue(), slot=CleaningDeviceConverter.faultWinch(value);
                event(device,wanted,raw,sample,"FAULT/"+value,"PLC",value,slot,part(value),description(value),true);
            }
            for(int i=0;i<sample.winches().size();i++)
                if("FAULT".equals(sample.winches().get(i).status()) && (code==null||CleaningDeviceConverter.faultWinch(code.intValue())!=i+1))
                    event(device,wanted,raw,sample,"WINCH/"+(i+1),"PLC",null,i+1,"动力设备","动力设备故障，请检修对应设备",true);
        } else {
            device.events.values().stream().filter(e->"PLC".equals(e.sourceKind())&&e.endedAt()==null)
                    .forEach(e->wanted.add(e.sourceKey()));
            event(device,wanted,raw,sample,"TCP","CONNECTION",null,0,"通信","PLC连接中断",false);
        }
        for(var entry:new ArrayList<>(device.events.entrySet())) {
            Event old=entry.getValue();
            if(old.endedAt()==null&&!wanted.contains(entry.getKey()))
                device.events.put(entry.getKey(),new Event(old.eventCode(),old.deviceId(),old.recordCode(),old.sourceKey(),
                        old.sourceKind(),old.faultCode(),old.winchSlot(),old.part(),old.content(),old.alarm(),old.severity(),
                        old.firstObservedAt(),raw.sampledAt(),old.revision()+1,old.trigger(),sample,old.faultDetails()));
        }
    }
    private void event(Device device,Set<String>wanted,PlcDeviceSnapshotBo raw,TelemetrySample sample,String key,
                       String kind,Integer fault,int slot,String part,String message,boolean alarm) {
        wanted.add(key); Event old=device.events.get(key);
        String prefix=slot==0?"系统":"动力设备"+slot;
        Map<String,BigDecimal> details=new LinkedHashMap<>();
        raw.points().stream().filter(p->p.valid()&&p.value()!=null&&Set.of("motorEnableFaultCode","motorRunningFaultCode").contains(p.field()))
                .forEach(p->details.put(p.field()+"/"+p.index(),p.rawValue()==null?p.value():p.rawValue()));
        if(old!=null&&old.endedAt()==null) {
            if(!old.faultDetails().equals(details))device.events.put(key,new Event(old.eventCode(),old.deviceId(),old.recordCode(),old.sourceKey(),old.sourceKind(),old.faultCode(),
                    old.winchSlot(),old.part(),old.content(),old.alarm(),old.severity(),old.firstObservedAt(),null,old.revision()+1,old.trigger(),null,details));
            return;
        }
        device.events.put(key,new Event("EVT-"+UUID.randomUUID(),raw.deviceId(),device.run==null?null:device.run.code,key,
                kind,fault,slot,part,prefix+"："+message,alarm,alarm?"SERIOUS":null,raw.sampledAt(),null,1,sample,null,details));
    }
    private static String part(int code) {
        if(code>=10&&code<=18)return "电机/驱动器";
        if(code>=20&&code<=27)return "钢绳/连接";
        if(code>=28&&code<=35)return "拉力传感器";
        if(code>=40&&code<=43)return "编码器";
        return "系统运行";
    }
    private static String description(int code) {
        if(code>=10&&code<=13)return "电机使能故障，请检查本机电机与驱动器";
        if(code>=15&&code<=18)return "电机运行故障，请检查本机电机与驱动器";
        if(code>=20&&code<=27)return "拉力"+(code-19)+"异常，请检查对应钢绳、连接与受力";
        if(code>=28&&code<=35)return "传感器"+(code-27)+"故障，请检查对应传感器与接线";
        if(code>=40&&code<=43)return "编码器读数异常，请检查本机编码器与连接";
        return code==50?"无法移动告警，PLC正在按既定策略重试":"PLC故障，原码"+code;
    }
    static BigDecimal number(PlcDeviceSnapshotBo raw,String field,int index) {
        return raw.points().stream().filter(p->field.equals(p.field())&&p.index()==index&&p.valid())
                .map(PlcDeviceSnapshotBo.Point::value).filter(Objects::nonNull).findFirst().orElse(null);
    }
    private static Boolean flag(PlcDeviceSnapshotBo raw,String...fields) {
        for(String field:fields) { var n=number(raw,field,1);if(n!=null)return n.signum()!=0; }return null;
    }
}
