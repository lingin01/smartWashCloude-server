package com.company.project.domain;

import java.time.LocalDateTime;

/**
 * 设备状态 BO（清洗器盒子 / 卷压机 / 传感器 / 摄像头）
 */
public class DeviceStatus {
    /** 设备编码 */
    private String deviceCode;
    /** 设备名称 */
    private String deviceName;
    /** 设备类型: BOX / ROLLER / SENSOR / CAMERA */
    private String deviceType;
    /** 在线状态 0=离线 1=在线 */
    private Integer onlineStatus;
    /** 工作状态 0=空闲 1=运行中 2=故障 3=维护中 */
    private Integer workStatus;
    /** 最后在线时间 */
    private LocalDateTime lastOnlineTime;
    /** 固件版本 */
    private String firmwareVersion;
    /** 所属清洗器编码（子设备归属哪个盒子） */
    private String parentCleanerCode;

    // TODO: Lombok @Data / @Getter @Setter
}
