package com.company.project.param;

import lombok.Data;

/**
 * 系统复位参数 — Controller → LobbyService
 */
@Data
public class SystemResetParam {
    /** 清洗器ID */
    private Long cleanerCode;
}
