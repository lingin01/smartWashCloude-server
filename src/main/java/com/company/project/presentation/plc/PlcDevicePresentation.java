package com.company.project.presentation.plc;

import com.company.project.domain.bo.plc.PlcOperationResultBo;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import com.company.project.info.device.PlcDeviceRuntime;
import com.company.project.service.CloudCleaningService;
import com.company.project.service.CloudCommandService;
import org.springframework.stereotype.Component;

/** 设备表示层将采集 DTO 放入 Runtime，再调用业务入口；写结果不进入采样缓冲。 */
@Component
public class PlcDevicePresentation {
    private final PlcDeviceRuntime runtime;
    private final CloudCleaningService cleaning;
    private final CloudCommandService commands;
    public PlcDevicePresentation(PlcDeviceRuntime runtime, CloudCleaningService cleaning, CloudCommandService commands) {
        this.runtime = runtime; this.cleaning = cleaning; this.commands = commands;
    }
    public void onSnapshot(PlcReadSnapshotDto value) { cleaning.acceptDeviceSnapshot(runtime.update(value)); }
    public void onWriteResult(PlcOperationResultBo result) {
        commands.acceptDeviceResult(result);
        cleaning.acceptDeviceWriteResult(result);
    }
}
