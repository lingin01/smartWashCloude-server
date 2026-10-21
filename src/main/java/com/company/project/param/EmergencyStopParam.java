package com.company.project.param;

import lombok.Data;

/**
 * 紧急停止参数 — Controller → LobbyService
 */
@Data
public class EmergencyStopParam {
    /** 清洗器ID */
    private Long cleanerCode;
}
