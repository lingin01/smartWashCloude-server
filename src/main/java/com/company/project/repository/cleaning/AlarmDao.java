package com.company.project.repository.cleaning;

import com.company.project.domain.bo.CloudAlarm;
import java.util.Optional;

public interface AlarmDao {
    Optional<CloudAlarm> findByEventId(String eventId);
    boolean insertIfAbsent(CloudAlarm alarm);
    boolean compareAndSet(CloudAlarm alarm, long expectedRevision, String operationId);
}
