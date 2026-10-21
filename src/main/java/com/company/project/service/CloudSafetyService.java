package com.company.project.service;

import com.company.project.domain.param.cloud.CloudOperationContext;

/** 云端控制权限与请求校验；不替代盒子和 PLC 的本地安全联锁。 */
public interface CloudSafetyService {
    void verifyRead(CloudOperationContext context);
    void verifyEmergencyStop(CloudOperationContext context);
    void verifySettingsChange(CloudOperationContext context);
}
