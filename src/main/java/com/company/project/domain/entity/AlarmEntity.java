package com.company.project.domain.entity;


import lombok.Data;

@Data
public class AlarmEntity {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 告警编码
     */
    private String alarmCode;

    /**
     * 清洗设备编码
     */
    private String cleanerCode;

    /**
     * 告警名称
     */
    private String alarmName;

    /**
     * 告警级别：一般、重要、紧急
     */
    private String alarmLevel;

    /**
     * 告警内容
     */
    private String alarmContent;

    /**
     * 告警时间
     */
    private String alarmTime;

    /**
     * 告警状态：ACTIVE(告警中)、RECOVERED(已恢复)
     */
    private String status;

}


