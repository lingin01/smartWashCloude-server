package com.company.project.repository.cleaning;

import com.company.project.config.PageAccessProperties;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Period;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.company.project.domain.bo.telemetry.RunHistoryBo.RULE;

@Repository
@ConditionalOnProperty(prefix="cleaning.history",name="runtime-enabled",havingValue="true")
public class JdbcRuntimeHistoryDao implements RuntimeHistoryDao {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NamedParameterJdbcTemplate streaming;
    private final PageAccessProperties mappings;
    public JdbcRuntimeHistoryDao(NamedParameterJdbcTemplate jdbc,ObjectMapper mapper,PageAccessProperties mappings) {
        this.jdbc=jdbc;this.mapper=mapper;this.mappings=mappings;
        var template=new org.springframework.jdbc.core.JdbcTemplate(jdbc.getJdbcTemplate().getDataSource());
        template.setFetchSize(Integer.MIN_VALUE);this.streaming=new NamedParameterJdbcTemplate(template);
    }
    private static LocalDateTime utc(Instant instant) {
        return instant==null?null:LocalDateTime.ofInstant(instant,ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private String json(Object value) {
        if(value==null)return null;
        try{return mapper.writeValueAsString(value);}catch(Exception failure){throw new IllegalArgumentException("无法序列化历史对象",failure);}
    }
    private <T>T read(String value,Class<T> type) {
        try{return mapper.readValue(value,type);}catch(Exception failure){throw new IllegalStateException("历史数据无法解析",failure);}
    }
    private String system(String deviceId) {
        var ids=jdbc.queryForList("SELECT system_id FROM cleaning_system WHERE device_id=:d",p().addValue("d",deviceId),String.class);
        if(ids.isEmpty())throw new IllegalStateException("PLC尚未绑定数据库系统档案："+deviceId);
        return ids.get(0);
    }
    @Override public String systemId(String deviceId) { return system(deviceId); }
    @Override @Transactional public void prepare() { registerConfiguredTopology(); }
    @Transactional
    public void registerConfiguredTopology() {
        for(var s:mappings.getSystems()) {
            var systemArgs=p().addValue("s",s.getSystemCode()).addValue("d",s.getDeviceId());
            for(var row:jdbc.queryForList("SELECT device_id,system_code FROM cleaning_system WHERE device_id=:d OR system_id=:s OR system_code=:s",systemArgs))
                if(!s.getDeviceId().equals(row.get("device_id"))||row.get("system_code")!=null&&!s.getSystemCode().equals(row.get("system_code")))
                    throw new IllegalStateException("系统公开编号与已有PLC归属冲突，请核对档案配置");
            jdbc.update("INSERT INTO cleaning_system(system_id,device_id,system_code,system_name) VALUES(:s,:d,:s,:n) ON DUPLICATE KEY UPDATE system_code=VALUES(system_code),system_name=VALUES(system_name)",
                    p().addValue("s",s.getSystemCode()).addValue("d",s.getDeviceId()).addValue("n",s.getSystemName()));
            String id=system(s.getDeviceId());
            for(var w:s.getWinches()) {
                var winchArgs=p().addValue("w",w.getWinchId()).addValue("s",id).addValue("slot",w.getGroupNo()).addValue("code",w.getWinchCode());
                for(var row:jdbc.queryForList("SELECT winch_id,system_id,slot_no FROM cleaning_winch WHERE winch_id=:w OR (system_id=:s AND (slot_no=:slot OR winch_code=:code))",winchArgs))
                    if(!w.getWinchId().equals(row.get("winch_id"))||!id.equals(row.get("system_id"))||((Number)row.get("slot_no")).intValue()!=w.getGroupNo())
                        throw new IllegalStateException("动力设备编号、系统及槽位与已有档案冲突");
                jdbc.update("INSERT INTO cleaning_winch(winch_id,system_id,slot_no,winch_code,winch_name) VALUES(:w,:s,:slot,:code,:n) ON DUPLICATE KEY UPDATE winch_code=VALUES(winch_code),winch_name=VALUES(winch_name)",
                        p().addValue("w",w.getWinchId()).addValue("s",id).addValue("slot",w.getGroupNo()).addValue("code",w.getWinchCode()).addValue("n",w.getWinchName()));
                for(var r:w.getSensors()) {
                    String rope=r.getRopeId()==null||r.getRopeId().isBlank()?r.getSensorId():r.getRopeId();
                    var sensorArgs=p().addValue("r",rope).addValue("w",w.getWinchId()).addValue("slot",r.getRopeNo()).addValue("sensor",r.getSensorId()).addValue("code",r.getSensorCode());
                    for(var row:jdbc.queryForList("SELECT rope_id,winch_id,slot_no,tension_sensor_id FROM cleaning_rope_sensor WHERE rope_id=:r OR tension_sensor_id=:sensor OR sensor_code=:code OR (winch_id=:w AND slot_no=:slot)",sensorArgs))
                        if(!rope.equals(row.get("rope_id"))||!w.getWinchId().equals(row.get("winch_id"))||!r.getSensorId().equals(row.get("tension_sensor_id"))||((Number)row.get("slot_no")).intValue()!=r.getRopeNo())
                            throw new IllegalStateException("传感器编号与动力设备归属冲突");
                    jdbc.update("INSERT INTO cleaning_rope_sensor(rope_id,winch_id,slot_no,tension_sensor_id,sensor_code,sensor_name) VALUES(:r,:w,:slot,:sensor,:code,:n) ON DUPLICATE KEY UPDATE sensor_code=VALUES(sensor_code),sensor_name=VALUES(sensor_name)",
                            p().addValue("r",rope).addValue("w",w.getWinchId()).addValue("slot",r.getRopeNo())
                                    .addValue("sensor",r.getSensorId()).addValue("code",r.getSensorCode()).addValue("n",r.getSensorName()));
                }
            }
        }
    }
    @Override @Transactional public void save(Write write) {
        if(write.command()!=null) { saveOperation(write.command(),write.commandStatus());return; }
        String system=system(write.deviceId());
        var args=p().addValue("d",write.deviceId()).addValue("capture",bytes(write.captureId()))
                .addValue("sequence",write.sequence()).addValue("now",utc(write.observedAt()));
        jdbc.update("INSERT INTO cleaning_runtime_checkpoint(device_id,capture_id,last_sequence,updated_at) VALUES(:d,:capture,0,:now) ON DUPLICATE KEY UPDATE device_id=VALUES(device_id)",args);
        Long seen=jdbc.queryForObject("SELECT last_sequence FROM cleaning_runtime_checkpoint WHERE device_id=:d AND capture_id=:capture FOR UPDATE",args,Long.class);
        if(seen!=null&&seen>=write.sequence())return;
        Long runId=null;
        if(write.run()!=null) {
            Run r=write.run();
            var a=p().addValue("code",r.recordCode()).addValue("s",system).addValue("d",write.deviceId())
                    .addValue("start",utc(r.startedAt())).addValue("end",utc(r.endedAt())).addValue("last",utc(r.observedThrough()))
                    .addValue("day",r.startedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()).addValue("state",r.state())
                    .addValue("result",r.result()).addValue("reason",r.endReason()).addValue("rule",RULE)
                    .addValue("sequence",r.lastSequence()).addValue("payload",json(r)).addValue("config",json(r.configuration()));
            jdbc.update("INSERT INTO cleaning_run_record(record_code,system_id,device_id,mode,motion_role,started_at,ended_at,first_observed_at,last_observed_at,business_date,state,result,termination_reason,selection_rule_version,statistics_rule_version,summary_version,runtime_snapshot,conversion_snapshot,created_at,updated_at) "
                    +"VALUES(:code,:s,:d,'AUTOMATIC','AUTOMATIC_CYCLE',:start,:end,:start,:last,:day,:state,:result,:reason,:rule,:rule,:sequence,:payload,:config,:start,:last) "
                    +"ON DUPLICATE KEY UPDATE ended_at=VALUES(ended_at),last_observed_at=VALUES(last_observed_at),state=VALUES(state),result=VALUES(result),termination_reason=VALUES(termination_reason),summary_version=VALUES(summary_version),runtime_snapshot=VALUES(runtime_snapshot),updated_at=VALUES(updated_at)",a);
            runId=jdbc.queryForObject("SELECT id FROM cleaning_run_record WHERE record_code=:code AND device_id=:d",a,Long.class);
            markPeriods(system,r.startedAt());
            if(!r.startedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(write.observedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()))
                markPeriods(system,write.observedAt());
        }
        if(write.frame()!=null)saveFrame(system,runId,write,write.frame());
        if(write.run()!=null)saveRunSummaries(system,runId,write.run(),write.captureId());
        for(Event e:write.events())saveEvent(system,e,write.observedAt());
        if(write.run()!=null)for(String event:write.run().eventCodes())
            jdbc.update("INSERT INTO cleaning_event_run(event_code,record_code) VALUES(:e,:r) ON DUPLICATE KEY UPDATE event_code=VALUES(event_code)",
                    p().addValue("e",event).addValue("r",write.run().recordCode()));
        jdbc.update("UPDATE cleaning_system SET runtime_state_snapshot=:payload WHERE system_id=:s",
                p().addValue("payload",json(write.latest())).addValue("s",system));
        jdbc.update("UPDATE cleaning_runtime_checkpoint SET last_sequence=:sequence,updated_at=:now WHERE device_id=:d AND capture_id=:capture",args);
    }
    private void saveFrame(String system,Long runId,Write write,SavedFrame frame) {
        var a=p().addValue("s",system).addValue("d",write.deviceId()).addValue("capture",bytes(write.captureId()))
                .addValue("seq",frame.sequence()).addValue("time",utc(frame.sampledAt())).addValue("run",runId)
                .addValue("key",frame.sampleKey()).addValue("rule",RULE).addValue("payload",json(frame));
        int mask=frame.selections().keySet().stream().mapToInt(i->1<<(i-1)).reduce(0,(x,y)->x|y);
        a.addValue("mask",mask).addValue("total",frame.sample().systemTotalTension());
        StringBuilder columns=new StringBuilder("system_id,device_id,capture_id,sample_seq,sampled_at,received_at,selection_mask,selection_rule_version,record_id,sample_key,runtime_snapshot,system_force_kg");
        StringBuilder values=new StringBuilder(":s,:d,:capture,:seq,:time,:time,:mask,:rule,:run,:key,:payload,:total");
        Map<String,Object> systemFields=new LinkedHashMap<>();
        systemFields.put("system_state_code",raw(frame,"systemState",1));systemFields.put("system_direction_code",raw(frame,"systemDirectionRaw",1));
        systemFields.put("m3_fault_value",raw(frame,"fault",1));systemFields.put("fault_code",raw(frame,"faultCode",1));
        systemFields.put("up_complete_count_raw",raw(frame,"upwardCompletedCount",1));
        systemFields.forEach((key,value)->{columns.append(",").append(key);values.append(",:").append(key);a.addValue(key,value);});
        for(int i=1;i<=4;i++) {
            var selection=frame.selections().get(i);
            if(i>frame.sample().winches().size())continue;
            var w=frame.sample().winches().get(i-1);
            String prefix="w"+i;
            var fields=new LinkedHashMap<String,Object>();
            fields.put("run_id",runId);fields.put("winch_id",w.winchId());fields.put("height_m",w.heightMeters());
            fields.put("target_height_m",selection==null?null:selection.targetHeight());
            fields.put("height_selected",selection!=null&&selection.heightNode()?1:0);
            fields.put("selection_reason_mask",selection==null?0:selection.reasonMask());
            fields.put("direction",w.direction());fields.put("speed_mpm",w.speed());
            fields.put("state_code",raw(frame,"winchState",i));fields.put("up_value",raw(frame,"winchUp",i));fields.put("down_value",raw(frame,"winchDown",i));
            fields.put("segment_id",selection==null||selection.segmentCode()==null?null:bytes(selection.segmentCode()));
            fields.put("motion_role","UP".equals(w.direction())?"PRODUCTION_UP":null);
            fields.put("motor_force_kg",w.torqueKg());fields.put("pair_force_kg",w.totalTension());
            fields.put("encoder_raw",raw(frame,"encoderCount",i));fields.put("axis_elapsed_s",raw(frame,"axisRunSeconds",i));
            fields.put("motor_raw",raw(frame,"winchTorque",i));
            if(frame.sample().ropes().size()>=i*2)for(int j=1;j<=2;j++) {
                var rope=frame.sample().ropes().get((i-1)*2+j-1);
                fields.put("rope"+j+"_id",rope.ropeId());fields.put("sensor"+j+"_id",rope.sensorId());
                fields.put("rope"+j+"_kg",rope.tension());fields.put("rope"+j+"_raw",raw(frame,"ropeTension",(i-1)*2+j));
            }
            fields.forEach((key,value)->{String name=prefix+"_"+key;columns.append(",").append(name);values.append(",:").append(name);a.addValue(name,value);});
        }
        jdbc.update("INSERT INTO cleaning_up_sample("+columns+") VALUES("+values+") ON DUPLICATE KEY UPDATE sample_seq=VALUES(sample_seq)",a);
    }
    private void saveRunSummaries(String system,Long id,Run run,String captureId) {
        Map<String,Object> main=new LinkedHashMap<>();
        Measure total=run.measures().get("SYSTEM/ROPE_TOTAL");
        metric(main,"system_force","kg",total,true);
        extremaHeights(main,"system_force_min",run.extrema().get("SYSTEM_MIN"));
        extremaHeights(main,"system_force_max",run.extrema().get("SYSTEM_MAX"));
        main.put("all_sample_count",run.measures().containsKey("SYSTEM/BATCH")?run.measures().get("SYSTEM/BATCH").count():0);
        main.put("saved_history_batch_count",jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_up_sample WHERE record_id=:id",p().addValue("id",id),Long.class));
        var args=p().addValue("id",id);
        List<String> assignments=new ArrayList<>();
        main.forEach((key,value)->{assignments.add(key+"=:"+key);args.addValue(key,value);});
        jdbc.update("UPDATE cleaning_run_record SET "+String.join(",",assignments)+" WHERE id=:id",args);
        Map<String,Map<String,Measure>> groups=new LinkedHashMap<>();
        run.measures().forEach((key,value)->{
            if(key.length()>11&&key.charAt(10)=='/') {
                int slash=key.indexOf('/',11);
                if(slash>0)groups.computeIfAbsent(key.substring(0,slash),k->new LinkedHashMap<>()).put(key.substring(slash+1),value);
            }
        });
        Map<String,Integer> winches=winches(system);
        for(var group:groups.entrySet()) {
            String[] scope=group.getKey().split("/");
            LocalDate date=LocalDate.parse(scope[0]);boolean isSystem=scope[1].equals("SYSTEM");
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("run_id",id);row.put("system_id",system);row.put("stat_date",date);row.put("rule_version",RULE);
            row.put("updated_at",utc(run.observedThrough()));
            row.put("first_sample_at",group.getValue().values().stream().map(Measure::firstAt).filter(Objects::nonNull).min(Instant::compareTo).map(JdbcRuntimeHistoryDao::utc).orElse(null));
            row.put("last_sample_at",group.getValue().values().stream().map(Measure::lastAt).filter(Objects::nonNull).max(Instant::compareTo).map(JdbcRuntimeHistoryDao::utc).orElse(null));
            Measure force=group.getValue().get("ROPE_TOTAL");
            row.put("all_sample_count",group.getValue().containsKey("BATCH")?group.getValue().get("BATCH").count():0);
            if(isSystem) {
                metric(row,"system_force","kg",force,true);
                for(String kind:List.of("min","max")) {
                    Extremum point=run.extrema().get(date+"/SYSTEM_"+kind.toUpperCase(Locale.ROOT));
                    extremaHeights(row,"system_force_"+kind,point);
                    if(point!=null) {
                        row.put("system_force_"+kind+"_capture_id",bytes(point.captureId()));
                        row.put("system_force_"+kind+"_sample_seq",point.sequence());
                    }
                }
                row.put("last_capture_id",bytes(captureId));
                row.put("last_sample_seq",run.lastSequence());
                upsert("cleaning_run_system_day",row,Set.of("run_id","stat_date","rule_version"));
            } else {
                int slot=Integer.parseInt(scope[1].substring(1));
                String winch=winches.entrySet().stream().filter(e->e.getValue()==slot).map(Map.Entry::getKey).findFirst().orElseThrow();
                row.put("winch_id",winch);row.put("motion_role","PRODUCTION_UP");row.put("summary_version",run.lastSequence());
                row.put("motion_duration_ms",run.motionMillis().getOrDefault(date+"/"+scope[1],0L));
                metric(row,"rope1","kg",group.getValue().get("ROPE1"),true);
                metric(row,"rope2","kg",group.getValue().get("ROPE2"),true);
                metric(row,"pair_force","kg",force,true);
                metric(row,"motor_force","kg",group.getValue().get("SERVO"),true);
                metric(row,"speed","mpm",group.getValue().get("SPEED"),true);
                Measure height=group.getValue().get("HEIGHT");
                if(height!=null){row.put("height_min_m",height.minimum());row.put("height_max_m",height.maximum());}
                Instant from=date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),to=date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
                row.put("saved_height_point_count",jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_up_sample WHERE record_id=:r AND w"+slot+"_height_selected=1 AND sampled_at>=:from AND sampled_at<:to",
                        p().addValue("r",id).addValue("from",utc(from)).addValue("to",utc(to)),Long.class));
                upsert("cleaning_run_winch_day",row,Set.of("run_id","winch_id","stat_date","motion_role"));
            }
        }
    }
    private static void metric(Map<String,Object> fields,String prefix,String unit,Measure value,boolean provenance) {
        if(value==null)return;
        fields.put(prefix+"_sum_"+unit,value.sum());fields.put(prefix+"_sample_count",value.count());
        fields.put(prefix+"_min_"+unit,value.minimum());fields.put(prefix+"_max_"+unit,value.maximum());
        if(provenance) {
            fields.put(prefix+"_min_at",utc(value.minimumAt()));fields.put(prefix+"_max_at",utc(value.maximumAt()));
            if(!prefix.equals("system_force")) {
                fields.put(prefix+"_min_height_m",value.minimumHeight());fields.put(prefix+"_max_height_m",value.maximumHeight());
            }
        }
    }
    private static void extremaHeights(Map<String,Object> fields,String prefix,Extremum point) {
        if(point!=null)for(int i=0;i<point.heights().size();i++)fields.put(prefix+"_w"+(i+1)+"_height_m",point.heights().get(i));
    }
    private void upsert(String table,Map<String,Object> fields,Set<String> keys) {
        String names=String.join(",",fields.keySet());
        String values=fields.keySet().stream().map(s->":"+s).collect(java.util.stream.Collectors.joining(","));
        String changes=fields.keySet().stream().filter(s->!keys.contains(s)).map(s->s+"=VALUES("+s+")").collect(java.util.stream.Collectors.joining(","));
        jdbc.update("INSERT INTO "+table+"("+names+") VALUES("+values+") ON DUPLICATE KEY UPDATE "+changes,new MapSqlParameterSource(fields));
    }
    private static BigDecimal raw(SavedFrame f,String field,int index) {
        return f.raw().points().stream().filter(p->field.equals(p.field())&&p.index()==index)
                .map(p->p.rawValue()!=null?p.rawValue():Set.of("ropeTension","winchTorque").contains(field)&&"kg".equals(p.unit())?null:p.value())
                .filter(Objects::nonNull).findFirst().orElse(null);
    }
    private void saveEvent(String system,Event e,Instant now) {
        String winch=e.winchSlot()==0?null:jdbc.query("SELECT winch_id FROM cleaning_winch WHERE system_id=:s AND slot_no=:slot",
                p().addValue("s",system).addValue("slot",e.winchSlot()),(rs,n)->rs.getString(1)).stream().findFirst().orElse(null);
        jdbc.update("INSERT INTO cleaning_exception_event(event_code,system_id,device_id,winch_id,source_kind,source_key,event_type,is_alarm,severity,fault_code,content,event_state,first_observed_at,ended_at,revision,created_at,updated_at,runtime_snapshot,direction,fault_detail_snapshot) "
                +"VALUES(:e,:s,:d,:w,:kind,:key,:part,:alarm,:severity,:fault,:content,:state,:start,:end,:revision,:start,:now,:payload,:direction,:details) "
                +"ON DUPLICATE KEY UPDATE ended_at=VALUES(ended_at),event_state=VALUES(event_state),revision=VALUES(revision),updated_at=VALUES(updated_at),runtime_snapshot=VALUES(runtime_snapshot),fault_detail_snapshot=VALUES(fault_detail_snapshot)",
                p().addValue("e",e.eventCode()).addValue("s",system).addValue("d",e.deviceId()).addValue("w",winch)
                        .addValue("kind",e.sourceKind()).addValue("key",e.eventCode().getBytes(StandardCharsets.UTF_8))
                        .addValue("part",e.part()).addValue("alarm",e.alarm()?1:0).addValue("severity",e.severity()).addValue("fault",e.faultCode())
                        .addValue("content",e.content()).addValue("state",e.endedAt()==null?"ACTIVE":"ENDED")
                        .addValue("start",utc(e.firstObservedAt())).addValue("end",utc(e.endedAt())).addValue("revision",e.revision())
                        .addValue("now",utc(now)).addValue("payload",json(e)).addValue("direction",e.direction()).addValue("details",json(e.faultDetails())));
    }
    private void markPeriods(String system,Instant started) {
        LocalDate date=started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        for(String type:List.of("DAY","MONTH","QUARTER","HALF_YEAR","YEAR")) {
            LocalDate start=periodStart(date,type),end=periodEnd(start,type);
            jdbc.update("INSERT INTO cleaning_period_summary(system_id,scope_kind,scope_id,period_type,period_start,period_end,rule_version,requested_version,needs_rebuild,updated_at) "
                    +"VALUES(:s,'SYSTEM',:s,:type,:start,:end,:rule,1,1,:now) ON DUPLICATE KEY UPDATE requested_version=requested_version+1,needs_rebuild=1,updated_at=VALUES(updated_at)",
                    p().addValue("s",system).addValue("type",type).addValue("start",utc(start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()))
                            .addValue("end",utc(end.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant())).addValue("rule",RULE).addValue("now",utc(Instant.now())));
        }
    }
    public static LocalDate periodStart(LocalDate date,String type) {
        return switch(type) {case "DAY"->date;case "MONTH"->date.withDayOfMonth(1);
            case "QUARTER"->LocalDate.of(date.getYear(),((date.getMonthValue()-1)/3)*3+1,1);
            case "HALF_YEAR"->LocalDate.of(date.getYear(),date.getMonthValue()<=6?1:7,1);
            case "YEAR"->LocalDate.of(date.getYear(),1,1);default->throw new IllegalArgumentException("不支持的统计周期");};
    }
    public static LocalDate periodEnd(LocalDate start,String type) {
        return switch(type){case "DAY"->start.plusDays(1);case "MONTH"->start.plusMonths(1);case "QUARTER"->start.plusMonths(3);
            case "HALF_YEAR"->start.plusMonths(6);case "YEAR"->start.plusYears(1);default->throw new IllegalArgumentException("不支持的统计周期");};
    }
    @Override @Transactional public void recoverInterruptedRuns(Instant recoveredAt) {
        registerConfiguredTopology();
        var open=jdbc.query("SELECT runtime_snapshot FROM cleaning_run_record WHERE state='RUNNING' AND runtime_snapshot IS NOT NULL",Map.of(),(rs,n)->read(rs.getString(1),Run.class));
        for(Run old:open) {
            Run ended=new Run(old.recordCode(),old.deviceId(),old.startedAt(),old.observedThrough(),old.observedThrough(),
                    "ENDED","ABNORMAL_ENDED","BACKEND_RESTART",old.hadAbnormal(),true,old.lastSequence(),old.measures(),old.motionMillis(),old.eventCodes(),old.configuration(),old.extrema());
            jdbc.update("UPDATE cleaning_run_record SET state='ENDED',result='ABNORMAL_ENDED',termination_reason='BACKEND_RESTART',ended_at=:end,runtime_snapshot=:payload,updated_at=:now WHERE record_code=:code AND state='RUNNING'",
                    p().addValue("code",old.recordCode()).addValue("end",utc(old.observedThrough())).addValue("payload",json(ended)).addValue("now",utc(recoveredAt)));
            markPeriods(system(old.deviceId()),old.startedAt());
        }
        var operations=jdbc.query("SELECT requested_snapshot,feedback_snapshot FROM cleaning_operation_audit WHERE state IN ('CREATED','QUEUED') AND requested_snapshot IS NOT NULL AND feedback_snapshot IS NOT NULL",
                Map.of(),(rs,n)->Map.entry(read(rs.getString(1),CleaningCommand.class),read(rs.getString(2),CleaningCommandStatusBo.class)));
        for(var entry:operations) {
            var old=entry.getValue();
            saveOperation(entry.getKey(),new CleaningCommandStatusBo(old.requestId(),old.commandId(),old.deviceId(),old.type(),
                    CleaningCommandStatusBo.State.UNCONFIRMED,"BACKEND_RESTART","后台重启，现场执行结果未确认，不自动重发",
                    old.createdAt(),old.expiresAt(),recoveredAt));
        }
    }
    @Override public Optional<TelemetrySample> lastState(String deviceId) {
        return optional("SELECT runtime_state_snapshot FROM cleaning_system WHERE device_id=:d AND runtime_state_snapshot IS NOT NULL",p().addValue("d",deviceId),TelemetrySample.class);
    }
    @Override public List<Event> activeEvents(String deviceId) {
        return list("SELECT runtime_snapshot FROM cleaning_exception_event WHERE device_id=:d AND ended_at IS NULL AND runtime_snapshot IS NOT NULL",p().addValue("d",deviceId),Event.class);
    }
    @Override public Page<Run> runs(String deviceId,Instant from,Instant to,String result,int offset,int limit) {
        var args=p().addValue("d",deviceId).addValue("from",utc(from)).addValue("to",utc(to)).addValue("result",result)
                .addValue("offset",offset).addValue("limit",limit);
        String where=" FROM cleaning_run_record WHERE device_id=:d AND runtime_snapshot IS NOT NULL AND started_at>=:from AND started_at<:to";
        if("COMPLETED".equals(result))where+=" AND result IN ('NORMAL_COMPLETED','ABNORMAL_COMPLETED')";
        else if("INCOMPLETE".equals(result)||"FAULT_TERMINATED".equals(result))where+=" AND result='ABNORMAL_ENDED'";
        else if(result!=null)where+=" AND result=:result";
        return new Page<>(list("SELECT runtime_snapshot"+where+" ORDER BY started_at DESC,id DESC LIMIT :limit OFFSET :offset",args,Run.class),
                jdbc.queryForObject("SELECT COUNT(*)"+where,args,Long.class));
    }
    @Override public Optional<Run> run(String deviceId,String code) {
        return optional("SELECT runtime_snapshot FROM cleaning_run_record WHERE device_id=:d AND record_code=:code AND runtime_snapshot IS NOT NULL",p().addValue("d",deviceId).addValue("code",code),Run.class);
    }
    @Override public List<SavedFrame> frames(String deviceId,String code,int offset,int limit) {
        return list("SELECT f.runtime_snapshot FROM cleaning_up_sample f JOIN cleaning_run_record r ON r.id=f.record_id WHERE f.device_id=:d AND r.record_code=:code AND f.runtime_snapshot IS NOT NULL ORDER BY f.sample_seq,f.id LIMIT :limit OFFSET :offset",
                p().addValue("d",deviceId).addValue("code",code).addValue("offset",offset).addValue("limit",limit),SavedFrame.class);
    }
    @Override public List<SavedFrame> framesByTime(String deviceId,Instant from,Instant to,String result,int offset,int limit) {
        String filter=result==null?"":"COMPLETED".equals(result)?" AND r.result IN ('NORMAL_COMPLETED','ABNORMAL_COMPLETED')":
                Set.of("INCOMPLETE","FAULT_TERMINATED").contains(result)?" AND r.result='ABNORMAL_ENDED'":" AND r.result=:result";
        return list("SELECT f.runtime_snapshot FROM cleaning_up_sample f JOIN cleaning_run_record r ON r.id=f.record_id WHERE f.device_id=:d AND f.sampled_at>=:from AND f.sampled_at<:to AND f.runtime_snapshot IS NOT NULL"+filter+" ORDER BY f.sampled_at,f.id LIMIT :limit OFFSET :offset",
                p().addValue("d",deviceId).addValue("from",utc(from)).addValue("to",utc(to)).addValue("result",result).addValue("offset",offset).addValue("limit",limit),SavedFrame.class);
    }
    @Override public Optional<SavedFrame> frame(String deviceId,String code,String key) {
        return optional("SELECT f.runtime_snapshot FROM cleaning_up_sample f JOIN cleaning_run_record r ON r.id=f.record_id WHERE f.device_id=:d AND r.record_code=:code AND f.sample_key=:key",
                p().addValue("d",deviceId).addValue("code",code).addValue("key",key),SavedFrame.class);
    }
    @Override public List<LocalDate> dates(String deviceId,int offset,int limit) {
        return jdbc.query("SELECT DISTINCT business_date FROM cleaning_run_record WHERE device_id=:d AND runtime_snapshot IS NOT NULL ORDER BY business_date DESC LIMIT :limit OFFSET :offset",
                p().addValue("d",deviceId).addValue("limit",limit).addValue("offset",offset),(rs,n)->rs.getDate(1).toLocalDate());
    }
    @Override public Page<Event> events(String deviceId,Instant from,Instant to,Boolean alarm,Integer slot,String recordCode,int offset,int limit) {
        return events(deviceId,from,to,alarm,slot,recordCode,null,offset,limit);
    }
    @Override public Page<Event> events(String deviceId,Instant from,Instant to,Boolean alarm,Integer slot,String recordCode,String direction,int offset,int limit) {
        var args=p().addValue("d",deviceId).addValue("from",utc(from)).addValue("to",utc(to)).addValue("alarm",Boolean.TRUE.equals(alarm)?1:0)
                .addValue("slot",slot).addValue("record",recordCode).addValue("direction",direction).addValue("offset",offset).addValue("limit",limit);
        String where=" FROM cleaning_exception_event e WHERE e.device_id=:d AND e.runtime_snapshot IS NOT NULL AND e.first_observed_at>=:from AND e.first_observed_at<:to";
        if(alarm!=null)where+=" AND e.is_alarm=:alarm";
        if(direction!=null)where+=" AND e.direction=:direction";
        if(slot!=null)where+=" AND e.winch_id=(SELECT winch_id FROM cleaning_winch WHERE system_id=e.system_id AND slot_no=:slot)";
        if(recordCode!=null)where+=" AND EXISTS(SELECT 1 FROM cleaning_event_run l WHERE l.event_code=e.event_code AND l.record_code=:record)";
        return new Page<>(list("SELECT e.runtime_snapshot"+where+" ORDER BY e.first_observed_at DESC,e.id DESC LIMIT :limit OFFSET :offset",args,Event.class),
                jdbc.queryForObject("SELECT COUNT(*)"+where,args,Long.class));
    }
    @Override public Optional<Event> event(String deviceId,String code) {
        return optional("SELECT runtime_snapshot FROM cleaning_exception_event WHERE device_id=:d AND event_code=:code AND runtime_snapshot IS NOT NULL",p().addValue("d",deviceId).addValue("code",code),Event.class);
    }
    @Override public List<PeriodWork> pendingPeriods(int limit) {
        return jdbc.query("SELECT system_id,period_type,period_start,period_end,requested_version FROM cleaning_period_summary WHERE scope_kind='SYSTEM' AND needs_rebuild=1 ORDER BY FIELD(period_type,'DAY','MONTH','QUARTER','HALF_YEAR','YEAR'),period_start LIMIT :limit",
                p().addValue("limit",limit),(rs,n)->new PeriodWork(rs.getString(1),rs.getString(2),rs.getObject(3,LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getObject(4,LocalDateTime.class).toInstant(ZoneOffset.UTC),rs.getLong(5)));
    }
    @Override public List<Run> periodRuns(String systemId,Instant from,Instant to) {
        return list("SELECT runtime_snapshot FROM cleaning_run_record WHERE system_id=:s AND started_at>=:from AND started_at<:to AND runtime_snapshot IS NOT NULL",
                p().addValue("s",systemId).addValue("from",utc(from)).addValue("to",utc(to)),Run.class);
    }
    @Override public List<Run> measuredRuns(String systemId,Instant from,Instant to) {
        return list("SELECT runtime_snapshot FROM cleaning_run_record WHERE system_id=:s AND started_at<:to AND last_observed_at>=:from AND runtime_snapshot IS NOT NULL",
                p().addValue("s",systemId).addValue("from",utc(from)).addValue("to",utc(to)),Run.class);
    }
    @Override public Map<String,Integer> winches(String systemId) {
        Map<String,Integer> values=new LinkedHashMap<>();
        jdbc.query("SELECT winch_id,slot_no FROM cleaning_winch WHERE system_id=:s ORDER BY slot_no",p().addValue("s",systemId),
                (org.springframework.jdbc.core.RowCallbackHandler)rs->values.put(rs.getString(1),rs.getInt(2)));
        return values;
    }
    @Override public java.util.stream.Stream<SavedFrame> periodFrames(String systemId,Instant from,Instant to) {
        return streaming.queryForStream("SELECT f.runtime_snapshot FROM cleaning_up_sample f JOIN cleaning_run_record r ON r.id=f.record_id WHERE r.system_id=:s AND r.started_at>=:from AND r.started_at<:to AND r.state='ENDED' AND f.runtime_snapshot IS NOT NULL ORDER BY r.id,f.sample_seq",
                p().addValue("s",systemId).addValue("from",utc(from)).addValue("to",utc(to)),(rs,n)->read(rs.getString(1),SavedFrame.class));
    }
    @Override public List<Period> dailyPeriods(String systemId,Instant from,Instant to) {
        return list("SELECT runtime_snapshot FROM cleaning_period_summary WHERE system_id=:s AND period_type='DAY' AND period_start>=:from AND period_start<:to AND runtime_snapshot IS NOT NULL",
                p().addValue("s",systemId).addValue("from",utc(from)).addValue("to",utc(to)),Period.class);
    }
    @Override public List<Height> dailyHeights(String systemId,Instant from,Instant to) {
        return list("SELECT runtime_snapshot FROM cleaning_height_bucket WHERE system_id=:s AND period_type='DAY' AND period_start>=:from AND period_start<:to AND runtime_snapshot IS NOT NULL",
                p().addValue("s",systemId).addValue("from",utc(from)).addValue("to",utc(to)),Height.class);
    }
    @Override @Transactional public void storePeriod(PeriodWork work,List<Period> periods,List<Height> heights) {
        for(Period value:periods) {
            jdbc.update("INSERT INTO cleaning_period_summary(system_id,scope_kind,scope_id,period_type,period_start,period_end,run_started_count,run_normal_completed_count,run_abnormal_completed_count,run_terminated_count,run_ongoing_count,run_completed_count,rule_version,runtime_snapshot,computed_version,requested_version,needs_rebuild,built_at,updated_at) "
                    +"VALUES(:s,:kind,:scope,:type,:start,:end,:started,:normal,:abnormal,:ended,:running,:completed,:rule,:payload,:version,:version,0,:now,:now) "
                    +"ON DUPLICATE KEY UPDATE run_started_count=VALUES(run_started_count),run_normal_completed_count=VALUES(run_normal_completed_count),run_abnormal_completed_count=VALUES(run_abnormal_completed_count),run_terminated_count=VALUES(run_terminated_count),run_ongoing_count=VALUES(run_ongoing_count),run_completed_count=VALUES(run_completed_count),runtime_snapshot=VALUES(runtime_snapshot),computed_version=VALUES(computed_version),requested_version=GREATEST(requested_version,VALUES(requested_version)),needs_rebuild=(requested_version>VALUES(computed_version)),built_at=VALUES(built_at),updated_at=VALUES(updated_at)",
                    p().addValue("s",value.systemId()).addValue("kind",value.systemId().equals(value.scopeId())?"SYSTEM":"WINCH")
                            .addValue("scope",value.scopeId()).addValue("type",value.periodType()).addValue("start",utc(value.start())).addValue("end",utc(value.end()))
                            .addValue("started",value.started()).addValue("normal",value.normalCompleted()).addValue("abnormal",value.abnormalCompleted())
                            .addValue("ended",value.abnormalEnded()).addValue("running",value.running()).addValue("completed",value.normalCompleted()+value.abnormalCompleted())
                            .addValue("rule",RULE).addValue("payload",json(value)).addValue("version",work.version()).addValue("now",utc(value.computedAt())));
            Map<String,Object> measurements=new LinkedHashMap<>();
            measurements.put("motion_duration_ms",value.motionMillis());
            measurements.put("all_sample_count",value.measures().containsKey("BATCH")?value.measures().get("BATCH").count():0);
            metric(measurements,"rope1","kg",value.measures().get("ROPE1"),false);
            metric(measurements,"rope2","kg",value.measures().get("ROPE2"),false);
            metric(measurements,value.scopeId().equals(value.systemId())?"system_force":"pair_force","kg",value.measures().get("ROPE_TOTAL"),false);
            metric(measurements,"motor_force","kg",value.measures().get("SERVO"),false);
            metric(measurements,"speed","mpm",value.measures().get("SPEED"),false);
            var bindings=new MapSqlParameterSource(measurements).addValue("s",value.systemId()).addValue("scope",value.scopeId())
                    .addValue("type",value.periodType()).addValue("start",utc(value.start()));
            jdbc.update("UPDATE cleaning_period_summary SET "+measurements.keySet().stream().map(k->k+"=:"+k).collect(java.util.stream.Collectors.joining(","))
                    +" WHERE system_id=:s AND scope_id=:scope AND period_type=:type AND period_start=:start",bindings);
        }
        var args=p().addValue("s",work.systemId()).addValue("type",work.periodType()).addValue("start",utc(work.start()));
        jdbc.update("DELETE FROM cleaning_height_bucket WHERE system_id=:s AND period_type=:type AND period_start=:start AND rule_version='"+RULE+"'",args);
        Map<String,Integer> bucketIndices=new HashMap<>();
        for(Height h:heights) {
            int bucket=bucketIndices.merge(h.scopeId()+"/"+h.source(),1,Integer::sum)-1;
            boolean system=h.scopeId().equals(h.systemId());
            jdbc.update("INSERT INTO cleaning_height_bucket(system_id,winch_id,scope_kind,scope_id,source_role,period_type,period_start,period_end,rule_version,bucket_index,height_from_m,height_to_m,sum_kg,sample_count,min_kg,max_kg,contributing_run_count,basis,runtime_snapshot,built_at) "
                    +"VALUES(:s,:w,:kind,:scope,:source,:type,:start,:end,:rule,:bucket,:height,:height+0.5,:sum,:count,:min,:max,:runs,'SAVED_HEIGHT_POINTS',:payload,:now)",
                    p().addValue("s",h.systemId()).addValue("w",system?null:h.scopeId()).addValue("kind",system?"SYSTEM":"WINCH")
                            .addValue("scope",h.scopeId()).addValue("source",h.source()).addValue("type",h.periodType()).addValue("start",utc(h.periodStart()))
                            .addValue("end",utc(work.end())).addValue("rule",RULE).addValue("bucket",bucket)
                    .addValue("height",h.targetHeight()).addValue("sum","SPEED".equals(h.source())?BigDecimal.ZERO:h.value().sum())
                            .addValue("count","SPEED".equals(h.source())?0:h.value().count()).addValue("min","SPEED".equals(h.source())?null:h.value().minimum())
                            .addValue("max","SPEED".equals(h.source())?null:h.value().maximum()).addValue("runs",h.contributingRuns()).addValue("payload",json(h)).addValue("now",utc(Instant.now())));
            Measure speed="SPEED".equals(h.source())?h.value():h.speed();
            if(speed!=null)jdbc.update("UPDATE cleaning_height_bucket SET speed_sum_mpm=:sum,speed_sample_count=:count,speed_min_mpm=:min,speed_max_mpm=:max WHERE system_id=:s AND scope_id=:scope AND period_type=:type AND period_start=:start AND source_role=:source AND rule_version=:rule AND bucket_index=:bucket",
                    p().addValue("sum",speed.sum()).addValue("count",speed.count()).addValue("min",speed.minimum()).addValue("max",speed.maximum())
                            .addValue("s",h.systemId()).addValue("scope",h.scopeId()).addValue("type",h.periodType()).addValue("start",utc(h.periodStart()))
                            .addValue("source",h.source()).addValue("rule",RULE).addValue("bucket",bucket));
        }
    }
    @Override public List<Period> periods(String deviceId,String type,Instant from,Instant to,String scopeId) {
        return list("SELECT p.runtime_snapshot FROM cleaning_period_summary p JOIN cleaning_system s ON s.system_id=p.system_id WHERE s.device_id=:d AND p.period_type=:type AND p.period_start>=:from AND p.period_start<:to AND p.scope_id=:scope AND p.runtime_snapshot IS NOT NULL ORDER BY p.period_start",
                p().addValue("d",deviceId).addValue("type",type).addValue("from",utc(from)).addValue("to",utc(to)).addValue("scope",scopeId==null?system(deviceId):scopeId),Period.class);
    }
    @Override public List<Height> heights(String deviceId,String type,Instant start,String scopeId) {
        return list("SELECT h.runtime_snapshot FROM cleaning_height_bucket h JOIN cleaning_system s ON s.system_id=h.system_id WHERE s.device_id=:d AND h.period_type=:type AND h.period_start=:start AND h.scope_id=:scope AND h.runtime_snapshot IS NOT NULL ORDER BY h.height_from_m,h.source_role",
                p().addValue("d",deviceId).addValue("type",type).addValue("start",utc(start)).addValue("scope",scopeId==null?system(deviceId):scopeId),Height.class);
    }
    @Override public List<String> deviceIds() {
        return jdbc.queryForList("SELECT device_id FROM cleaning_system",Map.of(),String.class);
    }
    @Override public StoredSettingsBo settings(String deviceId) {
        return jdbc.queryForObject("SELECT settings_saved_version,settings_saved_snapshot,settings_applied_version,settings_applied_snapshot,settings_apply_state,settings_message FROM cleaning_system WHERE device_id=:d",
                p().addValue("d",deviceId),(rs,n)->new StoredSettingsBo(rs.getLong(1),rs.getString(2)==null?null:read(rs.getString(2),CleaningSettingsBo.class),
                        rs.getObject(3)==null?null:rs.getLong(3),rs.getString(4)==null?null:read(rs.getString(4),CleaningSettingsBo.class),rs.getString(5),rs.getString(6)));
    }
    @Override @Transactional public StoredSettingsBo saveSettings(CleaningCommand command,long expectedVersion,CleaningSettingsBo value) {
        String deviceId=command.deviceId();
        var previous=operation(deviceId,command.requestId(),null);
        if(previous.isPresent()) {
            saveOperation(command,previous.get());
            return settings(deviceId).withReceipt(previous.get());
        }
        int changed=jdbc.update("UPDATE cleaning_system SET settings_saved_version=COALESCE(settings_saved_version,0)+1,settings_saved_snapshot=:payload,settings_saved_at=:now,settings_apply_state='SAVED',settings_message='平台已保存，等待现场下发或确认' WHERE device_id=:d AND COALESCE(settings_saved_version,0)=:version",
                p().addValue("d",deviceId).addValue("version",expectedVersion).addValue("payload",json(value)).addValue("now",utc(Instant.now())));
        if(changed!=1)throw new org.springframework.dao.OptimisticLockingFailureException("配置版本冲突，请重新读取");
        var status=new CleaningCommandStatusBo(command.requestId(),command.commandId(),deviceId,command.type(),
                CleaningCommandStatusBo.State.UNCONFIRMED,"SETTINGS_SAVED","平台已保存，等待现场下发或确认",
                command.createdAt(),command.expiresAt(),Instant.now());
        saveOperation(command,status);
        return settings(deviceId).withReceipt(status);
    }
    @Override public Map<String,Object> configuration(String deviceId) {
        String value=jdbc.queryForObject("SELECT settings_source_snapshot FROM cleaning_system WHERE device_id=:d",p().addValue("d",deviceId),String.class);
        if(value==null)return Map.of();
        try{return mapper.readValue(value,new TypeReference<Map<String,Object>>(){});}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private List<StoredThresholdsBo.Value> thresholdValues(String value) {
        if(value==null)return List.of();
        try {return mapper.readValue(value,mapper.getTypeFactory().constructCollectionType(List.class,StoredThresholdsBo.Value.class));}
        catch(Exception invalid){throw new IllegalStateException("阈值配置无法读取",invalid);}
    }
    @Override public StoredThresholdsBo thresholds(String deviceId,String winchId) {
        return jdbc.queryForObject("SELECT threshold_saved_version,threshold_saved_snapshot,threshold_applied_version,threshold_applied_snapshot,threshold_apply_state FROM cleaning_winch WHERE system_id=:s AND winch_id=:w",
                p().addValue("s",system(deviceId)).addValue("w",winchId),(rs,n)->new StoredThresholdsBo(rs.getLong(1),thresholdValues(rs.getString(2)),
                        rs.getObject(3)==null?null:rs.getLong(3),thresholdValues(rs.getString(4)),rs.getString(5)));
    }
    @Override @Transactional public StoredThresholdsBo saveThresholds(CleaningCommand command,String winchId,long version,List<StoredThresholdsBo.Value> values) {
        String system=system(command.deviceId());
        var previous=operation(command.deviceId(),command.requestId(),null);
        if(previous.isPresent()) {saveOperation(command,previous.get());return thresholds(command.deviceId(),winchId).withReceipt(previous.get());}
        var sensors=jdbc.queryForList("SELECT sensor_code FROM cleaning_rope_sensor WHERE winch_id=:w",p().addValue("w",winchId),String.class);
        if(values.size()!=2||!new HashSet<>(sensors).equals(new HashSet<>(values.stream().map(StoredThresholdsBo.Value::sensorCode).toList())))
            throw new IllegalArgumentException("阈值必须属于该动力设备的两个传感器");
        for(var value:values)if(value.alarmThresholdKg()==null||value.safetyValueKg()==null
                ||value.alarmThresholdKg().signum()<=0||value.alarmThresholdKg().compareTo(value.safetyValueKg())>=0)
            throw new IllegalArgumentException("阈值须为正数且预留提示阈值小于保护值");
        int changed=jdbc.update("UPDATE cleaning_winch SET threshold_saved_version=COALESCE(threshold_saved_version,0)+1,threshold_saved_snapshot=:payload,threshold_saved_at=:now,threshold_apply_state='SAVED' WHERE system_id=:s AND winch_id=:w AND COALESCE(threshold_saved_version,0)=:version",
                p().addValue("s",system).addValue("w",winchId).addValue("version",version).addValue("payload",json(values)).addValue("now",utc(Instant.now())));
        if(changed!=1)throw new org.springframework.dao.OptimisticLockingFailureException("阈值版本冲突");
        var status=new CleaningCommandStatusBo(command.requestId(),command.commandId(),command.deviceId(),command.type(),
                CleaningCommandStatusBo.State.UNCONFIRMED,"THRESHOLDS_SAVED","阈值已保存，现场点位和回读未配置时不下发",
                command.createdAt(),command.expiresAt(),Instant.now());
        saveOperation(command,status);return thresholds(command.deviceId(),winchId).withReceipt(status);
    }
    @Override public void saveConfiguration(String deviceId,Map<String,Object> configuration) {
        jdbc.update("UPDATE cleaning_system SET settings_source_snapshot=:payload WHERE device_id=:d",p().addValue("d",deviceId).addValue("payload",json(configuration)));
    }
    @Override @Transactional public void saveOperation(CleaningCommand command,CleaningCommandStatusBo status) {
        boolean written=status.state()==CleaningCommandStatusBo.State.SUCCEEDED||"PLC_SETTINGS_WRITTEN_STORAGE_PENDING".equals(status.code());
        String system=system(command.deviceId());
        var known=jdbc.queryForList("SELECT operator_user_id FROM cleaning_operation_audit WHERE system_id=:s AND request_id=:r",
                p().addValue("s",system).addValue("r",command.requestId().getBytes(StandardCharsets.UTF_8)),Long.class);
        Long user=known.isEmpty()?jdbc.queryForObject("SELECT id FROM t_user WHERE username=:name AND deleted=0",p().addValue("name",command.operatorId()),Long.class):known.get(0);
        byte[] hash=hash(command);
        var a=p().addValue("s",system).addValue("d",command.deviceId()).addValue("request",command.requestId().getBytes(StandardCharsets.UTF_8))
                .addValue("code",command.commandId()).addValue("user",user).addValue("name",command.operatorId()).addValue("type",command.type())
                .addValue("state",status.state().name()).addValue("phase",written?"WRITE_CONFIRMED":status.state().name())
                .addValue("hash",hash).addValue("requestPayload",json(command)).addValue("payload",json(status)).addValue("result",status.code())
                .addValue("message",status.message()).addValue("created",utc(command.createdAt())).addValue("updated",utc(status.updatedAt()))
                .addValue("ack",written?1:0);
        jdbc.update("INSERT INTO cleaning_operation_audit(operation_code,system_id,device_id,request_id,request_hash,operator_user_id,operator_name,operation_type,phase,state,requested_snapshot,feedback_snapshot,result_code,result_message,write_acknowledged,requested_at,accepted_at,updated_at) "
                +"VALUES(:code,:s,:d,:request,:hash,:user,:name,:type,:phase,:state,:requestPayload,:payload,:result,:message,:ack,:created,:created,:updated) "
                +"ON DUPLICATE KEY UPDATE operation_code=operation_code",a);
        byte[] old=jdbc.queryForObject("SELECT request_hash FROM cleaning_operation_audit WHERE system_id=:s AND request_id=:request FOR UPDATE",a,byte[].class);
        if(!MessageDigest.isEqual(hash,old))throw new IllegalArgumentException("请求编号已用于其他操作");
        jdbc.update("UPDATE cleaning_operation_audit SET phase=:phase,state=:state,feedback_snapshot=:payload,result_code=:result,result_message=:message,write_acknowledged=:ack,updated_at=:updated WHERE system_id=:s AND request_id=:request AND updated_at<=:updated AND (state NOT IN ('SUCCEEDED','FAILED','REJECTED','BLOCKED') OR state=:state)",a);
    }
    private byte[] hash(CleaningCommand c) {
        try{return MessageDigest.getInstance("SHA-256").digest(json(List.of(c.deviceId(),c.operatorId(),c.type(),new TreeMap<>(c.parameters()))).getBytes(StandardCharsets.UTF_8));}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    @Override public Optional<CleaningCommandStatusBo> operation(String deviceId,String requestId,String code) {
        var args=p().addValue("d",deviceId).addValue("request",requestId==null?null:requestId.getBytes(StandardCharsets.UTF_8)).addValue("code",code);
        return optional("SELECT feedback_snapshot FROM cleaning_operation_audit WHERE device_id=:d AND "+(code!=null?"operation_code=:code":"request_id=:request")+" AND feedback_snapshot IS NOT NULL",args,CleaningCommandStatusBo.class);
    }
    private <T>List<T> list(String sql,MapSqlParameterSource args,Class<T> type) {
        return jdbc.query(sql,args,(rs,n)->read(rs.getString(1),type));
    }
    @Override @Transactional public ExportTaskBo createExport(ExportTaskBo task) {
        String system=system(task.deviceId());
        Long owner=jdbc.queryForObject("SELECT id FROM t_user WHERE username=:name AND deleted=0 AND status=1",p().addValue("name",task.operatorId()),Long.class);
        byte[] fingerprint;
        try {fingerprint=MessageDigest.getInstance("SHA-256").digest(json(List.of(task.exportType(),task.unit(),task.filters())).getBytes(StandardCharsets.UTF_8));}
        catch(Exception failure){throw new IllegalStateException(failure);}
        var args=p().addValue("s",system).addValue("owner",owner).addValue("request",task.requestId().getBytes(StandardCharsets.UTF_8));
        jdbc.queryForObject("SELECT system_id FROM cleaning_system WHERE system_id=:s FOR UPDATE",args,String.class);
        var previous=list("SELECT runtime_snapshot FROM cleaning_export_task WHERE system_id=:s AND creator_user_id=:owner AND request_id=:request",args,ExportTaskBo.class);
        if(!previous.isEmpty()) {
            byte[] old=jdbc.queryForObject("SELECT request_hash FROM cleaning_export_task WHERE system_id=:s AND creator_user_id=:owner AND request_id=:request",args,byte[].class);
            if(!MessageDigest.isEqual(old,fingerprint))throw new IllegalArgumentException("导出请求编号已经用于其他条件");
            return previous.get(0);
        }
        Long queued=jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_export_task WHERE system_id=:s AND status IN ('QUEUED','RUNNING')",args,Long.class);
        if(queued!=null&&queued>=32)throw new com.company.project.domain.exception.PageApiException(429,"EXPORT_QUEUE_FULL","导出队列已满");
        ExportTaskBo saved=task.toBuilder().systemId(system).creatorUserId(owner).build();
        jdbc.update("INSERT INTO cleaning_export_task(task_code,request_id,request_hash,creator_user_id,system_id,export_type,filter_snapshot,unit,column_version,status,phase,runtime_snapshot,created_at,updated_at) "
                +"VALUES(:code,:request,:hash,:owner,:s,:type,:filters,:unit,'M106_EXPORT_V1','QUEUED','QUEUED',:payload,:now,:now)",
                args.addValue("code",saved.taskCode()).addValue("hash",fingerprint).addValue("type",saved.exportType())
                        .addValue("filters",json(saved.filters())).addValue("unit",saved.unit()).addValue("payload",json(saved)).addValue("now",utc(saved.createdAt())));
        return saved;
    }
    @Override public Page<ExportTaskBo> exports(String deviceId,String operator,String type,String state,int offset,int limit) {
        var args=p().addValue("d",deviceId).addValue("operator",operator).addValue("type",type).addValue("state",state).addValue("offset",offset).addValue("limit",limit);
        String from=" FROM cleaning_export_task e JOIN cleaning_system s ON s.system_id=e.system_id JOIN t_user u ON u.id=e.creator_user_id WHERE s.device_id=:d AND u.username=:operator AND u.deleted=0 AND e.runtime_snapshot IS NOT NULL";
        if(type!=null)from+=" AND e.export_type=:type";if(state!=null)from+=" AND e.status=:state";
        return new Page<>(list("SELECT e.runtime_snapshot"+from+" ORDER BY e.created_at DESC,e.id DESC LIMIT :limit OFFSET :offset",args,ExportTaskBo.class),
                jdbc.queryForObject("SELECT COUNT(*)"+from,args,Long.class));
    }
    @Override public Optional<ExportTaskBo> export(String deviceId,String operator,String code) {
        return optional("SELECT e.runtime_snapshot FROM cleaning_export_task e JOIN cleaning_system s ON s.system_id=e.system_id JOIN t_user u ON u.id=e.creator_user_id WHERE s.device_id=:d AND u.username=:operator AND u.deleted=0 AND e.task_code=:code AND e.runtime_snapshot IS NOT NULL",
                p().addValue("d",deviceId).addValue("operator",operator).addValue("code",code),ExportTaskBo.class);
    }
    @Override @Transactional public Optional<ExportTaskBo> claimExport(String storeId) {
        var tasks=list("SELECT runtime_snapshot FROM cleaning_export_task WHERE status='QUEUED' AND runtime_snapshot IS NOT NULL ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",p(),ExportTaskBo.class);
        if(tasks.isEmpty())return Optional.empty();
        ExportTaskBo task=tasks.get(0).toBuilder().state("RUNNING").phase("PREPARING").startedAt(Instant.now()).storeId(storeId).build();
        updateExport(task);return Optional.of(task);
    }
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateExport(ExportTaskBo task) {
        jdbc.update("UPDATE cleaning_export_task SET status=:state,phase=:phase,snapshot_at=:snapshot,started_at=:started,finished_at=:finished,processed_rows=:processed,output_rows=:rows,"
                +"total_size_bytes=:size,file_manifest=:files,runtime_snapshot=:payload,store_id=:store,error_code=:error,error_message=:message,updated_at=:now WHERE task_code=:code",
                p().addValue("state",task.state()).addValue("phase",task.phase()).addValue("snapshot",utc(task.snapshotAt())).addValue("started",utc(task.startedAt()))
                        .addValue("finished",utc(task.finishedAt())).addValue("processed",task.processedRows()).addValue("rows",task.outputRows())
                        .addValue("size",task.files().stream().mapToLong(ExportTaskBo.File::sizeBytes).sum()).addValue("files",json(task.files()))
                        .addValue("payload",json(task)).addValue("store",task.storeId()).addValue("error",task.errorCode()).addValue("message",task.errorMessage())
                        .addValue("now",utc(Instant.now())).addValue("code",task.taskCode()));
    }
    @Override public void recoverExports(String storeId,Instant now) {
        var tasks=list("SELECT runtime_snapshot FROM cleaning_export_task WHERE status='RUNNING' AND store_id=:store AND runtime_snapshot IS NOT NULL",p().addValue("store",storeId),ExportTaskBo.class);
        for(var task:tasks)updateExport(task.toBuilder().state("FAILED").phase("FAILED").finishedAt(now).files(List.of()).errorCode("BACKEND_RESTART").errorMessage("后台重启，未发布文件，请重新创建导出").build());
    }
    @Override @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public void exportSnapshot(Runnable action) {
        jdbc.queryForList("SELECT id FROM cleaning_run_record ORDER BY id LIMIT 1",Map.of(),Long.class);
        action.run();
    }
    @Override public Optional<List<Height>> runHeightCache(String deviceId,String recordCode) {
        var rows=jdbc.queryForList("SELECT height_snapshot FROM cleaning_run_record WHERE device_id=:d AND record_code=:code AND state='ENDED' AND height_snapshot IS NOT NULL AND selection_rule_version=:rule",
                p().addValue("d",deviceId).addValue("code",recordCode).addValue("rule",RULE),String.class);
        if(rows.isEmpty())return Optional.empty();
        try{return Optional.of(mapper.readValue(rows.get(0),mapper.getTypeFactory().constructCollectionType(List.class,Height.class)));}
        catch(Exception invalid){throw new IllegalStateException("单轮高度摘要无法读取",invalid);}
    }
    @Override public void cacheRunHeights(String deviceId,String recordCode,long version,List<Height> values) {
        jdbc.update("UPDATE cleaning_run_record SET height_snapshot=:payload WHERE device_id=:d AND record_code=:code AND state='ENDED' AND summary_version=:version AND height_snapshot IS NULL",
                p().addValue("d",deviceId).addValue("code",recordCode).addValue("version",version).addValue("payload",json(values)));
    }
    @Override public boolean statisticsReady(String deviceId,String type,Instant from,Instant to) {
        Long waiting=jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_period_summary p JOIN cleaning_system s ON s.system_id=p.system_id WHERE s.device_id=:d AND p.scope_kind='SYSTEM' AND p.period_type=:type AND p.period_start>=:from AND p.period_start<:to AND p.needs_rebuild=1",
                p().addValue("d",deviceId).addValue("type",type).addValue("from",utc(from)).addValue("to",utc(to)),Long.class);
        return waiting==null||waiting==0;
    }
    private <T>Optional<T> optional(String sql,MapSqlParameterSource args,Class<T> type) {
        return list(sql,args,type).stream().findFirst();
    }
    private static byte[] bytes(String uuid) {
        UUID id=UUID.fromString(uuid);return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
