package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.CleaningRoundEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 每轮下降+上升的冷数据接口，唯一键 sessionId+roundNo。 */
public interface CleaningRoundDao {
    Optional<CleaningRoundEntity> find(String sessionId, int roundNo);
    boolean insertIfAbsent(CleaningRoundEntity round);
    List<CleaningRoundEntity> findFinished(String boxId, Instant fromInclusive, Instant toExclusive);
}
