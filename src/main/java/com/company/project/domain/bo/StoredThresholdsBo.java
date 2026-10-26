package com.company.project.domain.bo;
import java.math.BigDecimal;
import java.util.List;
/** 数值统一kg；保存值与已确认生效值分开。 */
public record StoredThresholdsBo(long savedVersion,List<Value> saved,Long appliedVersion,List<Value> applied,String state,String requestId,String commandCode) {
    public StoredThresholdsBo { saved=saved==null?List.of():List.copyOf(saved);applied=applied==null?List.of():List.copyOf(applied); }
    public StoredThresholdsBo(long version,List<Value> saved,Long appliedVersion,List<Value> applied,String state) {
        this(version,saved,appliedVersion,applied,state,null,null);
    }
    public StoredThresholdsBo withReceipt(CleaningCommandStatusBo receipt) {
        return new StoredThresholdsBo(savedVersion,saved,appliedVersion,applied,state,receipt.requestId(),receipt.commandId());
    }
    public record Value(String sensorCode,BigDecimal alarmThresholdKg,BigDecimal safetyValueKg) { }
}
