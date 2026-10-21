package com.company.project.service.impl;

import com.company.project.domain.bo.CloudCommandStatusBo;
import com.company.project.domain.bo.CloudSettingsViewBo;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.param.cloud.*;
import com.company.project.service.CloudCleaningService;
import com.company.project.service.CloudLobbyService;
import com.company.project.service.CloudSafetyService;
import org.springframework.stereotype.Service;
import java.util.Optional;

/** 用户表示层进入大厅，先检查权限，再进入清洗业务；设备采集另走 Runtime。 */
@Service
public class CloudLobbyServiceImpl implements CloudLobbyService {
    private final CloudCleaningService cleaning;
    private final CloudSafetyService safety;

    public CloudLobbyServiceImpl(CloudCleaningService cleaning, CloudSafetyService safety) {
        this.cleaning = cleaning;
        this.safety = safety;
    }

    @Override
    public CloudCommandStatusBo emergencyStop(CloudEmergencyStopParam request) {
        safety.verifyEmergencyStop(request.context());
        return cleaning.emergencyStop(request);
    }

    @Override
    public CloudCommandStatusBo updateSettings(CloudSettingsUpdateParam request) {
        safety.verifySettingsChange(request.context());
        return cleaning.updateSettings(request);
    }

    @Override
    public Optional<TelemetrySample> queryRealtime(CloudOperationContext context) {
        safety.verifyRead(context);
        return cleaning.latest(context.boxId());
    }

    @Override
    public CloudSettingsViewBo querySettings(CloudOperationContext context) {
        safety.verifyRead(context);
        return cleaning.settings(context.boxId());
    }

    @Override
    public Optional<CloudCommandStatusBo> queryOperation(CloudOperationContext context, String requestId) {
        safety.verifyRead(context);
        return cleaning.operation(context.boxId(), requestId);
    }
}
