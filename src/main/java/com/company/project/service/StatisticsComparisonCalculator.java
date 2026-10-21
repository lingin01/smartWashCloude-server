package com.company.project.service;

import com.company.project.domain.bo.statistics.StatisticsResultBo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 环比/同比共同的算术口径；不同周期的基线范围由统计服务提供。 */
@Component
public class StatisticsComparisonCalculator {
    public StatisticsResultBo.ChangeBo compare(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null) {
            return new StatisticsResultBo.ChangeBo(false, baseline, null, null, null, "比较期数据不足");
        }
        if (baseline.signum() == 0) {
            return new StatisticsResultBo.ChangeBo(false, baseline, null, null, null, "比较期基线为零");
        }
        BigDecimal difference = current.subtract(baseline);
        BigDecimal rate = difference.multiply(BigDecimal.valueOf(100))
                .divide(baseline.abs(), 4, RoundingMode.HALF_UP);
        String direction = difference.signum() > 0 ? "INCREASE"
                : difference.signum() < 0 ? "DECREASE" : "UNCHANGED";
        return new StatisticsResultBo.ChangeBo(true, baseline, difference, rate, direction, null);
    }
}
