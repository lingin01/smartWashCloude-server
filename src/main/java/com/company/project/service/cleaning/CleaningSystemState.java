package com.company.project.service.cleaning;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcOperationResultBo;
import java.util.*;
/** 清洗服务私有的系统状态；其他模块只能取得不可变快照，没有共享写入口。 */
final class CleaningSystemState {
    private final Map<String, TelemetrySample> latest = new HashMap<>();
    private final Set<String> restored=new HashSet<>();
    private final Map<String, CleaningSettingsViewBo> settings = new HashMap<>();
    synchronized boolean update(TelemetrySample sample) {
        var old=latest.get(sample.deviceId());
        if (old != null && !restored.contains(sample.deviceId()) && sample.sequence() <= old.sequence()) return false;
        restored.remove(sample.deviceId());
        latest.put(sample.deviceId(), sample);
        return true;
    }
    synchronized Optional<TelemetrySample> latest(String deviceId) { return Optional.ofNullable(latest.get(deviceId)); }
    synchronized boolean isRestored(String device){return restored.contains(device);}
    synchronized void restore(TelemetrySample sample) {
        if(latest.containsKey(sample.deviceId()))return;
        boolean maintenance=sample.status().systemStatus()==SystemRealtimeStatusBo.Status.MAINTENANCE;
        var status=new SystemRealtimeStatusBo(maintenance?SystemRealtimeStatusBo.Status.MAINTENANCE:SystemRealtimeStatusBo.Status.OFFLINE,
                false,maintenance,false,sample.status().faultCode(),"STOP",sample.status().source());
        var winches=sample.winches().stream().map(w->new TelemetrySample.WinchSample(w.winchId(),w.heightMeters(),w.speed(),w.tensionAverage(),
                false,false,w.faultCode(),w.torqueKg(),false,false,false,maintenance?"MAINTENANCE":"OFFLINE","STOP",0)).toList();
        var ropes=sample.ropes().stream().map(r->new TelemetrySample.RopeSample(r.ropeId(),r.sensorId(),r.winchId(),r.tension(),false)).toList();
        latest.put(sample.deviceId(),new TelemetrySample(sample.deviceId(),sample.sequence(),sample.sampledAt(),ropes,winches,sample.groupAverageTension(),
                sample.groupAverageSpeed(),sample.groupAverageHeight(),sample.heightSpread(),status,sample.ringHeightMeters(),false,0,0,sample.groupSpeedMetersPerMinute(),sample.captureId()));
        restored.add(sample.deviceId());
    }
    synchronized CleaningSettingsViewBo settings(String deviceId) {
        return settings.getOrDefault(deviceId,new CleaningSettingsViewBo(deviceId,"NOT_SYNCHRONIZED",null,null));
    }
    synchronized void recordWrittenSettings(PlcOperationResultBo result) {
        settings.put(result.operation().deviceId(),new CleaningSettingsViewBo(result.operation().deviceId(),
                "WRITTEN_NOT_PERSISTED",null,result.operation().settings()));
    }
}
