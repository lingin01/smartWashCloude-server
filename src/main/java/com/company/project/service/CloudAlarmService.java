package com.company.project.service;

import com.company.project.domain.bo.CloudAlarm;

/** 设备告警接收及人工处置的业务接口。 */
public interface CloudAlarmService {
    void receiveDeviceAlarm(CloudAlarm alarm);
    void markPending(String eventId, long expectedVersion, String operator);
    void complete(String eventId, long expectedVersion, String operator);
}
