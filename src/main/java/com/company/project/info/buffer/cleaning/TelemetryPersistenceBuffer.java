package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
import java.util.List;

/** 历史批次待落库缓冲；真正的断电恢复需要持久化 inbox。 */
public interface TelemetryPersistenceBuffer {
    boolean offerHistory(TelemetryBatchBo batch);
    List<TelemetryBatchBo> takeHistoryBatch(int maxCount);
    void returnHistoryUncommitted(List<TelemetryBatchBo> batches);
}
