package com.company.project.service;

import com.company.project.domain.bo.CleaningRun;
import com.company.project.domain.bo.CleaningCycle;
import com.company.project.domain.bo.CurveData;
import com.company.project.domain.bo.CurveQuery;
import java.util.List;

/** 历史记录区分连续自动作业与单机检修；曲线查询先保留契约。 */
public interface CloudHistoryService {
    List<CleaningRun> listRuns(String systemCode, String mode, int page, int size);
    CleaningRun getRun(String runId);
    List<CleaningCycle> listCycles(String runId);
    CurveData getRunCurve(CurveQuery query);
}
