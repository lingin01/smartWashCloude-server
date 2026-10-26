package com.company.project.service.safety;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import org.springframework.security.access.AccessDeniedException;

/** 使用表示层提供的可信权限快照；现场设备条件仍须由正式设备接入层核对。 */
final class OperationAccessPolicy {
    public void verifyRead(CleaningOperationContext context) { verify(context, "device:read", false); }
    public void verifyEmergencyStop(CleaningOperationContext context) { verify(context, "device:write", true); }
    public void verifySettingsChange(CleaningOperationContext context) { verify(context, "device:write", true); }
    public void verifyPagePermission(CleaningOperationContext context, String permission) {
        if (!hasIdentity(context) || permission == null || !context.permissions().contains(permission))
            throw new AccessDeniedException("当前账号没有相应页面权限");
    }
    public void verifyPageSystem(CleaningOperationContext context, com.company.project.domain.bo.page.PageSystemBo system) {
        if (!hasIdentity(context) || !system.allowedUsernames().contains(context.operatorId()))
            throw new AccessDeniedException("当前账号未获授权访问该系统");
    }
    public boolean canReadPageSystem(CleaningOperationContext context, com.company.project.domain.bo.page.PageSystemBo system) {
        return hasIdentity(context) && context.permissions().contains("device:read")
                && system.allowedUsernames().contains(context.operatorId());
    }
    private boolean hasIdentity(CleaningOperationContext context) {
        return context != null && context.operatorId() != null && !context.operatorId().isBlank();
    }
    private void verify(CleaningOperationContext context, String permission, boolean operation) {
        if (context == null || context.operatorId() == null || context.operatorId().isBlank()
                || !context.permissions().contains(permission)) throw new AccessDeniedException("无设备操作权限");
        if (context.deviceId() == null || !context.deviceId().matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("PLC编号格式不正确");
        if (operation && (context.requestId() == null || !context.requestId().matches("[A-Za-z0-9._-]{1,128}")))
            throw new IllegalArgumentException("操作必须提供有效且稳定的 requestId");
    }
}
