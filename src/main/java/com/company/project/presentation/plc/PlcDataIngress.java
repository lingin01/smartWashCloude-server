package com.company.project.presentation.plc;

import com.company.project.config.PlcAccessProperties;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
import com.company.project.infrastructure.plc.PlcDeviceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** 上层接入任务主动取缓冲并调用表示层；底层驱动/线程中心不依赖业务服务。 */
@Component
public class PlcDataIngress {
    private final PlcAccessProperties properties;
    private final BackgroundWorkerRegistrar workers;
    private final PlcDeviceService devices;
    private final PlcMessageBuffer messages;
    private final PlcDevicePresentation presentation;
    private volatile boolean active;
    public PlcDataIngress(PlcAccessProperties properties, BackgroundWorkerRegistrar workers,
            PlcDeviceService devices, PlcMessageBuffer messages, PlcDevicePresentation presentation) {
        this.properties = properties; this.workers = workers; this.devices = devices;
        this.messages = messages; this.presentation = presentation;
    }
    @PostConstruct public void start() {
        if (!properties.isEnabled()) return;
        if (!workers.registerDedicatedPeriodic("plc-device-dispatch", this::dispatchCycle, Duration.ofMillis(50)))
            throw new IllegalStateException("PLC 结果任务重复登记");
        try {
            if (!workers.registerDedicatedPeriodic("plc-device-io", () -> { if (active) devices.runCycle(); },
                    Duration.ofMillis(properties.getWorkerIntervalMs())))
                throw new IllegalStateException("PLC I/O 任务重复登记");
        } catch (RuntimeException failure) {
            workers.unregister("plc-device-dispatch");
            throw failure;
        }
        active = true;
    }
    public void dispatchCycle() {
        if (!active) return;
        for (int index = 0; index < 128; index++) {
            var next = messages.peekResult();
            if (next == null) break;
            presentation.onWriteResult(next.payload());
            if(!messages.completeResult(next.messageId()))break;
        }
        for (int index = 0; index < 128; index++) {
            var snapshot = messages.peekOrderedSnapshot();
            if (snapshot == null) break;
            presentation.onSnapshot(snapshot.payload());
            messages.completeSnapshot(snapshot.messageId());
        }
    }
    @PreDestroy public void stop() {
        active = false;
        messages.stopAccepting();
        devices.shutdown();
        workers.unregister("plc-device-io");
        workers.unregister("plc-device-dispatch");
        devices.cancelQueuedOperations();
        // 工作者停止后再有限处理写结果，未发送请求不能留成虚假的执行中状态。
        for (int index = 0; index < properties.getQueueCapacity(); index++) {
            var result = messages.peekResult();
            if (result == null) break;
            presentation.onWriteResult(result.payload());
            if(!messages.completeResult(result.messageId()))break;
        }
    }
}
