package com.company.project.dto.cloud;
/** 操作人由登录身份取得，客户端仅提交稳定请求编号和业务参数。 */
public record CloudEmergencyStopDto(String requestId, String sessionId, String reason) { }
