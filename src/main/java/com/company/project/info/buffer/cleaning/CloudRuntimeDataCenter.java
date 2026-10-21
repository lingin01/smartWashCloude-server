package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.CloudAlarm;
import com.company.project.domain.bo.TelemetrySample;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/** 设备热/温数据内存中心；冷数据必须另行接入 DAO，不能依赖此类持久保存。 */
@Component
public class CloudRuntimeDataCenter implements RealtimeSnapshotSink, RealtimeSnapshotQuery {
    private static final int MAX_WARM_SAMPLES_PER_BOX = 10_000;
    private final Map<String, TelemetrySample> latestByBox = new HashMap<>();
    private final Map<String, ArrayDeque<TelemetrySample>> warmByBox = new HashMap<>();
    private final Map<String, CloudAlarm> alarmsByEventId = new HashMap<>();

    public synchronized boolean acceptTelemetry(TelemetrySample sample) {
        TelemetrySample last = latestByBox.get(sample.boxId());
        // 同一运行会话的重复/乱序包不覆盖最新值；跨会话以采样时间判断。
        if (last != null && (Objects.equals(last.sessionId(), sample.sessionId())
                ? sample.sequence() <= last.sequence()
                : !sample.sampledAt().isAfter(last.sampledAt()))) {
            return false;
        }
        latestByBox.put(sample.boxId(), sample);
        ArrayDeque<TelemetrySample> warm = warmByBox.computeIfAbsent(sample.boxId(), k -> new ArrayDeque<>());
        warm.addLast(sample);
        while (warm.size() > MAX_WARM_SAMPLES_PER_BOX) {
            warm.removeFirst();
        }
        return true;
    }

    @Override
    public boolean updateLatest(TelemetrySample sample) {
        return acceptTelemetry(sample);
    }

    @Override
    public synchronized Optional<TelemetrySample> latestSnapshot(String boxId) {
        return Optional.ofNullable(latestByBox.get(boxId));
    }

    @Override
    public synchronized List<TelemetrySample> latestSnapshots(Collection<String> boxIds) {
        return boxIds.stream().map(latestByBox::get).filter(java.util.Objects::nonNull).toList();
    }

    public synchronized TelemetrySample latest(String boxId) {
        return latestByBox.get(boxId);
    }

    public synchronized void putAlarm(CloudAlarm alarm) {
        CloudAlarm previous = alarmsByEventId.get(alarm.eventId());
        if (previous == null || alarm.version() > previous.version()) {
            alarmsByEventId.put(alarm.eventId(), alarm);
        } else if ("BOX".equals(alarm.source()) && alarm.active() != previous.active()) {
            // 告警恢复与处置状态是两个维度；盒子重复事件不可回退云端处置版本。
            alarmsByEventId.put(alarm.eventId(), new CloudAlarm(previous.eventId(),
                    previous.systemCode(), previous.boxCode(), previous.source(), previous.alarmCode(),
                    previous.level(), previous.objectCode(), previous.message(), previous.occurredAt(),
                    alarm.active(), previous.state(), previous.version()));
        }
    }

    public synchronized CloudAlarm alarm(String eventId) {
        return alarmsByEventId.get(eventId);
    }

}
