package com.company.project.service;

import com.company.project.domain.bo.CleaningCommand;
import com.company.project.domain.bo.CleaningCommandStatusBo;
import java.util.Optional;
import com.company.project.domain.bo.plc.PlcOperationResultBo;

/** 命令服务聚合登记、查询和超时能力；设备通道未就绪时不得假报已发送。 */
public interface CleaningCommandService {
    /** 只接收清洗服务完成业务校验并生成的命令。 */
    CleaningCommandStatusBo submit(CleaningCommand command);
    Optional<CleaningCommandStatusBo> find(String deviceId, String requestId);
    Optional<CleaningCommandStatusBo> findByCommandCode(String deviceId, String commandCode);
    void runTimeoutCycle();
    CleaningCommandStatusBo acceptDeviceResult(PlcOperationResultBo result);
}
