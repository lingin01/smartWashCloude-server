package com.company.project.service;
import com.company.project.domain.bo.*;
import com.company.project.domain.param.cloud.*;
import java.util.Optional;
import com.company.project.domain.bo.plc.*;

/** 云端内壁清洗业务：组织两种允许的控制操作及读取，不直接创建网络客户端。 */
public interface CloudCleaningService {
    void acceptDeviceSnapshot(PlcDeviceSnapshotBo snapshot);
    void acceptDeviceWriteResult(PlcOperationResultBo result);
    CloudCommandStatusBo emergencyStop(CloudEmergencyStopParam request);
    CloudCommandStatusBo updateSettings(CloudSettingsUpdateParam request);
    Optional<TelemetrySample> latest(String boxId);
    CloudSettingsViewBo settings(String boxId);
    Optional<CloudCommandStatusBo> operation(String boxId, String requestId);
}
