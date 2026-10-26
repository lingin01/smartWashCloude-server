package com.company.project.service;
import com.company.project.domain.bo.*;
import com.company.project.domain.param.cleaning.*;
import java.util.Optional;
import com.company.project.domain.bo.plc.*;

/** 服务端内壁清洗业务：组织两种允许的控制操作及读取，不直接创建网络客户端。 */
public interface CleaningService {
    void acceptDeviceSnapshot(PlcDeviceSnapshotBo snapshot);
    void acceptDeviceWriteResult(PlcOperationResultBo result);
    CleaningCommandStatusBo emergencyStop(EmergencyStopParam request);
    CleaningCommandStatusBo updateSettings(SettingsUpdateParam request);
    Optional<TelemetrySample> latest(String deviceId);
    CleaningSettingsViewBo settings(String deviceId);
    Optional<CleaningCommandStatusBo> operation(String deviceId, String requestId);
    Optional<CleaningCommandStatusBo> operationByCommandCode(String deviceId, String commandCode);
    com.company.project.domain.bo.page.PageSystemBo pageSystem(String systemCode);
    java.util.List<com.company.project.domain.bo.page.PageSystemBo> pageSystems();
    default StoredSettingsBo saveStoredSettings(CleaningOperationContext context,long version,CleaningSettingsBo value) { return null; }
    default StoredThresholdsBo saveStoredThresholds(CleaningOperationContext context,String winchId,long version,
            java.util.List<StoredThresholdsBo.Value> values) { return null; }
}
