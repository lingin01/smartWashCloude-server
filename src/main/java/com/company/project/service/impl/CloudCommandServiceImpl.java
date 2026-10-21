package com.company.project.service.impl;

import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcOperationResultBo;
import com.company.project.info.buffer.command.CloudCommandRegistry;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.presentation.plc.PlcCommandConverter;
import com.company.project.service.CloudCommandService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Optional;
import static com.company.project.domain.bo.CloudCommandStatusBo.State;

/** 业务命令→内部消息；准备阶段不进行网络 I/O，发送交给 PLC 专用工作者。 */
@Service
public class CloudCommandServiceImpl implements CloudCommandService {
    private final CloudCommandRegistry registry;
    private final PlcCommandConverter converter;
    private final PlcMessageBuffer messages;
    public CloudCommandServiceImpl(CloudCommandRegistry registry, PlcCommandConverter converter, PlcMessageBuffer messages) {
        this.registry = registry; this.converter = converter; this.messages = messages;
    }
    @Override public CloudCommandStatusBo submit(CloudCommand command) {
        if (command == null || command.commandId() == null || command.commandId().isBlank()
                || command.requestId() == null || command.requestId().isBlank()
                || command.boxCode() == null || command.boxCode().isBlank()
                || command.type() == null || command.createdAt() == null || command.expiresAt() == null
                || !command.expiresAt().isAfter(command.createdAt()))
            throw new IllegalArgumentException("操作缺少有效编号、目标或有效期");
        if (registry.find(command.requestId()).isPresent()) return registry.register(command).entry().status();
        if (!Instant.now().isBefore(command.expiresAt()))
            return registry.registerBlocked(command, "COMMAND_EXPIRED", "操作请求已过期，未发送到设备。");
        try {
            var prepared = converter.prepare(command);
            return registry.registerAndOffer(command, prepared, messages, converter.capacity());
        } catch (PlcCommandConverter.NotReady unavailable) {
            String notice = "emergency_stop".equals(command.type())
                    ? "急停点位或通道未就绪，指令未发送；紧急情况请使用现场急停装置。"
                    : "参数点位、编码或版本未就绪，本次参数未发送。";
            return registry.registerBlocked(command, unavailable.code(), notice);
        } catch (IllegalArgumentException invalid) {
            return registry.registerBlocked(command, "PLC_WRITE_CONFIGURATION_INVALID", "设备写点配置或参数无效，本次操作未发送。");
        }
    }
    @Override public Optional<CloudCommandStatusBo> find(String boxId, String requestId) {
        return registry.find(requestId).filter(e -> e.command().boxCode().equals(boxId)).map(CloudCommandRegistry.Entry::status);
    }
    @Override public void acceptDeviceResult(PlcOperationResultBo result) { registry.applyDeviceResult(result); }
    @Override public void runTimeoutCycle() {
        Instant now = Instant.now();
        for (var entry : registry.entries()) if (!entry.status().state().terminal()
                && now.isAfter(entry.command().expiresAt()))
            registry.mark(entry.command().requestId(), State.UNCONFIRMED,
                    "DEVICE_RESULT_UNCONFIRMED", "暂未确认设备操作结果，请检查现场状态。");
    }
}
