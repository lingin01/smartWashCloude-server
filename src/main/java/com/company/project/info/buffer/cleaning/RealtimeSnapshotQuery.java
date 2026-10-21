package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.TelemetrySample;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** WebSocket/大厅只拥有读取最新快照的窄接口。 */
public interface RealtimeSnapshotQuery {
    Optional<TelemetrySample> latestSnapshot(String boxId);
    List<TelemetrySample> latestSnapshots(Collection<String> boxIds);
}
