package com.company.project.domain.bo.telemetry;

import java.math.BigDecimal;

/** 历史分段中一个测点的统计值。channel 为历史记录的 1 基通道序号，不代替设备档案 ID。 */
public record MeasurementAggregateBo(int channel, String targetId,
                                     BigDecimal minimum, BigDecimal maximum,
                                     BigDecimal average, BigDecimal latest,
                                     long sampleCount) {
}
