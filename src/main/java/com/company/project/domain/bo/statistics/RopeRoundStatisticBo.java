package com.company.project.domain.bo.statistics;

/** 每轮单根牵引索的独立统计，传感器 ID 与索 ID 不合并。 */
public record RopeRoundStatisticBo(String ropeId, String sensorId, String winchId,
                                   MetricAccumulatorBo tensionKilograms,
                                   long warningCrossings, long stopCrossings) {
}
