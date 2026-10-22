package com.company.project.service.command;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcOperationResultBo;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.repository.device.DeviceManager;
import com.company.project.service.CleaningCommandService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Optional;
/** 命令门面；登记与生命周期只由本模块的CommandState维护。 */
@Service
public class CleaningCommandServiceImpl implements CleaningCommandService {
    private final CommandState state=new CommandState();
    private final CommandSubmission submission;
    public CleaningCommandServiceImpl(DeviceManager devices,PlcMessageBuffer messages) {
        submission=new CommandSubmission(state,new PlcCommandEncoder(devices),messages);
    }
    @Override public CleaningCommandStatusBo submit(CleaningCommand command) { return submission.submit(command); }
    @Override public Optional<CleaningCommandStatusBo> find(String deviceId,String requestId) {
        return state.find(requestId).filter(e->e.command().deviceId().equals(deviceId)).map(CommandState.Entry::status);
    }
    @Override public CleaningCommandStatusBo acceptDeviceResult(PlcOperationResultBo result) {
        state.applyDeviceResult(result);
        return state.find(result.operation().requestId()).orElseThrow().status();
    }
    @Override public void runTimeoutCycle() { state.expire(Instant.now()); }
}
