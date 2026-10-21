package com.company.project.dto;

import lombok.Data;

/**
 * 系统复位请求 DTO（Controller → Service）
 */
@Data
public class SystemResetDTO {
    /** 清洗器ID */
    private Long cleanerId;
}
