package com.company.project.domain.bo;

import java.time.Instant;

/** 告警事件保留盒子原始事件 ID，供云盒双向状态同步与去重。 */
public record CloudAlarm(String eventId, String systemCode, String boxCode, String source,
                         String alarmCode, String level, String objectCode, String message,
                         Instant occurredAt, boolean active, String state, long version) {
}
