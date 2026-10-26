package com.company.project.service;

import com.company.project.domain.bo.CleaningCommandStatusBo;
import com.company.project.domain.bo.CleaningSettingsViewBo;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.param.cleaning.*;
import java.util.Optional;

/** 用户大厅：协调安全检查、清洗操作及查询，不处理设备协议报文。 */
public interface CleaningLobbyService {
    CleaningCommandStatusBo emergencyStop(EmergencyStopParam request);
    CleaningCommandStatusBo updateSettings(SettingsUpdateParam request);
    Optional<TelemetrySample> queryRealtime(CleaningOperationContext context);
    CleaningSettingsViewBo querySettings(CleaningOperationContext context);
    Optional<CleaningCommandStatusBo> queryOperation(CleaningOperationContext context, String requestId);
    Optional<CleaningCommandStatusBo> queryOperationByCommandCode(CleaningOperationContext context, String commandCode);
    void acceptDeviceSnapshot(com.company.project.domain.bo.plc.PlcDeviceSnapshotBo snapshot);
    void acceptDeviceWriteResult(com.company.project.domain.bo.plc.PlcOperationResultBo result);
    void acceptHistoryBatch(com.company.project.domain.bo.telemetry.TelemetryBatchBo batch);
    com.company.project.domain.bo.statistics.CleaningStatisticsBo queryStatistics(CleaningOperationContext context);
    com.company.project.domain.bo.page.PageSystemBo pageSystem(CleaningOperationContext context,
            String systemCode, String permission);
    java.util.List<com.company.project.domain.bo.page.PageSystemBo> pageSystems(CleaningOperationContext context);
}
