package com.company.project.service;

import com.company.project.domain.bo.CloudCommandStatusBo;
import com.company.project.domain.bo.CloudSettingsViewBo;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.param.cloud.*;
import java.util.Optional;

/** 用户大厅：协调安全检查、清洗操作及查询，不处理设备协议报文。 */
public interface CloudLobbyService {
    CloudCommandStatusBo emergencyStop(CloudEmergencyStopParam request);
    CloudCommandStatusBo updateSettings(CloudSettingsUpdateParam request);
    Optional<TelemetrySample> queryRealtime(CloudOperationContext context);
    CloudSettingsViewBo querySettings(CloudOperationContext context);
    Optional<CloudCommandStatusBo> queryOperation(CloudOperationContext context, String requestId);
}
