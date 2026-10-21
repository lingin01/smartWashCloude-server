package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.TelemetrySample;

/** 统计服务只拥有写入最新快照的窄接口。 */
public interface RealtimeSnapshotSink {
    boolean updateLatest(TelemetrySample sample);
}
