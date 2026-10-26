package com.company.project.presentation.plc;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
/** PLC表示层只转换传输对象，不判断业务状态或维护设备事实。 */
final class PlcSnapshotConverter {
    PlcDeviceSnapshotBo convert(PlcReadSnapshotDto input) {
        return new PlcDeviceSnapshotBo(input.deviceId(),input.online(),input.sequence(),input.sampledAt(),
                input.points().stream().map(p->new PlcDeviceSnapshotBo.Point(p.pointId(),p.field(),p.index(),
                        p.ropeId(),p.sensorId(),p.winchId(),p.unit(),p.activeValue(),p.value(),p.valid(),p.rawValue())).toList());
    }
}
