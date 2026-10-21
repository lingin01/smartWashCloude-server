package com.company.project.domain.bo.plc;

import com.company.project.domain.bo.CloudCleaningSettingsBo;
import java.time.Instant;
import java.util.List;

/** 一次逻辑操作及其子写请求；网络结果只能确认写入，不能确认设备已经停止。 */
public record PlcOperationBo(String requestId, String commandId, String boxId, String deviceId,
        String type, Instant expiresAt, long expectedVersion, Long initialVersion,
        CloudCleaningSettingsBo settings, List<Write> writes) {
    public PlcOperationBo { writes = List.copyOf(writes); }
    public boolean emergency() { return "emergency_stop".equals(type); }
    public record Write(int functionCode, int address, List<Integer> registers, Boolean coil) {
        public Write { registers = List.copyOf(registers); }
    }
}
