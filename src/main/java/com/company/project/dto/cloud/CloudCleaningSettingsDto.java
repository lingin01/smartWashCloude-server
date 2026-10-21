package com.company.project.dto.cloud;
import java.math.BigDecimal;
/** 三项运行配置的 HTTP 对象；单位在字段名中明确。 */
public record CloudCleaningSettingsDto(String startTime, Long intervalSeconds,
        Integer speedLevel, BigDecimal groupSpeedMetersPerMinute) { }
