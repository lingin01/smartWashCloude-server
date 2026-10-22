package com.company.project.domain.bo;
import java.math.BigDecimal;
import java.time.LocalTime;

/** 三项运行参数：开始时间、运行间隔和明确的整组速度。 */
public record CleaningSettingsBo(LocalTime startTime, Long intervalSeconds,
                                      BigDecimal groupSpeedMetersPerMinute) { }
