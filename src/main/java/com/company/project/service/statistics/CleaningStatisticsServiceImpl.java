package com.company.project.service.statistics;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.statistics.CleaningStatisticsBo;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
import com.company.project.info.buffer.cleaning.TelemetryPersistenceBuffer;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.service.CleaningStatisticsService;
import org.springframework.stereotype.Service;
/** 统计门面只委派：样本累计与历史事务保存由各自辅助类承担。 */
@Service
public class CleaningStatisticsServiceImpl implements CleaningStatisticsService {
    private final SampleStatistics samples=new SampleStatistics();
    private final HistoryBatchPersistence history;
    public CleaningStatisticsServiceImpl(TelemetryPersistenceBuffer buffer,TelemetryHistoryDao dao){history=new HistoryBatchPersistence(buffer,dao);}
    @Override public void acceptSample(TelemetrySample sample){samples.accept(sample);}
    @Override public CleaningStatisticsBo statistics(String deviceId){return samples.snapshot(deviceId);}
    @Override public void acceptHistoryBatch(TelemetryBatchBo batch){history.acceptHistoryBatch(batch);}
    @Override public void runPersistenceCycle(){history.runPersistenceCycle();}
}
