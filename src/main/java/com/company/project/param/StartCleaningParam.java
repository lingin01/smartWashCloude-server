package com.company.project.param;

import lombok.Data;

/**
 * 开始清洗参数 — Controller → LobbyService
 */
@Data
public class StartCleaningParam {
    /** 清洗器ID */
    private String cleanerCode;
    /** 操作密码 */
    private String password;
    /** 操作人 */
    private String operator;

    private String action;

    private Long previewTime;       // 预览时间戳




}
