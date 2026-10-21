package com.company.project.domain.bo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 曲线值、横纵轴和单位由后端一次性提供，前端只绘制。 */
public record CurveData(String title, String xAxisTitle, String yAxisTitle,
                        String displayUnit, BigDecimal yMin, BigDecimal yMax,
                        List<Point> points) {
    public record Point(Instant time, BigDecimal value) {
    }
}
