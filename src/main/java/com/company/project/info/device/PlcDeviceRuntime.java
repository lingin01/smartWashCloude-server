package com.company.project.info.device;

import com.company.project.dto.plc.PlcReadSnapshotDto;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime 是采集事实的唯一副本来源；保存不可变快照，不生成清洗启动或控制命令。 */
@Component
public class PlcDeviceRuntime {
    private final Map<String, PlcDeviceSnapshotBo> devices = new ConcurrentHashMap<>();
    public PlcDeviceSnapshotBo update(PlcReadSnapshotDto input) {
        var snapshot = new PlcDeviceSnapshotBo(input.deviceId(), input.boxId(), input.online(), input.sequence(),
                input.sampledAt(), input.points().stream().map(p -> new PlcDeviceSnapshotBo.Point(p.pointId(),
                p.field(), p.index(), p.ropeId(), p.sensorId(), p.winchId(), p.unit(),
                p.activeValue(), p.value(), p.valid())).toList());
        devices.compute(input.deviceId(), (key, previous) ->
                previous == null || input.sequence() > previous.sequence() ? snapshot : previous);
        return devices.get(input.deviceId());
    }
    public PlcDeviceSnapshotBo snapshot(String deviceId) { return devices.get(deviceId); }
}
