package com.company.project.domain.entity.cleaning;

import java.math.BigDecimal;
import java.time.Instant;

/** 现场确认后的版本化规则；空值表示尚未确认，不能据此启用自动清洗。 */
public record CleaningSafetyPolicyEntity(String cleaningSystemId, String policyVersion,
                                         String scopeType, String scopeId, String ruleType,
                                         BigDecimal warningValue, BigDecimal stopValue,
                                         String unit, Instant effectiveAt, String status,
                                         String reviewedBy) {
}
