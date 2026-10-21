package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.statistics.CleaningRoundSummaryBo;
import java.util.List;

/** 已完成轮次等待聚合的温数据端口。 */
public interface CompletedRoundBuffer {
    boolean offerRound(CleaningRoundSummaryBo round);
    List<CleaningRoundSummaryBo> takeRoundsBatch(int maxCount);
    void returnRoundsUncommitted(List<CleaningRoundSummaryBo> rounds);
}
