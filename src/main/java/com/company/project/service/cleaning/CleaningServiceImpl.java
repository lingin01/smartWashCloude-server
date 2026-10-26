package com.company.project.service.cleaning;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.*;
import com.company.project.domain.param.cleaning.*;
import com.company.project.repository.device.DeviceManager;
import com.company.project.service.*;
import org.springframework.stereotype.Service;
import java.util.Optional;
/** 清洗编排入口：状态、操作规则、命令和统计各自委派，不在门面展开算法。 */
@Service
public class CleaningServiceImpl implements CleaningService {
    private final DeviceManager devices;
    private final CleaningCommandService commands;
    private final CleaningStatisticsService statistics;
    private final com.company.project.repository.device.PageSystemRegistry pageSystems;
    private final CleaningSystemState state=new CleaningSystemState();
    private final CleaningOperationPlanner operations=new CleaningOperationPlanner();
    private final CleaningDeviceConverter converter=new CleaningDeviceConverter();
    private final RunHistoryTracker runs=new RunHistoryTracker();
    private final java.util.Set<String> restoredDevices=new java.util.HashSet<>();
    public CleaningServiceImpl(DeviceManager devices,CleaningCommandService commands,CleaningStatisticsService statistics) {
        this(devices, commands, statistics, new com.company.project.repository.device.PageSystemRegistry(
                new com.company.project.config.PageAccessProperties()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CleaningServiceImpl(DeviceManager devices, CleaningCommandService commands, CleaningStatisticsService statistics,
            com.company.project.repository.device.PageSystemRegistry pageSystems) {
        this.devices=devices;this.commands=commands;this.statistics=statistics;this.pageSystems=pageSystems;
    }
    @jakarta.annotation.PostConstruct public void restore() {
        for(var system:pageSystems.all()) {
            restoreDevice(system.deviceId());
        }
    }
    private void restoreDevice(String device) {
        if(!restoredDevices.add(device))return;
        statistics.restoredState(device).ifPresent(state::restore);
        runs.restoreEvents(device,statistics.activeEvents(device));
    }
    @Override public synchronized void acceptDeviceSnapshot(PlcDeviceSnapshotBo snapshot) {
        restoreDevice(snapshot.deviceId());
        var previous=state.latest(snapshot.deviceId()).orElse(null);
        if(previous!=null && !state.isRestored(snapshot.deviceId()) && snapshot.sequence()<=previous.sequence()) return;
        devices.recordSnapshot(snapshot);
        var configured=devices.activeDevice(snapshot.deviceId()).orElse(null);
        var configuration=statistics.runtimeConfiguration(snapshot.deviceId());
        var calibration=calibration(configured,configuration);
        var sample=runs.pending(snapshot.deviceId(),snapshot.sequence());
        if(sample==null)sample=converter.toRealtime(snapshot,previous,calibration).withCaptureId(runs.captureId());
        if(statistics.runtimeEnabled())runs.accept(snapshot,sample,calibration,configuration,statistics::acceptRuntimeWrite);
        if (state.update(sample)) statistics.acceptSample(sample);
    }
    private com.company.project.config.PlcAccessProperties.Device calibration(
            com.company.project.config.PlcAccessProperties.Device configured,java.util.Map<String,Object> saved) {
        if(saved.isEmpty())return configured;
        var view=new com.company.project.config.PlcAccessProperties.Device();
        if(configured!=null) {
            view.setEncoderBottomCounts(new java.util.HashMap<>(configured.getEncoderBottomCounts()));
            view.setHeightMin(configured.getHeightMin());view.setHeightMax(configured.getHeightMax());
        }
        if(saved.get("encoderBottomCounts") instanceof java.util.Map<?,?> counts)
            counts.forEach((k,v)->view.getEncoderBottomCounts().put(Integer.parseInt(k.toString()),new java.math.BigDecimal(v.toString())));
        if(saved.get("heightMin")!=null)view.setHeightMin(new java.math.BigDecimal(saved.get("heightMin").toString()));
        if(saved.get("heightMax")!=null)view.setHeightMax(new java.math.BigDecimal(saved.get("heightMax").toString()));
        return view;
    }
    @Override public void acceptDeviceWriteResult(PlcOperationResultBo result) {
        var status=commands.acceptDeviceResult(result);
        if (result.success() && result.operation().settings()!=null
                && "PLC_SETTINGS_WRITTEN_STORAGE_PENDING".equals(status.code())) state.recordWrittenSettings(result);
    }
    @Override public CleaningCommandStatusBo emergencyStop(EmergencyStopParam request) { return commands.submit(operations.emergencyStop(request)); }
    @Override public CleaningCommandStatusBo updateSettings(SettingsUpdateParam request) { return commands.submit(operations.updateSettings(request)); }
    @Override public Optional<TelemetrySample> latest(String deviceId) { return state.latest(deviceId); }
    @Override public CleaningSettingsViewBo settings(String deviceId) { return state.settings(deviceId); }
    @Override public Optional<CleaningCommandStatusBo> operation(String deviceId,String requestId) { return commands.find(deviceId,requestId); }
    @Override public Optional<CleaningCommandStatusBo> operationByCommandCode(String deviceId, String commandCode) {
        return commands.findByCommandCode(deviceId, commandCode);
    }
    @Override public com.company.project.domain.bo.page.PageSystemBo pageSystem(String systemCode) {
        if (pageSystems.all().isEmpty())
            throw new com.company.project.domain.exception.PageApiException(503, "SYSTEM_MAPPING_NOT_READY", "页面系统公开编号尚未配置");
        return pageSystems.find(systemCode).orElseThrow(() -> new com.company.project.domain.exception.PageApiException(
                404, "SYSTEM_NOT_FOUND", "系统不存在"));
    }
    @Override public java.util.List<com.company.project.domain.bo.page.PageSystemBo> pageSystems() { return pageSystems.all(); }
    @Override public StoredSettingsBo saveStoredSettings(CleaningOperationContext context,long version,CleaningSettingsBo value) {
        var command=operations.updateSettings(new SettingsUpdateParam(context,version,value));
        return statistics.saveSettings(command,version,value);
    }
    @Override public StoredThresholdsBo saveStoredThresholds(CleaningOperationContext context,String winchId,long version,
            java.util.List<StoredThresholdsBo.Value> values) {
        var now=java.time.Instant.now();
        var command=new CleaningCommand(java.util.UUID.randomUUID().toString(),context.deviceId(),"save_tension_thresholds",
                java.util.Map.of("winchId",winchId,"expectedVersion",version,"thresholds",values),now,context.requestId(),context.operatorId(),now.plusSeconds(30));
        return statistics.saveThresholds(command,winchId,version,values);
    }
}
