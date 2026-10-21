package com.company.project.domain.bo.statistics;

import java.math.BigDecimal;

/** 历史分段中一个测点的统计值。channel 为盒子上报数组的 1 基序号，不代替设备档案 ID。 */
public record MeasurementAggregateBo(int channel, String targetId,
                                     BigDecimal minimum, BigDecimal maximum,
                                     BigDecimal average, BigDecimal latest,
                                     long sampleCount) {
}
