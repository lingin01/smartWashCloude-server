package com.company.project.domain.bo.statistics;

import java.math.BigDecimal;

/** 两索合计、速度、高度和转矩分别统计；转矩只使用盒子上报值。 */
public record WinchRoundStatisticBo(String winchId,
                                    MetricAccumulatorBo twoRopeTensionKilograms,
                                    MetricAccumulatorBo speedMetersPerMinute,
                                    BigDecimal minimumHeightMeters,
                                    BigDecimal maximumHeightMeters,
                                    BigDecimal latestHeightMeters,
                                    MetricAccumulatorBo torqueNewtonMeters) {
}
