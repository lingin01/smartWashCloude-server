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
    private final PlcMessageBuffer messages;
    private com.company.project.repository.cleaning.RuntimeHistoryDao audit;
    private com.company.project.info.buffer.cleaning.DurableHistoryInbox inbox;
    private final java.util.Set<String> awaitingAudit=new java.util.HashSet<>();
    private final java.util.Set<String> timeoutAudited=new java.util.HashSet<>();
    public CleaningCommandServiceImpl(DeviceManager devices,PlcMessageBuffer messages) {
        this.messages=messages;
        submission=new CommandSubmission(state,new PlcCommandEncoder(devices),messages);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CleaningCommandServiceImpl(DeviceManager devices,PlcMessageBuffer messages,
            org.springframework.beans.factory.ObjectProvider<com.company.project.repository.cleaning.RuntimeHistoryDao> audit,
            org.springframework.beans.factory.ObjectProvider<com.company.project.info.buffer.cleaning.DurableHistoryInbox> inbox) {
        this(devices,messages);this.audit=audit.getIfAvailable();this.inbox=inbox.getIfAvailable();
    }
    @Override public synchronized CleaningCommandStatusBo submit(CleaningCommand command) {
        if(audit==null)return submission.submit(command);
        var memory=state.find(command.requestId());
        if(memory.isPresent())return state.register(command).entry().status();
        var previous=audit.operation(command.deviceId(),command.requestId(),null);
        if(previous.isPresent()) {
            audit.saveOperation(command,previous.get());
            return previous.get();
        }
        var initial=new CleaningCommandStatusBo(command.requestId(),command.commandId(),command.deviceId(),command.type(),
                CleaningCommandStatusBo.State.CREATED,"CREATED","操作已持久登记",command.createdAt(),command.expiresAt(),Instant.now());
        audit.saveOperation(command,initial);
        var result=submission.submit(command);
        audit.saveOperation(command,result);
        if(result.state().terminal())state.remove(command.requestId());
        return result;
    }
    @Override public Optional<CleaningCommandStatusBo> find(String deviceId,String requestId) {
        var value=state.find(requestId).filter(e->e.command().deviceId().equals(deviceId)).map(CommandState.Entry::status);
        return value.isPresent()||audit==null?value:audit.operation(deviceId,requestId,null);
    }
    @Override public Optional<CleaningCommandStatusBo> findByCommandCode(String deviceId, String commandCode) {
        var value=state.entries().stream().filter(e -> e.command().deviceId().equals(deviceId)
                && e.command().commandId().equals(commandCode)).map(CommandState.Entry::status).findFirst();
        return value.isPresent()||audit==null?value:audit.operation(deviceId,null,commandCode);
    }
    @Override public synchronized CleaningCommandStatusBo acceptDeviceResult(PlcOperationResultBo result) {
        if(audit!=null && state.find(result.operation().requestId()).isEmpty())
            return audit.operation(result.operation().deviceId(),result.operation().requestId(),null).orElseThrow();
        state.applyDeviceResult(result);
        var entry=state.find(result.operation().requestId()).orElseThrow();
        if(audit!=null) {
            String request=result.operation().requestId();
            if(messages.isAuditCommitted(request)) {
                awaitingAudit.remove(request);timeoutAudited.remove(request);state.remove(request);
            } else if(!awaitingAudit.contains(request)) {
                messages.requireAudit(request);
                inbox.append(new com.company.project.domain.bo.telemetry.RunHistoryBo.Write(null,result.operation().deviceId(),0,
                        Instant.now(),null,null,null,java.util.List.of(),entry.command(),entry.status()));
                awaitingAudit.add(request);
            }
        }
        return entry.status();
    }
    @Override public synchronized void runTimeoutCycle() {
        state.expire(Instant.now());
        if(audit==null)return;
        for(var entry:state.entries())if("DEVICE_RESULT_UNCONFIRMED".equals(entry.status().code())&&!timeoutAudited.contains(entry.command().requestId())) {
            // 超时审计不能释放通信结果；迟到应答仍须独立持久化后确认。
            audit.saveOperation(entry.command(),entry.status());
            timeoutAudited.add(entry.command().requestId());
        }
        timeoutAudited.removeIf(request->state.find(request).isEmpty());
    }
}
