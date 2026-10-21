package com.company.project.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清洗计划 数据库实体
 */
@Data
public class PlanEntity {

    /** 计划编号 */
    private String planCode;

    /** 计划名称 */
    private String planName;

    /** 清洗器编号 */
    private String cleanerCode;

    /** 计划类型 1-单次(SINGLE) 2-周期(PERIODIC) */
    private Integer planType;

    /** 周期计划才有：工作时间(单位：分钟/秒) */
    private Integer workTime;

    /** 周期计划才有：休息时间(单位：分钟/秒) */
    private Integer restTime;

    /** 清洗速度 */
    private Double cleanSpeed;

    /** 基本拉力 */
    private Double baseTension;

    /** 拉力范围 */
    private String tensionRange;

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 状态 true-启用(ENABLED) false-禁用(DISABLED)，默认禁用 */
    private Boolean status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建人 */
    private String createUser;

    /** 删除 1在 0是删除 */
    private boolean deleted;
}
