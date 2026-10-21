package com.company.project.vo;


import lombok.Data;

/**
 * 压力传感器视图VO
 */
@Data
public class PressureSensorVO {

    /** 传感器名称 */
    private String sensorName;

    /** 传感器设备编码 */
    private String sensorDeviceCode;

    /** 压力数据 */
    private Double pressureData;

    /** 传感器状态 */
    private String sensorStatus;

    /** 数据更新时间 */
    private String updateData;
}
