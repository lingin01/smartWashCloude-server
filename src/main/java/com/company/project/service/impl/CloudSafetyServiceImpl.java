package com.company.project.service.impl;
import com.company.project.domain.param.cloud.CloudOperationContext;
import com.company.project.service.CloudSafetyService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** 使用表示层提供的可信权限快照；现场设备条件仍须由正式设备接入层核对。 */
@Service
public class CloudSafetyServiceImpl implements CloudSafetyService {
    @Override public void verifyRead(CloudOperationContext context) { verify(context, "device:read", false); }
    @Override public void verifyEmergencyStop(CloudOperationContext context) { verify(context, "device:write", true); }
    @Override public void verifySettingsChange(CloudOperationContext context) { verify(context, "device:write", true); }
    private void verify(CloudOperationContext context, String permission, boolean operation) {
        if (context == null || context.operatorId() == null || context.operatorId().isBlank()
                || !context.permissions().contains(permission)) throw new AccessDeniedException("无设备操作权限");
        if (context.boxId() == null || !context.boxId().matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("盒子编号格式不正确");
        if (operation && (context.requestId() == null || !context.requestId().matches("[A-Za-z0-9._-]{1,128}")))
            throw new IllegalArgumentException("操作必须提供有效且稳定的 requestId");
    }
}
