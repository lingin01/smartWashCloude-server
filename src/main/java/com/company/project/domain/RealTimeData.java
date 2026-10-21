package com.company.project.domain;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实时遥测数据 BO（传感器/摄像头推送）
 */
public class RealTimeData {
    /** 设备编码 */
    private String deviceCode;
    /** 数据类型: temperature/pressure/speed/image */
    private String dataType;
    /** 数据值 */
    private Map<String, Object> values;
    /** 时间戳 */
    private LocalDateTime timestamp;

    // TODO: Lombok @Data
}
