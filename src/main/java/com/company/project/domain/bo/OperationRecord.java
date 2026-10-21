package com.company.project.domain.bo;

import com.company.project.domain.enums.OperationTypeEnum;
import com.company.project.domain.enums.RecordRunStatusEnum;

import java.time.LocalDateTime;

/**
 * 运行记录 Bo
 */
import java.time.LocalDateTime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OperationRecord {
    private String recordCode;          // 记录ID：清洗器编号_开始时间
    private String cleanerCode;
    private OperationTypeEnum operationType;
    private String operator;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private RecordRunStatusEnum status;
    private String resultSummary;

    // 私有构造器（只允许通过静态工厂创建）
    public OperationRecord() {
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建一个“启动”类型的运行记录（自动生成 recordCode 并设置初始状态为 STARTING）
     * @param cleanerCode 清洗器编码
     * @param operator    操作人
     * @return 已填充基本字段的 OperationRecord 实例
     */
    public static OperationRecord forStart(String cleanerCode, String operator) {
        LocalDateTime now = LocalDateTime.now();
        String timeSuffix = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String recordCode = cleanerCode + "_" + timeSuffix;

        OperationRecord record = new OperationRecord();
        record.recordCode = recordCode;
        record.cleanerCode = cleanerCode;
        record.operationType = OperationTypeEnum.START;
        record.operator = (operator != null) ? operator : "system";
        record.startTime = now;
        record.status = RecordRunStatusEnum.STARTING;
        return record;
    }

    // ========== 链式设置方法（无 set 前缀，返回自身） ==========
    public OperationRecord endTime(LocalDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    public OperationRecord status(RecordRunStatusEnum status) {
        this.status = status;
        return this;
    }

    public OperationRecord resultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
        return this;
    }

    // ========== Getter 方法（只读） ==========
    public String getRecordCode() { return recordCode; }
    public String getCleanerCode() { return cleanerCode; }
    public OperationTypeEnum getOperationType() { return operationType; }
    public String getOperator() { return operator; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public RecordRunStatusEnum getStatus() { return status; }
    public String getResultSummary() { return resultSummary; }
}
