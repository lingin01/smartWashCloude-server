package com.company.project.domain;

import com.company.project.domain.bo.PlanParameters;

import java.time.LocalDateTime;

/**
 * 清洗流程返回结果 BO
 */



public class CleaningResult {
    private Boolean success;
    private String operationType;   // 操作类型或错误码
    private String recordCode;        // 运行记录编号
    private String message;         // 结果消息（成功或失败描述）
    private LocalDateTime completeTime;
    private Object data;            // 预览时存放 PlanParameters，启动成功时存放 CleaningSuccessData
    private Long previewTime;       // 预览时间戳

    // 默认构造器（保留）
    public CleaningResult() {
    }

    /**
     * 预览专用方法：一次性设置计划参数和时间戳
     * @param planParams  计划参数
     * @param previewTime 预览时间戳（毫秒）
     * @return 当前对象，便于链式调用
     */
    public CleaningResult preview(PlanParameters planParams, long previewTime) {
        this.data = planParams;
        this.previewTime = previewTime;
        return this;
    }

    // ==================== 失败方法（链式调用） ====================
    public CleaningResult fail(String operationType, String message) {
        this.success = false;
        this.operationType = operationType;
        this.message = message;
        this.completeTime = LocalDateTime.now();
        return this;
    }

    // ==================== 成功方法（多重重载） ====================
    public CleaningResult success() {
        this.success = true;
        this.completeTime = LocalDateTime.now();
        return this;
    }

    public CleaningResult success(String message) {
        this.success = true;
        this.message = message;
        this.completeTime = LocalDateTime.now();
        return this;
    }

    public CleaningResult success(Object data) {
        this.success = true;
        this.data = data;
        this.completeTime = LocalDateTime.now();
        return this;
    }

    public CleaningResult success(String message, Object data) {
        this.success = true;
        this.message = message;
        this.data = data;
        this.completeTime = LocalDateTime.now();
        return this;
    }

    // ==================== 非 setter 的赋值方法（链式） ====================
    public CleaningResult data(Object data) {
        this.data = data;
        return this;
    }

    public CleaningResult previewTime(Long previewTime) {
        this.previewTime = previewTime;
        return this;
    }

    // ==================== Getter（只读） ====================
    public Boolean getSuccess() {
        return success;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getRecordCode() {
        return recordCode;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public Object getData() {
        return data;
    }

    public Long getPreviewTime() {
        return previewTime;
    }
}
