package com.company.project.vo;

/**
 * 清洗器状态快照 VO（Controller → 前端）
 */
public class CleaningStatusVO {
    /** 清洗器编码 */
    private String cleanerCode;
    /** 设备在线状态描述 */
    private String deviceOnlineStatus;
    /** 设备工作状态描述 */
    private String deviceWorkStatus;
    /** 分解槽温度 */
    private Double tankTemperature;
    /** 分解槽压力 */
    private Double tankPressure;
    /** 分解槽清洁度 */
    private Integer tankCleanliness;
    /** 当前操作类型 */
    private String currentOperation;
    /** 操作开始时间 */
    private String operationStartTime;
    /** 是否有未处理异常 */
    private Boolean hasAlarm;
    /** 未处理异常数 */
    private Integer alarmCount;

    // TODO: Lombok @Data

    public String getCleanerCode() { return cleanerCode; }
    public void setCleanerCode(String cleanerCode) { this.cleanerCode = cleanerCode; }
    public String getDeviceOnlineStatus() { return deviceOnlineStatus; }
    public void setDeviceOnlineStatus(String deviceOnlineStatus) { this.deviceOnlineStatus = deviceOnlineStatus; }
    public String getDeviceWorkStatus() { return deviceWorkStatus; }
    public void setDeviceWorkStatus(String deviceWorkStatus) { this.deviceWorkStatus = deviceWorkStatus; }
    public Double getTankTemperature() { return tankTemperature; }
    public void setTankTemperature(Double tankTemperature) { this.tankTemperature = tankTemperature; }
    public Double getTankPressure() { return tankPressure; }
    public void setTankPressure(Double tankPressure) { this.tankPressure = tankPressure; }
    public Integer getTankCleanliness() { return tankCleanliness; }
    public void setTankCleanliness(Integer tankCleanliness) { this.tankCleanliness = tankCleanliness; }
    public String getCurrentOperation() { return currentOperation; }
    public void setCurrentOperation(String currentOperation) { this.currentOperation = currentOperation; }
    public String getOperationStartTime() { return operationStartTime; }
    public void setOperationStartTime(String operationStartTime) { this.operationStartTime = operationStartTime; }
    public Boolean getHasAlarm() { return hasAlarm; }
    public void setHasAlarm(Boolean hasAlarm) { this.hasAlarm = hasAlarm; }
    public Integer getAlarmCount() { return alarmCount; }
    public void setAlarmCount(Integer alarmCount) { this.alarmCount = alarmCount; }
}
