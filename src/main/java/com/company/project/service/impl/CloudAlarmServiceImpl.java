package com.company.project.service.impl;

import com.company.project.domain.bo.CloudAlarm;
import com.company.project.info.buffer.cleaning.CloudRuntimeDataCenter;
import com.company.project.service.CloudAlarmService;
import org.springframework.stereotype.Service;

/** 告警状态采用事件 ID 和版本号去重；正式采集与持久化在后续设备接入阶段补齐。 */
@Service
public class CloudAlarmServiceImpl implements CloudAlarmService {
    private final CloudRuntimeDataCenter dataCenter;

    public CloudAlarmServiceImpl(CloudRuntimeDataCenter dataCenter) {
        this.dataCenter = dataCenter;
    }

    @Override
    public synchronized void receiveDeviceAlarm(CloudAlarm alarm) {
        if (alarm.eventId() == null || alarm.eventId().isBlank()) {
            throw new IllegalArgumentException("告警缺少事件 ID");
        }
        dataCenter.putAlarm(alarm);
    }

    @Override
    public synchronized void markPending(String eventId, long expectedVersion, String operator) {
        transition(eventId, expectedVersion, "PENDING");
    }

    @Override
    public synchronized void complete(String eventId, long expectedVersion, String operator) {
        transition(eventId, expectedVersion, "COMPLETED");
    }

    private void transition(String eventId, long expectedVersion, String next) {
        CloudAlarm old = dataCenter.alarm(eventId);
        if (old == null || old.version() != expectedVersion) {
            throw new IllegalStateException("告警状态已变化，请刷新后重试");
        }
        if (("PENDING".equals(next) && !"ACTIVE".equals(old.state()))
                || ("COMPLETED".equals(next) && !"PENDING".equals(old.state()))) {
            throw new IllegalStateException("当前告警状态不允许该操作");
        }
        dataCenter.putAlarm(new CloudAlarm(old.eventId(), old.systemCode(), old.boxCode(),
                old.source(), old.alarmCode(), old.level(), old.objectCode(), old.message(),
                old.occurredAt(), old.active(), next, old.version() + 1));
    }
}
