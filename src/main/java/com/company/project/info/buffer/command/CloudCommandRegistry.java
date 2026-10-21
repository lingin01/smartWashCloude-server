package com.company.project.info.buffer.command;

import com.company.project.domain.bo.CloudCommand;
import com.company.project.domain.bo.CloudCommandStatusBo;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import com.company.project.domain.bo.plc.*;
import com.company.project.domain.message.Message;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import static com.company.project.domain.bo.CloudCommandStatusBo.State;

/** 命令登记聚合：只操作内存，短临界区内不调用设备网络、数据库或其他业务服务。 */
@Component
public class CloudCommandRegistry {
    public record Entry(CloudCommand command, CloudCommandStatusBo status) { }
    public record Registration(Entry entry, boolean inserted) { }
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, String> pendingSettings = new HashMap<>();

    public synchronized Registration register(CloudCommand command) {
        var previous = entries.get(command.requestId());
        if (previous != null) {
            var original = previous.command();
            if (!Objects.equals(original.boxCode(), command.boxCode())
                    || !Objects.equals(original.operatorId(), command.operatorId())
                    || !original.type().equals(command.type())
                    || !original.parameters().equals(command.parameters())) {
                throw new IllegalArgumentException("请求编号已被其他操作使用，请使用新的 requestId");
            }
            return new Registration(previous, false);
        }
        entries.values().removeIf(entry -> (entry.status().state().terminal()
                || entry.status().state() == State.UNCONFIRMED)
                && entry.status().updatedAt().isBefore(Instant.now().minusSeconds(86400)));
        if (entries.size() >= 4096) throw new IllegalStateException("命令登记缓冲已满");
        var status = new CloudCommandStatusBo(command.requestId(), command.commandId(), null,
                command.boxCode(), command.type(), State.CREATED, "CREATED", "请求已登记",
                command.createdAt(), command.expiresAt(), Instant.now(), 0);
        var entry = new Entry(command, status);
        entries.put(command.requestId(), entry);
        return new Registration(entry, true);
    }

    /** 同一监视器内登记并给出未就绪结果，不让并发重试看到暂时的已受理状态。 */
    public synchronized CloudCommandStatusBo registerBlocked(CloudCommand command, String code, String message) {
        var registration = register(command);
        if (!registration.inserted()) return registration.entry().status();
        return mark(command.requestId(), State.BLOCKED, code, message);
    }

    public synchronized Optional<Entry> find(String requestId) {
        return Optional.ofNullable(entries.get(requestId));
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public synchronized CloudCommandStatusBo mark(String requestId, State next, String code, String message) {
        var entry = entries.get(requestId);
        if (entry == null) throw new IllegalArgumentException("操作记录不存在");
        var old = entry.status();
        // 终态不会被迟到消息回退，也不根据枚举数值推导阶段。
        if (!old.state().canAdvanceTo(next) || old.state().terminal()) return old;
        var updated = new CloudCommandStatusBo(old.requestId(), old.cloudCommandId(), old.boxCommandId(),
                old.boxId(), old.type(), next, code, message, old.createdAt(), old.expiresAt(),
                Instant.now(), old.resourceVersion());
        entries.put(requestId, new Entry(entry.command(), updated));
        return updated;
    }
    /** 唯一锁顺序：登记中心→非阻塞消息 offer；消费者先释放队列锁再处理登记结果。 */
    public synchronized CloudCommandStatusBo registerAndOffer(CloudCommand command, Message<PlcOperationBo> message,
            PlcMessageBuffer messages, int capacity) {
        var registration = register(command);
        if (!registration.inserted()) return registration.entry().status();
        var operation = message.payload();
        if (!operation.emergency()) {
            if (pendingSettings.containsKey(command.boxCode()))
                return mark(command.requestId(), State.REJECTED, "SETTINGS_IN_PROGRESS", "已有参数修改尚未确认，请先核对现场配置。");
            if (operation.initialVersion() == null || operation.expectedVersion() != operation.initialVersion())
                return mark(command.requestId(), State.REJECTED, "CONFIGURATION_VERSION_CONFLICT", "配置版本已变化，请核对后重试。");
        }
        if (!messages.offer(message, capacity))
            return mark(command.requestId(), State.REJECTED, "SEND_QUEUE_FULL", "设备消息队列已满或正在停止，未发送。");
        if (!operation.emergency()) pendingSettings.put(command.boxCode(), command.requestId());
        return mark(command.requestId(), State.QUEUED, "QUEUED", "操作已进入设备队列，尚未确认写入。");
    }

    public synchronized void applyDeviceResult(PlcOperationResultBo result) {
        var operation = result.operation();
        var entry = entries.get(operation.requestId());
        if (entry == null || !entry.command().commandId().equals(operation.commandId())
                || !entry.command().boxCode().equals(operation.boxId()))
            throw new IllegalArgumentException("设备写结果无法关联原操作");
        if (result.success()) {
            if (operation.emergency()) {
                mark(operation.requestId(), State.SUCCEEDED, "PLC_EMERGENCY_WRITE_CONFIRMED",
                        "急停请求写入已确认，设备是否停止仍需查看实际状态。");
            } else {
                // 配置 DAO 尚未接入：保留在途标记，不允许另一用户用旧版本继续覆盖。
                mark(operation.requestId(), State.UNCONFIRMED, "PLC_SETTINGS_WRITTEN_STORAGE_PENDING",
                        "PLC 参数写入已确认，但配置版本尚未持久保存，不能认定整个修改流程完成。");
            }
        } else if (result.uncertain() || result.confirmedWrites() > 0) {
            mark(operation.requestId(), State.UNCONFIRMED, result.code(),
                    "写入结果不确定或存在部分写入，请核对现场；不会自动重发或回滚。");
        } else {
            if (!operation.emergency()) pendingSettings.remove(operation.boxId(), operation.requestId());
            mark(operation.requestId(), State.FAILED, result.code(), "本次设备写入未完成，请检查配置及现场情况。");
        }
    }
}
