package com.company.project.dto.cleaning;
/** 操作人由登录身份取得，设备由路径指定。 */
public record EmergencyStopDto(String requestId, String reason) { }
