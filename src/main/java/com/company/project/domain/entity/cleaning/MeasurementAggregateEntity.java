package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;

/** 冷数据中的单个拉力、速度或转矩力统计测点。 */
public record MeasurementAggregateEntity(int channel, String targetId,
                                         BigDecimal minimum, BigDecimal maximum,
                                         BigDecimal average, BigDecimal latest,
                                         long sampleCount) {
}
