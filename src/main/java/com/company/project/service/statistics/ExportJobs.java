package com.company.project.service.statistics;

import com.company.project.domain.bo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.exception.PageApiException;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.repository.cleaning.RuntimeHistoryDao;
import com.company.project.repository.device.PageSystemRegistry;
import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static java.nio.file.StandardOpenOption.*;

/** 单实例受限导出器：一致数据库快照、逐批输出、完成后原子发布。 */
final class ExportJobs implements AutoCloseable {
    private final RuntimeHistoryDao dao;
    private final PageSystemRegistry systems;
    private final Path root;
    private final String storeId;
    private final FileChannel lockChannel;
    private final FileLock lock;
    ExportJobs(RuntimeHistoryDao dao,PageSystemRegistry systems,String directory) {
        this.dao=dao;this.systems=systems;
        try {
            root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);
            lockChannel=FileChannel.open(root.resolve(".lock"),CREATE,WRITE);lock=lockChannel.tryLock();
            if(lock==null){lockChannel.close();throw new IOException("导出目录已被其他后端占用");}
            Path identity=root.resolve(".store-id");
            if(!Files.exists(identity))Files.writeString(identity,UUID.randomUUID().toString(),StandardCharsets.US_ASCII,CREATE_NEW);
            storeId=UUID.fromString(Files.readString(identity,StandardCharsets.US_ASCII).trim()).toString();
        } catch(IOException failure){throw new IllegalStateException("导出文件存储不可用",failure);}
    }
    void initialize() { dao.recoverExports(storeId,Instant.now()); }
    ExportAccess access(CleaningOperationContext context) {
        var system=systems.all().stream().filter(s->s.deviceId().equals(context.deviceId())).findFirst()
                .orElseThrow(()->new PageApiException(503,"SYSTEM_MAPPING_NOT_READY","系统档案未配置"));
        return new ExportAccess() {
            public ExportTaskBo create(String request,String type,String unit,ExportTaskBo.Selection filters) {
                requirePermission(context,type);
                if(!Set.of("RUN_LIST","RUN_SAMPLES","EXCEPTIONS","PERIOD_STATISTICS").contains(type))throw new IllegalArgumentException("导出类型不支持");
                if(!Set.of("t","kg").contains(unit))throw new IllegalArgumentException("导出单位不支持");
                if(filters.motionRole()!=null&&!Set.of("ALL_UP","PRODUCTION_UP").contains(filters.motionRole()))
                    throw new PageApiException(400,"MOTION_ROLE_NOT_SUPPORTED","当前PLC未提供恢复/检修上行角色，历史仅保存自动上行");
                if("RUN_LIST".equals(type)&&(filters.direction()!=null||filters.motionRole()!=null))
                    throw new PageApiException(400,"FILTER_NOT_SUPPORTED","运行列表按整轮结果筛选；方向及上行角色用于采样导出");
                if(Set.of("RUN_LIST","RUN_SAMPLES").contains(type)&&(filters.levels()!=null||filters.metric()!=null||filters.tensionMetric()!=null||filters.period()!=null)
                        ||"EXCEPTIONS".equals(type)&&(filters.runResult()!=null||filters.motionRole()!=null||filters.metric()!=null||filters.tensionMetric()!=null||filters.period()!=null)
                        ||"PERIOD_STATISTICS".equals(type)&&(filters.runResult()!=null||filters.direction()!=null||filters.motionRole()!=null||filters.levels()!=null))
                    throw new PageApiException(400,"FILTER_NOT_SUPPORTED","导出筛选条件不适用于当前导出类型");
                if(filters.recordCode()!=null)requireRun(context.deviceId(),filters.recordCode());
                for(String code:filters.recordCodes())requireRun(context.deviceId(),code);
                for(String code:filters.eventCodes())dao.event(context.deviceId(),code).orElseThrow(()->missing());
                return dao.createExport(ExportTaskBo.builder().taskCode("EXP-"+UUID.randomUUID()).requestId(request)
                        .systemCode(system.systemCode()).deviceId(context.deviceId()).operatorId(context.operatorId())
                        .exportType(type).unit(unit).filters(filters).state("QUEUED").phase("QUEUED").createdAt(Instant.now()).build());
            }
            public Page<ExportTaskBo> list(String type,String state,int offset,int limit) {
                if(type!=null)requirePermission(context,type);
                return dao.exports(context.deviceId(),context.operatorId(),type,state,offset,limit);
            }
            public ExportTaskBo task(String code) {
                var task=dao.export(context.deviceId(),context.operatorId(),code).orElseThrow(ExportJobs::missing);
                requirePermission(context,task.exportType());return task;
            }
            public Download download(String task,String file) {
                var value=task(task);
                if(!"SUCCESS".equals(value.state()))throw new PageApiException(409,"EXPORT_NOT_FINISHED","文件尚未完整生成");
                var info=value.files().stream().filter(f->f.fileCode().equals(file)).findFirst().orElseThrow(ExportJobs::missing);
                try {
                    Path path=root.resolve(info.relativePath()).normalize();
                    if(!path.startsWith(root)||!path.toRealPath().startsWith(root.toRealPath())||!Files.isRegularFile(path))
                        throw missing();
                    if(Files.size(path)!=info.sizeBytes())throw new PageApiException(409,"EXPORT_FILE_CHANGED","导出文件与发布信息不一致");
                    return new Download(path,info.fileName(),info.mediaType(),info.sizeBytes());
                }catch(IOException unavailable){throw missing();}
            }
        };
    }
    private static void requirePermission(CleaningOperationContext context,String type) {
        String permission="EXCEPTIONS".equals(type)?"alarm:read":"device:read";
        if(!context.permissions().contains(permission))throw new org.springframework.security.access.AccessDeniedException("没有导出权限");
    }
    private Run requireRun(String device,String code) {return dao.run(device,code).orElseThrow(ExportJobs::missing);}
    private static PageApiException missing(){return new PageApiException(404,"EXPORT_NOT_FOUND","任务、文件或源记录不存在或不属于当前账号/系统");}
    synchronized void cycle() {
        var claimed=dao.claimExport(storeId);if(claimed.isEmpty())return;
        ExportTaskBo task=claimed.get().toBuilder().snapshotAt(Instant.now()).phase("GENERATING").build();
        boolean zipped="PERIOD_STATISTICS".equals(task.exportType());
        String extension=zipped?".zip":".csv",relative=task.taskCode()+extension;
        Path partial=root.resolve(relative+".part"),published=root.resolve(relative);
        long[] counts={0,0};
        List<ExportTaskBo.TableCount> tables=new ArrayList<>();
        try {
            dao.updateExport(task);
            dao.exportSnapshot(()->{
                try {
                    if(zipped) {
                        try(var stream=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(partial,CREATE,TRUNCATE_EXISTING)),StandardCharsets.UTF_8)) {
                            stream.putNextEntry(new ZipEntry("period-summary.csv"));
                            long summary=writePeriods(task,new Csv(stream),false);stream.closeEntry();
                            stream.putNextEntry(new ZipEntry("height-statistics.csv"));
                            long heights=writePeriods(task,new Csv(stream),true);stream.closeEntry();
                            counts[0]=summary+heights;counts[1]=counts[0];
                            tables.add(new ExportTaskBo.TableCount("period-summary.csv","PERIOD_SCOPE",summary));
                            tables.add(new ExportTaskBo.TableCount("height-statistics.csv","PERIOD_HEIGHT_SOURCE",heights));
                        }
                    } else try(var stream=new BufferedOutputStream(Files.newOutputStream(partial,CREATE,TRUNCATE_EXISTING))) {
                        var csv=new Csv(stream);
                        counts[0]=switch(task.exportType()) {
                            case "RUN_LIST"->writeRuns(task,csv);
                            case "RUN_SAMPLES"->writeSamples(task,csv);
                            case "EXCEPTIONS"->writeEvents(task,csv);
                            default->throw new IllegalArgumentException("导出类型不支持");
                        };
                        counts[1]=counts[0];tables.add(new ExportTaskBo.TableCount("data.csv",task.exportType(),counts[0]));
                    }
                }catch(IOException failure){throw new UncheckedIOException(failure);}
            });
            try{Files.move(partial,published,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(partial,published);}
            var info=new ExportTaskBo.File(UUID.randomUUID().toString(),task.systemCode()+"-"+task.exportType()+"-"+task.taskCode()+extension,
                    relative,zipped?"application/zip":"text/csv",Files.size(published),counts[1],zipped?"MULTIPLE_TABLES":task.exportType());
            dao.updateExport(task.toBuilder().state("SUCCESS").phase("PUBLISHED").finishedAt(Instant.now()).processedRows(counts[0])
                    .outputRows(counts[1]).files(List.of(info)).tableCounts(tables).build());
        } catch(StatisticsPending pending) {
            try{Files.deleteIfExists(partial);}catch(IOException ignored){ }
            dao.updateExport(task.toBuilder().state("QUEUED").phase("WAITING_STATISTICS").build());
        } catch(Exception failure) {
            try{Files.deleteIfExists(partial);}catch(IOException ignored){ }
            dao.updateExport(task.toBuilder().state("FAILED").phase("FAILED").finishedAt(Instant.now()).processedRows(counts[0])
                    .outputRows(0).files(List.of()).errorCode("EXPORT_FAILED").errorMessage("导出未完成，请核对数据及存储后重新创建任务").build());
            org.slf4j.LoggerFactory.getLogger(ExportJobs.class).error("导出任务失败 {}",task.taskCode(),failure);
        }
    }
    private List<Run> selectedRuns(ExportTaskBo task,int offset,int limit) {
        var filter=task.filters();
        if(filter.recordCode()!=null)return offset==0?java.util.stream.Stream.of(requireRun(task.deviceId(),filter.recordCode())).filter(r->matches(r,filter.runResult())).toList():List.of();
        if(!filter.recordCodes().isEmpty())return filter.recordCodes().stream().skip(offset).limit(limit).map(c->requireRun(task.deviceId(),c)).filter(r->matches(r,filter.runResult())).toList();
        if(filter.startTime()==null||filter.endTime()==null)throw new IllegalArgumentException("缺少明确的运行时间范围");
        return dao.runs(task.deviceId(),filter.startTime(),filter.endTime(),filter.runResult(),offset,limit).entries();
    }
    private static boolean matches(Run run,String result) {
        return result==null||"COMPLETED".equals(result)&&Set.of("NORMAL_COMPLETED","ABNORMAL_COMPLETED").contains(run.result()==null?"":run.result())
                ||Set.of("INCOMPLETE","FAULT_TERMINATED").contains(result)&&"ABNORMAL_ENDED".equals(run.result())||result.equals(run.result());
    }
    private String scopeCode(ExportTaskBo task,String scope) {
        var system=systems.find(task.systemCode()).orElseThrow();
        return system.winches().stream().filter(w->w.winchId().equals(scope)).map(w->w.winchCode()).findFirst().orElse(system.systemCode());
    }
    private long writeRuns(ExportTaskBo task,Csv csv) throws IOException {
        csv.row("systemCode","recordCode","startedAt","endedAt","result","endReason","durationSeconds","systemForceMinimum","systemForceMaximum","forceUnit");
        long rows=0;
        for(int offset=0;;offset+=500) {
            var records=selectedRuns(task,offset,500);
            for(var run:records) {
                var metric=run.measures().get("SYSTEM/ROPE_TOTAL");
                csv.row(task.systemCode(),run.recordCode(),run.startedAt(),run.endedAt(),run.result(),run.endReason(),
                        run.endedAt()==null?null:Duration.between(run.startedAt(),run.endedAt()).getSeconds(),
                        metric==null?null:force(metric.minimum(),task.unit()),metric==null?null:force(metric.maximum(),task.unit()),task.unit());
                rows++;
            }
            if(records.size()<500)break;
        }
        return rows;
    }
    private long writeSamples(ExportTaskBo task,Csv csv) throws IOException {
        List<Object> header=new ArrayList<>(List.of("systemCode","recordCode","sampleKey","sequence","sampledAt","systemTotalForce","forceUnit"));
        for(int i=1;i<=4;i++)for(String field:List.of("winchCode","heightNode","targetHeight","actualHeight","direction","encoderRaw","axisSeconds","sensor1Code","sensor1Raw","sensor1Force",
                "sensor2Code","sensor2Raw","sensor2Force","totalForce","motorRaw","motorEquivalentForce","speedMPerMinute"))header.add("w"+i+"_"+field);
        header.add("systemState");header.add("M106");header.add("M60");header.add("M61");header.add("faultCode");
        csv.row(header.toArray());long count=0;
        var filter=task.filters();
        if(filter.startTime()!=null&&filter.recordCode()==null&&filter.recordCodes().isEmpty()) {
            for(int offset=0;;offset+=500) {
                var frames=dao.framesByTime(task.deviceId(),filter.startTime(),filter.endTime(),filter.runResult(),offset,500);
                for(var frame:frames)if(writeFrame(task,frame,csv))count++;
                if(frames.size()<500)break;
            }
        } else for(int offset=0;;offset+=500) {
            var runs=selectedRuns(task,offset,500);for(var run:runs)count+=writeRunSamples(task,run,csv,false);if(runs.size()<500)break;
        }
        return count;
    }
    private long writeRunSamples(ExportTaskBo task,Run run,Csv csv,boolean timeFilter) throws IOException {
        long count=0;
        for(int offset=0;;offset+=500) {
            var frames=dao.frames(task.deviceId(),run.recordCode(),offset,500);
            for(var frame:frames) {
                if(timeFilter&&(frame.sampledAt().isBefore(task.filters().startTime())||!frame.sampledAt().isBefore(task.filters().endTime())))continue;
                if(writeFrame(task,frame,csv))count++;
            }
            if(frames.size()<500)break;
        }
        return count;
    }
    private boolean writeFrame(ExportTaskBo task,SavedFrame frame,Csv csv)throws IOException {
        var f=task.filters();
        boolean matching=false;
        for(var w:frame.sample().winches())if((f.scopeId()==null||f.scopeId().equals(w.winchId()))
                &&(f.direction()==null||f.direction().equals(w.direction()))&&(f.motionRole()==null||"UP".equals(w.direction())))matching=true;
        if(!matching)return false;
        var system=systems.find(task.systemCode()).orElseThrow();
        List<Object> row=new ArrayList<>(Arrays.asList(task.systemCode(),frame.recordCode(),frame.sampleKey(),frame.sequence(),frame.sampledAt(),force(frame.sample().systemTotalTension(),task.unit()),task.unit()));
        for(int i=1;i<=4;i++) {
            var w=frame.sample().winches().get(i-1);var selected=frame.selections().get(i);
            final int slot=i;
            var identity=system.winches().stream().filter(v->v.groupNo()==slot).findFirst().orElseThrow();
            String sensor1=identity.sensors().stream().filter(s->s.ropeNo()==1).findFirst().orElseThrow().sensorCode();
            String sensor2=identity.sensors().stream().filter(s->s.ropeNo()==2).findFirst().orElseThrow().sensorCode();
            row.addAll(Arrays.asList(identity.winchCode(),selected!=null&&selected.heightNode(),selected==null?null:selected.targetHeight(),w.heightMeters(),
                    w.direction(),raw(frame,"encoderCount",i),raw(frame,"axisRunSeconds",i),sensor1,raw(frame,"ropeTension",(i-1)*2+1),
                    force(frame.sample().ropes().get((i-1)*2).tension(),task.unit()),sensor2,raw(frame,"ropeTension",(i-1)*2+2),
                    force(frame.sample().ropes().get((i-1)*2+1).tension(),task.unit()),force(w.totalTension(),task.unit()),
                    raw(frame,"winchTorque",i),force(w.torqueKg(),task.unit()),w.speed()));
        }
        row.add(raw(frame,"systemState",1));row.add(raw(frame,"automaticCycleActive",1));row.add(raw(frame,"cycleAbnormal",1));
        row.add(raw(frame,"cycleAbnormalEnd",1));row.add(raw(frame,"faultCode",1));csv.row(row.toArray());return true;
    }
    private long writeEvents(ExportTaskBo task,Csv csv) throws IOException {
        csv.row("systemCode","eventCode","recordCode","winchSlot","part","faultCode","severity","content","firstObservedAt","endedAt","direction","driverErrors");
        long rows=0;var filter=task.filters();
        boolean levels=filter.levels()!=null&&!filter.levels().isBlank();
        if(levels&&!Arrays.asList(filter.levels().split(",")).contains("SERIOUS"))return 0;
        for(int offset=0;;offset+=500) {
            List<Event> values=filter.eventCodes().isEmpty()
                    ?dao.events(task.deviceId(),filter.startTime(),filter.endTime(),levels?true:null,filter.winchSlot(),null,filter.direction(),offset,500).entries()
                    :filter.eventCodes().stream().skip(offset).limit(500).map(c->dao.event(task.deviceId(),c).orElseThrow(ExportJobs::missing)).toList();
            for(var event:values) {
                if(levels&&!event.alarm()||filter.direction()!=null&&!filter.direction().equals(event.direction())||filter.winchSlot()!=null&&filter.winchSlot()!=event.winchSlot())continue;
                csv.row(task.systemCode(),event.eventCode(),event.recordCode(),event.winchSlot(),event.part(),event.faultCode(),event.severity(),event.content(),event.firstObservedAt(),event.endedAt(),event.direction(),event.faultDetails());rows++;
            }
            if(values.size()<500)break;
        }
        return rows;
    }
    private long writePeriods(ExportTaskBo task,Csv csv,boolean height) throws IOException {
        var f=task.filters();String type="WEEK".equals(f.period())?"DAY":f.period();
        Instant from=f.startDate().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),to=f.endDate().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        if(!dao.statisticsReady(task.deviceId(),type,from,to)||!dao.statisticsReady(task.deviceId(),"DAY",from,to))throw new StatisticsPending();
        List<String> scopes=new ArrayList<>();
        if(f.scopeId()!=null)scopes.add(f.scopeId());
        else {scopes.add(null);systems.find(task.systemCode()).orElseThrow().winches().forEach(w->scopes.add(w.winchId()));}
        long count=0;
        if(height)csv.row("periodType","periodStart","scopeCode","source","height","average","minimum","maximum","contributingRuns","unit");
        else csv.row("periodType","periodStart","periodEnd","scopeCode","started","normalCompleted","abnormalCompleted","abnormalEnded","running","version","computedAt");
        for(String scope:scopes)for(var period:dao.periods(task.deviceId(),type,from,to,scope)) {
            if(height)for(var value:dao.heights(task.deviceId(),type,period.start(),scope)) {
                boolean speed="SPEED".equals(value.source());
                if(f.metric()!=null&&speed!="SPEED".equals(f.metric()))continue;
                String source="WINCH_TENSION".equals(f.tensionMetric())?"ROPE1":"TRACTION_ROPE_TENSION".equals(f.tensionMetric())?"ROPE2":"ROPE_TOTAL";
                if(f.tensionMetric()!=null&&!source.equals(value.source()))continue;
                csv.row(type,period.start(),scopeCode(task,value.scopeId()),value.source(),value.targetHeight(),speed?value.value().average():force(value.value().average(),task.unit()),
                        speed?value.value().minimum():force(value.value().minimum(),task.unit()),speed?value.value().maximum():force(value.value().maximum(),task.unit()),value.contributingRuns(),speed?"m/min":task.unit());count++;
            } else {
                csv.row(type,period.start(),period.end(),scopeCode(task,period.scopeId()),period.started(),period.normalCompleted(),period.abnormalCompleted(),period.abnormalEnded(),period.running(),period.version(),period.computedAt());count++;
            }
        }
        return count;
    }
    private static final class StatisticsPending extends RuntimeException { }
    private static Object raw(SavedFrame frame,String field,int index) {
        return frame.raw().points().stream().filter(p->p.field().equals(field)&&p.index()==index)
                .map(p->p.rawValue()!=null?p.rawValue():Set.of("ropeTension","winchTorque").contains(field)&&"kg".equals(p.unit())?null:p.value())
                .filter(Objects::nonNull).findFirst().orElse(null);
    }
    private static BigDecimal force(BigDecimal value,String unit){return value==null?null:"t".equals(unit)?value.movePointLeft(3):value;}
    private static final class Csv {
        private final OutputStream output;
        Csv(OutputStream output)throws IOException{this.output=output;output.write(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf});}
        void row(Object...values)throws IOException {
            StringJoiner row=new StringJoiner(",");
            for(Object value:values) {
                String text=value==null?"":value.toString();
                if(value instanceof String&&!text.isEmpty()&&"=+-@\t\r".indexOf(text.charAt(0))>=0)text="'"+text;
                row.add("\""+text.replace("\"","\"\"")+"\"");
            }
            output.write((row+"\r\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    @Override public void close() throws IOException {if(lock.isValid())lock.release();lockChannel.close();}
}
