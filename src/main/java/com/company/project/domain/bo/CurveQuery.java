package com.company.project.domain.bo;

/** 历史曲线预留查询；自动单位、坐标轴及降采样均由后端确定。 */
public record CurveQuery(String runId, String metric, String objectCode,
                         String displayUnit, int maxPoints) {
}
