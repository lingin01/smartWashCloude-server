package com.company.project.vo.cloud;
import java.math.BigDecimal;
public record CloudSettingsVo(String boxId, String state, Long version, SettingsVo settings) {
    public record SettingsVo(String startTime, Long intervalSeconds, Integer speedLevel,
                              BigDecimal groupSpeedMetersPerMinute) { }
}
