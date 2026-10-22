package com.company.project.info.buffer.cleaning;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
/** 已整理历史批次的有界待保存队列；不会自动从实时快照生成批次。 */
@Component
public class TelemetryHistoryBuffer implements TelemetryPersistenceBuffer {
    private final BlockingQueue<TelemetryBatchBo> history = new ArrayBlockingQueue<>(2048);
    @Override public boolean offerHistory(TelemetryBatchBo batch) { return history.offer(batch); }
    @Override public List<TelemetryBatchBo> takeHistoryBatch(int maxCount) {
        List<TelemetryBatchBo> result = new ArrayList<>();
        history.drainTo(result, Math.max(0, maxCount));
        return result;
    }
    @Override public void returnHistoryUncommitted(List<TelemetryBatchBo> batches) {
        for (var batch : batches)
            if (!history.offer(batch)) throw new IllegalStateException("历史批次缓冲已满，不能安全回存");
    }
}
