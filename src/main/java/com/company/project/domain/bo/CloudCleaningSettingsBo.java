package com.company.project.domain.bo;
import java.math.BigDecimal;
import java.time.LocalTime;

/** 本轮仅保留开始时间、整组运行间隔和速度/挡位。PLC 编码与协议字段仍待联调。 */
public record CloudCleaningSettingsBo(LocalTime startTime, Long intervalSeconds,
                                      Integer speedLevel, BigDecimal groupSpeedMetersPerMinute) { }
