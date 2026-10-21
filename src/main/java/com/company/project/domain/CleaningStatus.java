package com.company.project.domain;

import com.company.project.domain.bo.TankStatus;
import com.company.project.domain.bo.OperationRecord;

/**
 * 清洗器当前状态快照 BO
 */
public class CleaningStatus {
    /** 清洗器编码 */
    private String cleanerCode;
    /** 设备状态 */
    private DeviceStatus deviceStatus;
    /** 分解槽状态 */
    private TankStatus tankStatus;
    /** 当前运行记录 */
    private OperationRecord activeRecord;
    /** 是否有未处理异常 */
    private Boolean hasAbnormalEvents;
    /** 未处理异常数 */
    private Integer abnormalEventCount;

    // TODO: Lombok @Data
}
