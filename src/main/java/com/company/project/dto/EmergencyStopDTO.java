package com.company.project.dto;

import lombok.Data;

/**
 * 紧急停止请求 DTO（Controller → Service）
 */
@Data
public class EmergencyStopDTO {
    /** 清洗器ID */
    private Long cleanerId;
}
