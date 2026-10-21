package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.CleaningSessionEntity;
import java.util.Optional;

/** 清洗会话冷数据；与单机检修记录分开。 */
public interface CleaningSessionDao {
    Optional<CleaningSessionEntity> findById(String sessionId);
    void insert(CleaningSessionEntity session);
    boolean update(CleaningSessionEntity session, String expectedStatus);
}
