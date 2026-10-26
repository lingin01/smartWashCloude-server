package com.company.project.service.lobby;

import com.company.project.domain.bo.CleaningCommandStatusBo;
import com.company.project.domain.bo.CleaningSettingsViewBo;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.param.cleaning.*;
import com.company.project.service.CleaningService;
import com.company.project.service.CleaningLobbyService;
import com.company.project.service.CleaningSafetyService;
import org.springframework.stereotype.Service;
import java.util.Optional;

/** 用户表示层进入大厅，先检查权限，再进入清洗业务；设备采集另走 Runtime。 */
@Service
public class CleaningLobbyServiceImpl implements CleaningLobbyService {
    private final CleaningService cleaning;
    private final CleaningSafetyService safety;
    private final com.company.project.service.CleaningStatisticsService statistics;

    public CleaningLobbyServiceImpl(CleaningService cleaning, CleaningSafetyService safety, com.company.project.service.CleaningStatisticsService statistics) {
        this.cleaning = cleaning;
        this.safety = safety;
        this.statistics = statistics;
    }

    @Override
    public CleaningCommandStatusBo emergencyStop(EmergencyStopParam request) {
        safety.verifyEmergencyStop(request.context());
        return cleaning.emergencyStop(request);
    }

    @Override
    public CleaningCommandStatusBo updateSettings(SettingsUpdateParam request) {
        safety.verifySettingsChange(request.context());
        return cleaning.updateSettings(request);
    }

    @Override
    public Optional<TelemetrySample> queryRealtime(CleaningOperationContext context) {
        safety.verifyRead(context);
        return cleaning.latest(context.deviceId());
    }

    @Override
    public CleaningSettingsViewBo querySettings(CleaningOperationContext context) {
        safety.verifyRead(context);
        return cleaning.settings(context.deviceId());
    }

    @Override
    public Optional<CleaningCommandStatusBo> queryOperation(CleaningOperationContext context, String requestId) {
        safety.verifyRead(context);
        return cleaning.operation(context.deviceId(), requestId);
    }
    @Override public Optional<CleaningCommandStatusBo> queryOperationByCommandCode(CleaningOperationContext context, String commandCode) {
        safety.verifyRead(context);
        return cleaning.operationByCommandCode(context.deviceId(), commandCode);
    }
    @Override public void acceptDeviceSnapshot(com.company.project.domain.bo.plc.PlcDeviceSnapshotBo snapshot) {
        cleaning.acceptDeviceSnapshot(snapshot);
    }
    @Override public void acceptDeviceWriteResult(com.company.project.domain.bo.plc.PlcOperationResultBo result) {
        cleaning.acceptDeviceWriteResult(result);
    }
    @Override public void acceptHistoryBatch(com.company.project.domain.bo.telemetry.TelemetryBatchBo batch) {
        statistics.acceptHistoryBatch(batch);
    }
    @Override public com.company.project.domain.bo.statistics.CleaningStatisticsBo queryStatistics(CleaningOperationContext context) {
        safety.verifyRead(context);
        return statistics.statistics(context.deviceId());
    }
    @Override public com.company.project.domain.bo.page.PageSystemBo pageSystem(CleaningOperationContext context,
            String systemCode, String permission) {
        safety.verifyPagePermission(context, permission);
        var system = cleaning.pageSystem(systemCode);
        safety.verifyPageSystem(context, system);
        return system;
    }
    @Override public java.util.List<com.company.project.domain.bo.page.PageSystemBo> pageSystems(CleaningOperationContext context) {
        return cleaning.pageSystems().stream().filter(system -> safety.canReadPageSystem(context, system)).toList();
    }
}
