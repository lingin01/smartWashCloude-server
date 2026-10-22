package com.company.project.service.statistics;
import com.company.project.domain.bo.telemetry.*;
import com.company.project.domain.entity.cleaning.*;
import com.company.project.info.buffer.cleaning.TelemetryPersistenceBuffer;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import java.util.List;
/** 只保留已有历史批次保存逻辑；采集整理、轮次与曲线另行实现。 */
final class HistoryBatchPersistence {
    private final TelemetryPersistenceBuffer historyBuffer;
    private final TelemetryHistoryDao historyDao;
    HistoryBatchPersistence(TelemetryPersistenceBuffer historyBuffer, TelemetryHistoryDao historyDao) {
        this.historyBuffer = historyBuffer; this.historyDao = historyDao;
    }
    public void acceptHistoryBatch(TelemetryBatchBo batch) {
        if (batch == null || batch.batchId() == null || batch.batchId().isBlank())
            throw new IllegalArgumentException("历史批次缺少 batchId");
        if (!historyBuffer.offerHistory(batch)) throw new IllegalStateException("历史批次缓冲已满");
    }
    public void runPersistenceCycle() {
        List<TelemetryBatchBo> batches = historyBuffer.takeHistoryBatch(20);
        for (int index = 0; index < batches.size(); index++) {
            TelemetryBatchBo batch = batches.get(index);
            try {
                List<TelemetrySegmentEntity> segments = batch.segments().stream()
                        .map(HistoryBatchPersistence::toEntity).toList();
                historyDao.insertBatch(batch.deviceId(), batch.batchId(), batch.firstSequence(),
                        batch.lastSequence(), segments);
            } catch (Exception failure) {
                // 已提交的批次不回队；当前和未处理批次回温缓冲等待下一轮。
                historyBuffer.returnHistoryUncommitted(batches.subList(index, batches.size()));
                throw new IllegalStateException("历史批次落库失败，待下轮重试", failure);
            }
        }
    }

    private static TelemetrySegmentEntity toEntity(TelemetrySegmentBo segment) {
        return new TelemetrySegmentEntity(segment.deviceId(), segment.batchId(),
                segment.telemetrySequence(), segment.sessionId(), segment.roundNo(),
                segment.direction(), segment.startedAt(), segment.finishedAt(),
                segment.startHeightMeters(), segment.endHeightMeters(),
                segment.triggerReason(), segment.policyVersion(),
                toEntityMetrics(segment.ropeTensions()), toEntityMetrics(segment.winchSpeeds()),
                toEntityMetrics(segment.winchTorqueForcesKg()));
    }

    private static List<MeasurementAggregateEntity> toEntityMetrics(List<MeasurementAggregateBo> values) {
        return values.stream().map(value -> new MeasurementAggregateEntity(value.channel(),
                value.targetId(), value.minimum(), value.maximum(), value.average(),
                value.latest(), value.sampleCount())).toList();
    }

}
