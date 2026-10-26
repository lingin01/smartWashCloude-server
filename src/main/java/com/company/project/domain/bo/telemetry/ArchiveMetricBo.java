package com.company.project.domain.bo.telemetry;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Measure;
import java.time.Instant;
import java.util.List;
/** 时间序列由分日摘要生成，完整范围汇总不依赖页面点数。 */
public record ArchiveMetricBo(List<Point> points,Measure summary,boolean ready) {
    public ArchiveMetricBo {points=List.copyOf(points);}
    public record Point(String key,Instant at,Measure value) { }
}
