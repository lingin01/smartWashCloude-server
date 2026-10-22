package com.company.project.service.command;
import com.company.project.domain.bo.*;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import java.time.Instant;
/** 接收清洗服务生成的命令，只处理重试、过期、准备和非阻塞入队。 */
final class CommandSubmission {
    private final CommandState registry;
    private final PlcCommandEncoder converter;
    private final PlcMessageBuffer messages;
    CommandSubmission(CommandState registry, PlcCommandEncoder converter, PlcMessageBuffer messages) {
        this.registry=registry; this.converter=converter; this.messages=messages;
    }
    public CleaningCommandStatusBo submit(CleaningCommand command) {
        if (registry.find(command.requestId()).isPresent()) return registry.register(command).entry().status();
        if (!Instant.now().isBefore(command.expiresAt()))
            return registry.registerBlocked(command, "COMMAND_EXPIRED", "操作请求已过期，未发送到设备。");
        try {
            var prepared = converter.prepare(command);
            return registry.registerAndOffer(command, prepared, messages, converter.capacity());
        } catch (PlcCommandEncoder.NotReady unavailable) {
            String notice = "emergency_stop".equals(command.type())
                    ? "急停点位或通道未就绪，指令未发送；紧急情况请使用现场急停装置。"
                    : "参数点位、编码或版本未就绪，本次参数未发送。";
            return registry.registerBlocked(command, unavailable.code(), notice);
        } catch (IllegalArgumentException invalid) {
            return registry.registerBlocked(command, "PLC_WRITE_CONFIGURATION_INVALID", "设备写点配置或参数无效，本次操作未发送。");
        }
    }
}
