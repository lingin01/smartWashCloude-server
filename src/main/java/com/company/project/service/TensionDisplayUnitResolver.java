package com.company.project.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/** 只处理显示格式，原始拉力与安全判断仍保持盒子上报的 KG。 */
@Component
public class TensionDisplayUnitResolver {
    private final BigDecimal autoRangeKilograms;

    public TensionDisplayUnitResolver(@Value("${cleaning.statistics.autoUnitRangeKg:1000}")
                                      BigDecimal autoRangeKilograms) {
        this.autoRangeKilograms = autoRangeKilograms;
    }

    public String resolve(String requestedUnit, Collection<BigDecimal> visibleValuesKilograms) {
        String requested = requestedUnit == null ? "AUTO" : requestedUnit.toUpperCase(java.util.Locale.ROOT);
        if ("KG".equals(requested) || "TON".equals(requested)) return requested;
        if (!"AUTO".equals(requested)) throw new IllegalArgumentException("不支持的拉力显示单位");
        if (visibleValuesKilograms == null || visibleValuesKilograms.isEmpty()) return "KG";
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (BigDecimal value : visibleValuesKilograms) {
            if (value == null) continue;
            minimum = minimum == null ? value : minimum.min(value);
            maximum = maximum == null ? value : maximum.max(value);
        }
        return minimum != null && maximum.subtract(minimum).compareTo(autoRangeKilograms) >= 0
                ? "TON" : "KG";
    }

    public BigDecimal format(BigDecimal kilograms, String resolvedUnit) {
        if (kilograms == null) return null;
        if ("KG".equals(resolvedUnit)) return kilograms;
        if ("TON".equals(resolvedUnit)) {
            return kilograms.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
        throw new IllegalArgumentException("展示单位必须已解析为 KG 或 TON");
    }
}
