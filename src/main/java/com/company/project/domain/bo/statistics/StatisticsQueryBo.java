package com.company.project.domain.bo.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 统一统计查询条件；日期范围由服务按 Asia/Shanghai 的自然周期解析。 */
public record StatisticsQueryBo(String boxId, String metric, String periodType,
                                String groupBy, LocalDate date, Integer year, Integer month,
                                Integer quarter, Integer half, Instant from, Instant to,
                                List<String> ropeIds, List<String> winchIds,
                                boolean includeAbnormal, String displayUnit,
                                boolean includeComparison, String comparisonScope) {
    public StatisticsQueryBo {
        ropeIds = ropeIds == null ? List.of() : List.copyOf(ropeIds);
        winchIds = winchIds == null ? List.of() : List.copyOf(winchIds);
    }
}
