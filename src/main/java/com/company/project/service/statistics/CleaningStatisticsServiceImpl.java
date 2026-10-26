package com.company.project.service.statistics;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.statistics.CleaningStatisticsBo;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
import com.company.project.info.buffer.cleaning.TelemetryPersistenceBuffer;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.service.CleaningStatisticsService;
import org.springframework.stereotype.Service;
/** 统计门面只委派：样本累计与历史事务保存由各自辅助类承担。 */
@Service
public class CleaningStatisticsServiceImpl implements CleaningStatisticsService,AutoCloseable {
    private final SampleStatistics samples=new SampleStatistics();
    private final HistoryBatchPersistence history;
    private RuntimeHistory runtime;
    public CleaningStatisticsServiceImpl(TelemetryPersistenceBuffer buffer,TelemetryHistoryDao dao){history=new HistoryBatchPersistence(buffer,dao);}
    @org.springframework.beans.factory.annotation.Autowired
    public CleaningStatisticsServiceImpl(TelemetryPersistenceBuffer buffer,TelemetryHistoryDao dao,
            org.springframework.beans.factory.ObjectProvider<com.company.project.repository.cleaning.RuntimeHistoryDao> store,
            org.springframework.beans.factory.ObjectProvider<com.company.project.info.buffer.cleaning.DurableHistoryInbox> inbox,
            com.company.project.info.buffer.plc.PlcMessageBuffer messages,
            com.company.project.repository.device.PageSystemRegistry systems,
            @org.springframework.beans.factory.annotation.Value("${cleaning.exports.directory:var/exports}") String exportDirectory) {
        this(buffer,dao);
        if(store.getIfAvailable()!=null && inbox.getIfAvailable()!=null)
            runtime=new RuntimeHistory(store.getObject(),inbox.getObject(),messages,systems,exportDirectory);
    }
    @jakarta.annotation.PostConstruct public void initialize() { if(runtime!=null)runtime.initialize(); }
    @Override public void acceptSample(TelemetrySample sample){samples.accept(sample);}
    @Override public CleaningStatisticsBo statistics(String deviceId){return samples.snapshot(deviceId);}
    @Override public void acceptHistoryBatch(TelemetryBatchBo batch){history.acceptHistoryBatch(batch);}
    @Override public void runPersistenceCycle(){if(runtime!=null)runtime.persist(40);history.runPersistenceCycle();}
    @Override public boolean runtimeEnabled(){return runtime!=null;}
    @Override public void acceptRuntimeWrite(com.company.project.domain.bo.telemetry.RunHistoryBo.Write write){if(runtime!=null)runtime.accept(write);}
    @Override public java.util.Optional<TelemetrySample> restoredState(String deviceId){return runtime==null?java.util.Optional.empty():runtime.restored(deviceId);}
    @Override public java.util.Map<String,Object> runtimeConfiguration(String deviceId){return runtime==null?java.util.Map.of():runtime.configuration(deviceId);}
    @Override public void runPeriodCycle(){if(runtime!=null)runtime.periodCycle();}
    @Override public void runExportCycle(){if(runtime!=null)runtime.exportCycle();}
    @Override public com.company.project.domain.bo.ExportAccess exports(com.company.project.domain.param.cleaning.CleaningOperationContext context) {
        if(runtime==null)return CleaningStatisticsService.super.exports(context);
        return runtime.exports(context);
    }
    @Override @jakarta.annotation.PreDestroy public void close(){if(runtime!=null)runtime.close();}
    @Override public java.util.List<com.company.project.domain.bo.telemetry.RunHistoryBo.Event> activeEvents(String deviceId) {
        return runtime==null?java.util.List.of():runtime.activeEvents(deviceId);
    }
    @Override public com.company.project.domain.bo.telemetry.HistoryReader history(String deviceId) {
        if(runtime==null)return CleaningStatisticsService.super.history(deviceId);
        return runtime.reader(deviceId);
    }
    @Override public com.company.project.domain.bo.StoredSettingsBo storedSettings(String deviceId){return runtime==null?null:runtime.settings(deviceId);}
    @Override public com.company.project.domain.bo.StoredThresholdsBo thresholds(String deviceId,String winchId){return runtime==null?null:runtime.thresholds(deviceId,winchId);}
    @Override public com.company.project.domain.bo.StoredThresholdsBo saveThresholds(com.company.project.domain.bo.CleaningCommand command,String winchId,long version,
            java.util.List<com.company.project.domain.bo.StoredThresholdsBo.Value> values) {
        if(runtime==null)return CleaningStatisticsService.super.saveThresholds(command,winchId,version,values);
        return runtime.saveThresholds(command,winchId,version,values);
    }
    @Override public com.company.project.domain.bo.StoredSettingsBo saveSettings(com.company.project.domain.bo.CleaningCommand command,long version,com.company.project.domain.bo.CleaningSettingsBo value) {
        if(runtime==null)return CleaningStatisticsService.super.saveSettings(command,version,value);
        return runtime.saveSettings(command,version,value);
    }
}
