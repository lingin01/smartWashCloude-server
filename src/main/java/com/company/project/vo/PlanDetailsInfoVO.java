package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清洗计划详情VO
 */
@Data
public class PlanDetailsInfoVO {

    /** 计划编号 */
    private String planCode;

    /** 计划名称 */
    private String planName;

    /** 清洗器编号 */
    private String cleanerCode;

    /** 计划类型 false-单次(SINGLE) true-周期(PERIODIC) */
    private Boolean planType;

    /** 周期才有 工作时间(单位：分钟/秒) */
    private Integer workTime;

    /** 周期才有 休息时间(单位：分钟/秒) */
    private Integer restTime;

    /** 清洗速度 */
    private Double cleanSpeed;

    /** 基本拉力 */
    private Double baseTension;

    /** 拉力范围 */
    private String tensionRange;

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 状态 1-启用(ENABLED) 0-未用() 默认是未用*/
    private Integer status;

    /**
     * 组装模拟假数据
     */
    public void buildMockData() {
        this.setPlanCode("PLAN-20260614001");
        this.setPlanName("A区一号槽单次清洗计划");
        this.setCleanerCode("CL-001");
        this.setPlanType(false);
        this.setWorkTime(null);
        this.setRestTime(null);
        this.setCleanSpeed(2.6);
        this.setBaseTension(520.0);
        this.setTensionRange("480 ~ 560 N");
        this.setExecuteTime(LocalDateTime.now().plusHours(2));
        this.setStatus(1);
    }
}
