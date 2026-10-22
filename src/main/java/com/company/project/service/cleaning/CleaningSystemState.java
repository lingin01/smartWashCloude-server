package com.company.project.service.cleaning;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcOperationResultBo;
import java.util.*;
/** 清洗服务私有的系统状态；其他模块只能取得不可变快照，没有共享写入口。 */
final class CleaningSystemState {
    private final Map<String, TelemetrySample> latest = new HashMap<>();
    private final Map<String, CleaningSettingsViewBo> settings = new HashMap<>();
    synchronized boolean update(TelemetrySample sample) {
        var old=latest.get(sample.deviceId());
        if (old != null && sample.sequence() <= old.sequence()) return false;
        latest.put(sample.deviceId(), sample);
        return true;
    }
    synchronized Optional<TelemetrySample> latest(String deviceId) { return Optional.ofNullable(latest.get(deviceId)); }
    synchronized CleaningSettingsViewBo settings(String deviceId) {
        return settings.getOrDefault(deviceId,new CleaningSettingsViewBo(deviceId,"NOT_SYNCHRONIZED",null,null));
    }
    synchronized void recordWrittenSettings(PlcOperationResultBo result) {
        settings.put(result.operation().deviceId(),new CleaningSettingsViewBo(result.operation().deviceId(),
                "WRITTEN_NOT_PERSISTED",null,result.operation().settings()));
    }
}
