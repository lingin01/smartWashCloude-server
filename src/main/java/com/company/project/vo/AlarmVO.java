package com.company.project.vo;


import lombok.Data;

import java.time.LocalDateTime;


/*
* 最新的告警信息
* */
@Data
public class AlarmVO {

    /** 告警编号 */
    private String alarmCode;

    /** 设备编号 */
    private String cleanerCode;

    /** 告警级别：一般、重要、紧急 */
    private String alarmLevel;

    /** 告警状态：未恢复、已恢复 */
    private String alarmStatus;

    /** 可选补充：告警时间（常用字段） */
    private LocalDateTime alarmTime;
}
