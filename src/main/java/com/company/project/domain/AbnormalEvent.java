package com.company.project.domain;

import java.time.LocalDateTime;

/**
 * 异常事件 BO（设备上报的异常/告警）
 */
public class AbnormalEvent {
    /** 事件ID */
    private Long eventId;
    /** 设备编码 */
    private String deviceCode;
    /** 异常编码 */
    private String alarmCode;
    /** 异常名称 */
    private String alarmName;
    /** 异常级别 1=一般 2=重要 3=紧急 */
    private Integer alarmLevel;
    /** 异常描述 */
    private String alarmMessage;
    /** 是否已解决 */
    private Boolean resolved;
    /** 触发时间 */
    private LocalDateTime triggerTime;
    /** 解决时间 */
    private LocalDateTime resolveTime;

    // TODO: Lombok @Data
}
