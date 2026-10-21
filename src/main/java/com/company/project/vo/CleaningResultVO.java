package com.company.project.vo;

/**
 * 清洗操作结果 VO（Controller → 前端）
 */
public class CleaningResultVO {
    /** 是否成功 */
    private Boolean success;
    /** 操作类型: START/STOP/RESET */
    private String operationType;
    /** 运行记录ID */
    private String recordId;
    /** 结果消息 */
    private String message;
    /** 完成时间 */
    private String completeTime;

    // TODO: Lombok @Data

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCompleteTime() { return completeTime; }
    public void setCompleteTime(String completeTime) { this.completeTime = completeTime; }
}
