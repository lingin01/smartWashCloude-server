package com.company.project.service;

import com.company.project.domain.bo.CloudCommand;
import com.company.project.domain.bo.CloudCommandStatusBo;
import java.util.Optional;
import com.company.project.domain.bo.plc.PlcOperationResultBo;

/** 命令服务聚合登记、查询和超时能力；设备通道未就绪时不得假报已发送。 */
public interface CloudCommandService {
    CloudCommandStatusBo submit(CloudCommand command);
    Optional<CloudCommandStatusBo> find(String boxId, String requestId);
    void runTimeoutCycle();
    void acceptDeviceResult(PlcOperationResultBo result);
}
