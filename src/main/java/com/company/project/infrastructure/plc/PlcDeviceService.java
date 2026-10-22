package com.company.project.infrastructure.plc;

import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.plc.PlcOperationResultBo;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.util.*;

/** 网络设备服务只消费已准备的消息，不解析用户 JSON 或计算清洗均值。 */
@Component
public class PlcDeviceService {
    private final PlcAccessProperties properties;
    private final PlcMessageBuffer messages;
    private final Map<String, ModbusTcpDevice> devices = new LinkedHashMap<>();
    private int cursor;
    private volatile boolean stopping;
    public PlcDeviceService(PlcAccessProperties properties, PlcMessageBuffer messages) {
        this.properties = properties; this.messages = messages;
    }
    @PostConstruct public void initialize() {
        if (!properties.isEnabled()) return;
        properties.validate();
        properties.getDevices().stream().filter(PlcAccessProperties.Device::isEnabled)
                .forEach(d -> devices.put(d.getId(), new ModbusTcpDevice(d)));
    }
    public void runCycle() {
        if (stopping || !properties.isEnabled()) return;
        for (int count = 0; count < 8 && !stopping; count++) {
            var message = messages.pollOperation();
            if (message == null) break;
            var operation = message.payload();
            var device = devices.get(operation.deviceId());
            messages.offerResult(device == null
                    ? new PlcOperationResultBo(operation, false, false, 0, "PLC_DEVICE_UNAVAILABLE")
                    : device.write(operation, () -> messages.emergencyWaiting(device.id())));
        }
        List<ModbusTcpDevice> active = List.copyOf(devices.values());
        // 每轮最多轮询一个设备，避免失败 PLC 将所有命令拖入长串阻塞。
        for (int count = 0; count < active.size() && !stopping; count++) {
            var device = active.get(cursor);
            cursor = (cursor + 1) % active.size();
            if (device.due(System.nanoTime())) {
                var snapshot = device.poll(() -> messages.emergencyWaiting(device.id()));
                if (snapshot != null) messages.offerSnapshot(snapshot);
                break;
            }
        }
    }
    @PreDestroy public void shutdown() { stopping = true; devices.values().forEach(ModbusTcpDevice::close); }
    public void cancelQueuedOperations() {
        for (var next = messages.pollOperation(); next != null; next = messages.pollOperation())
            messages.offerResult(new PlcOperationResultBo(next.payload(), false, false, 0, "SERVICE_STOPPED"));
    }
}
