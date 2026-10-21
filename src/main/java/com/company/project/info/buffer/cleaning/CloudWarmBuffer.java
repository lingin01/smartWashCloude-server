package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.statistics.CleaningRoundSummaryBo;
import com.company.project.domain.bo.statistics.TelemetryBatchBo;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** 一个聚合实现分别暴露历史批次和完成轮次窄接口；队列容量固定。 */
@Component
public class CloudWarmBuffer implements TelemetryPersistenceBuffer, CompletedRoundBuffer {
    private final BlockingQueue<TelemetryBatchBo> history = new ArrayBlockingQueue<>(2048);
    private final BlockingQueue<CleaningRoundSummaryBo> rounds = new ArrayBlockingQueue<>(2048);

    @Override
    public boolean offerHistory(TelemetryBatchBo batch) {
        return history.offer(batch);
    }

    @Override
    public List<TelemetryBatchBo> takeHistoryBatch(int maxCount) {
        List<TelemetryBatchBo> result = new ArrayList<>();
        history.drainTo(result, Math.max(0, maxCount));
        return result;
    }

    @Override
    public void returnHistoryUncommitted(List<TelemetryBatchBo> batches) {
        for (TelemetryBatchBo batch : batches) {
            if (!history.offer(batch)) throw new IllegalStateException("历史批次缓冲已满，不能安全回存");
        }
    }

    @Override
    public boolean offerRound(CleaningRoundSummaryBo round) {
        return rounds.offer(round);
    }

    @Override
    public List<CleaningRoundSummaryBo> takeRoundsBatch(int maxCount) {
        List<CleaningRoundSummaryBo> result = new ArrayList<>();
        rounds.drainTo(result, Math.max(0, maxCount));
        return result;
    }

    @Override
    public void returnRoundsUncommitted(List<CleaningRoundSummaryBo> failedRounds) {
        for (CleaningRoundSummaryBo round : failedRounds) {
            if (!rounds.offer(round)) throw new IllegalStateException("轮次缓冲已满，不能安全回存");
        }
    }
}
