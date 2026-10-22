package com.company.project.vo.cleaning;
import java.math.BigDecimal;
public record CleaningSettingsVo(String deviceId, String state, Long version, SettingsVo settings) {
    public record SettingsVo(String startTime, Long intervalSeconds, BigDecimal groupSpeedMetersPerMinute) { }
}
