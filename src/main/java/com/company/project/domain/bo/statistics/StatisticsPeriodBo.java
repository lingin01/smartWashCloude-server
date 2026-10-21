package com.company.project.domain.bo.statistics;

import java.time.Instant;

/** 所有统计范围左闭右开，以 Asia/Shanghai 自然日历解析。 */
public record StatisticsPeriodBo(Instant startInclusive, Instant endExclusive, String label) {
}
