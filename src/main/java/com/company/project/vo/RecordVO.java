package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清洗记录 VO
 */
@Data
public class RecordVO {

    /** 记录编号 */
    private String recordCode;

    /** 任务编号 */
    private String taskCode;

    /** 计划编号 */
    private String planCode;

    /** 清洗器编号 */
    private String cleanerCode;

    /** 操作人 */
    private String operator;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 总时长(单位：秒/分钟) */
    private Long totalDuration;

    /** 状态 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
