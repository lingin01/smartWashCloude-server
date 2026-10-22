package com.company.project.service;

import com.company.project.domain.param.cleaning.CleaningOperationContext;

/** 服务端控制权限与请求校验；不替代 PLC 的本地安全联锁。 */
public interface CleaningSafetyService {
    void verifyRead(CleaningOperationContext context);
    void verifyEmergencyStop(CleaningOperationContext context);
    void verifySettingsChange(CleaningOperationContext context);
}
