package com.company.project.service.impl;

import com.company.project.domain.bo.TelemetrySample;
import com.company.project.info.buffer.cleaning.RealtimeSnapshotSink;
import com.company.project.info.buffer.cleaning.TelemetryPersistenceBuffer;
import com.company.project.info.buffer.cleaning.CompletedRoundBuffer;
import com.company.project.domain.bo.statistics.CleaningRoundSummaryBo;
import com.company.project.domain.bo.statistics.StatisticsQueryBo;
import com.company.project.domain.bo.statistics.StatisticsResultBo;
import com.company.project.domain.bo.statistics.TelemetryBatchBo;
import com.company.project.domain.bo.statistics.MeasurementAggregateBo;
import com.company.project.domain.bo.statistics.TelemetrySegmentBo;
import com.company.project.domain.entity.cleaning.MeasurementAggregateEntity;
import com.company.project.domain.entity.cleaning.TelemetrySegmentEntity;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.service.CloudStatisticsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** 先完成实时数据入热/温缓冲；日月年统计必须基于持久化后的完整行程计算。 */
@Service
public class CloudStatisticsServiceImpl implements CloudStatisticsService {
    private final RealtimeSnapshotSink realtimeSink;
    private final TelemetryPersistenceBuffer historyBuffer;
    private final CompletedRoundBuffer roundBuffer;
    private final TelemetryHistoryDao historyDao;

    public CloudStatisticsServiceImpl(RealtimeSnapshotSink realtimeSink,
                                      TelemetryPersistenceBuffer historyBuffer,
                                      CompletedRoundBuffer roundBuffer,
                                      TelemetryHistoryDao historyDao) {
        this.realtimeSink = realtimeSink;
        this.historyBuffer = historyBuffer;
        this.roundBuffer = roundBuffer;
        this.historyDao = historyDao;
    }

    @Override
    public void acceptSample(TelemetrySample sample) {
        realtimeSink.updateLatest(sample);
    }

    @Override
    public void acceptHistoryBatch(TelemetryBatchBo batch) {
        if (batch == null || batch.batchId() == null || batch.batchId().isBlank()) {
            throw new IllegalArgumentException("历史批次缺少 batchId");
        }
        if (!historyBuffer.offerHistory(batch)) throw new IllegalStateException("历史批次缓冲已满");
    }

    @Override
    public void acceptCompletedRound(CleaningRoundSummaryBo round) {
        if (round == null || round.sessionId() == null || round.roundNo() <= 0) {
            throw new IllegalArgumentException("轮次标识无效");
        }
        if (!roundBuffer.offerRound(round)) throw new IllegalStateException("轮次缓冲已满");
    }

    @Override
    public StatisticsResultBo query(StatisticsQueryBo query) {
        throw new UnsupportedOperationException("统计 DAO 与返回曲线计算尚未接入");
    }

    @Override
    public void runRealtimePersistenceCycle() {
        List<TelemetryBatchBo> batches = historyBuffer.takeHistoryBatch(20);
        for (int index = 0; index < batches.size(); index++) {
            TelemetryBatchBo batch = batches.get(index);
            try {
                List<TelemetrySegmentEntity> segments = batch.segments().stream()
                        .map(CloudStatisticsServiceImpl::toEntity).toList();
                historyDao.insertBatch(batch.boxId(), batch.batchId(), batch.firstSequence(),
                        batch.lastSequence(), segments);
            } catch (Exception failure) {
                // 已提交的批次不回队；当前和未处理批次回温缓冲等待下一轮。
                historyBuffer.returnHistoryUncommitted(batches.subList(index, batches.size()));
                throw new IllegalStateException("历史批次落库失败，待下轮重试", failure);
            }
        }
    }

    private static TelemetrySegmentEntity toEntity(TelemetrySegmentBo segment) {
        return new TelemetrySegmentEntity(segment.boxId(), segment.batchId(),
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

    @Override
    public void runRoundAggregationCycle() {
        throw new UnsupportedOperationException("轮次聚合 DAO 尚未接入");
    }

    @Override
    public void runDirtyBucketRebuildCycle() {
        throw new UnsupportedOperationException("脏桶重算 DAO 尚未接入");
    }

    @Override
    public void runRetentionCycle() {
        throw new UnsupportedOperationException("保留策略 DAO 尚未接入");
    }

    @Override
    public void closeRun(String runId) {
        throw new UnsupportedOperationException("行程汇总 DAO 尚未接入");
    }

    @Override
    public void rebuildDay(String systemCode, LocalDate day) {
        throw new UnsupportedOperationException("日统计 DAO 尚未接入");
    }

    @Override
    public void rebuildMonth(String systemCode, YearMonth month) {
        throw new UnsupportedOperationException("月统计 DAO 尚未接入");
    }

    @Override
    public void rebuildYear(String systemCode, int year) {
        throw new UnsupportedOperationException("年统计 DAO 尚未接入");
    }
}
