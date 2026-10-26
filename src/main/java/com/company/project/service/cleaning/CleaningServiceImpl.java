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
    public CleaningServiceImpl(DeviceManager devices,CleaningCommandService commands,CleaningStatisticsService statistics) {
        this(devices, commands, statistics, new com.company.project.repository.device.PageSystemRegistry(
                new com.company.project.config.PageAccessProperties()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CleaningServiceImpl(DeviceManager devices, CleaningCommandService commands, CleaningStatisticsService statistics,
            com.company.project.repository.device.PageSystemRegistry pageSystems) {
        this.devices=devices;this.commands=commands;this.statistics=statistics;this.pageSystems=pageSystems;
    }
    @Override public void acceptDeviceSnapshot(PlcDeviceSnapshotBo snapshot) {
        devices.recordSnapshot(snapshot);
        var sample=converter.toRealtime(snapshot,state.latest(snapshot.deviceId()).orElse(null));
        if (state.update(sample)) statistics.acceptSample(sample);
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
}
