package com.company.project.domain.bo.statistics;

import java.util.List;

/** 一个来源的有序历史保存批次，重复任务由 DAO 幂等处理。 */
public record TelemetryBatchBo(String batchId, String boxId, long firstSequence,
                               long lastSequence, List<TelemetrySegmentBo> segments) {
    public TelemetryBatchBo {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
