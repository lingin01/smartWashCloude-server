package com.company.project.dto;

import lombok.Data;

/**
 * 开始清洗请求 DTO（Controller → Service）
 */
@Data
public class StartCleaningDTO {
    private String cleanerId;
    private String password;
    private String operator;
}
