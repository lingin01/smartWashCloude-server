package com.company.project.domain.bo;

/**
 * 计划参数 BO（预设的清洗参数模板）
 */
public class PlanParameters {
    /** 计划ID */
    private String planId;
    /** 清洗器编码 */
    private String cleanerCode;
    /** 目标压力 */
    private Double targetPressure;
    /** 目标温度 */
    private Double targetTemperature;
    /** 持续时间（秒） */
    private Integer durationSeconds;
    /** 清洗模式: NORMAL/DEEP/FAST */
    private String cleaningMode;
    /** 卷压机转速 */
    private Double rollerSpeed;
    /** 传感器采样间隔（毫秒） */
    private Integer sensorIntervalMs;



    public boolean isValid(){

        return false;
    }
}
