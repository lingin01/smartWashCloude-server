package com.company.project.domain.entity;

/**
 * 设备类型枚举
 */
public enum DeviceTypeEnum {

    CLEANER("清洗器"),
    CAMERA("摄像头"),
    ROLLING_MACHINE("卷压机"),
    PRESSURE_SENSOR("压力传感器");

    private final String desc;

    DeviceTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
