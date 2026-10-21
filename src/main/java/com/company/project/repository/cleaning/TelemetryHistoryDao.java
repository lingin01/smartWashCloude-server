package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.TelemetrySegmentEntity;
import java.time.Instant;
import java.util.List;

/** 批次与分段应在同一事务中写入，重复批次不得重复累计。 */
public interface TelemetryHistoryDao {
    boolean batchExists(String boxId, String batchId);
    void insertBatch(String boxId, String batchId, long firstSequence, long lastSequence,
                     List<TelemetrySegmentEntity> segments);
    List<TelemetrySegmentEntity> findByRange(String boxId, Instant fromInclusive, Instant toExclusive);
}
