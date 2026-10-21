package com.company.project.cleaning;

import com.company.project.domain.bo.*;
import com.company.project.domain.param.cloud.*;
import com.company.project.info.buffer.cleaning.CloudRuntimeDataCenter;
import com.company.project.info.buffer.command.CloudCommandRegistry;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.config.PlcAccessProperties;
import com.company.project.presentation.plc.PlcCommandConverter;
import com.company.project.service.impl.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import static com.company.project.domain.bo.CloudCommandStatusBo.State;
import static org.junit.jupiter.api.Assertions.*;

/** 不连接设备：清理旧通信后，验证未就绪、幂等与终态不会伪装成发送成功。 */
class CleaningLocalCommandReadinessTest {
    private static CloudCommandServiceImpl commands(CloudCommandRegistry registry) {
        return new CloudCommandServiceImpl(registry, new PlcCommandConverter(new PlcAccessProperties()), new PlcMessageBuffer());
    }
    private static CloudCommand request(String requestId, String reason) {
        Instant now = Instant.now();
        return new CloudCommand(UUID.randomUUID().toString(), null, "box-001",
                "emergency_stop", Map.of("reason", reason), now, requestId, "alice",
                now.plusSeconds(30));
    }

    @Test
    void unavailableChannelIsBlockedAndQueryable() {
        var registry = new CloudCommandRegistry();
        var commands = commands(registry);
        var status = commands.submit(request("req-1", "现场确认"));
        assertEquals(State.BLOCKED, status.state());
        assertEquals("PLC_DOWNLINK_NOT_READY", status.code());
        assertNull(status.boxCommandId());
        assertEquals(status, commands.find("box-001", "req-1").orElseThrow());
        assertTrue(commands.find("box-other", "req-1").isEmpty());
    }

    @Test
    void bothAllowedOperationsRemainBlockedWithoutDeviceChannel() {
        var commands = commands(new CloudCommandRegistry());
        var cleaning = new CloudCleaningServiceImpl(commands, new CloudRuntimeDataCenter());
        var lobby = new CloudLobbyServiceImpl(cleaning, new CloudSafetyServiceImpl());
        var stopContext = new CloudOperationContext("stop-1", "box-001", "alice", Set.of("device:write"));
        var settingsContext = new CloudOperationContext("settings-1", "box-001", "alice", Set.of("device:write"));
        var emergency = lobby.emergencyStop(new CloudEmergencyStopParam(stopContext, null, "现场急停"));
        var settings = new CloudCleaningSettingsBo(LocalTime.of(8, 0), 3600L, null, new BigDecimal("6"));
        var update = lobby.updateSettings(new CloudSettingsUpdateParam(settingsContext, 0, settings));
        assertEquals(State.BLOCKED, emergency.state());
        assertEquals(State.BLOCKED, update.state());
        assertEquals("PLC_DOWNLINK_NOT_READY", emergency.code());
        assertEquals("PLC_DOWNLINK_NOT_READY", update.code());
        assertEquals("NOT_SYNCHRONIZED", cleaning.settings("box-001").state());
    }

    @Test
    void retriesKeepOriginalIdentityAndRejectChangedContent() {
        var registry = new CloudCommandRegistry();
        var commands = commands(registry);
        var original = commands.submit(request("same-request", "同一操作"));
        assertEquals(original, commands.submit(request("same-request", "同一操作")));
        assertEquals(1, registry.entries().size());
        assertThrows(IllegalArgumentException.class,
                () -> commands.submit(request("same-request", "不同操作")));
    }

    @Test
    void concurrentRetriesNeverObserveCreatedOrQueuedState() throws Exception {
        var registry = new CloudCommandRegistry();
        var commands = commands(registry);
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        List<Future<CloudCommandStatusBo>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 16; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return commands.submit(request("parallel-request", "同一操作"));
                }));
            }
            start.countDown();
            String commandId = null;
            for (var future : futures) {
                var result = future.get(3, TimeUnit.SECONDS);
                assertEquals(State.BLOCKED, result.state());
                if (commandId == null) commandId = result.cloudCommandId();
                assertEquals(commandId, result.cloudCommandId());
            }
            assertEquals(1, registry.entries().size());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void terminalStateCannotBeChangedByLateStateUpdates() {
        var registry = new CloudCommandRegistry();
        var status = registry.registerBlocked(request("terminal-request", "测试"),
                "PLC_DOWNLINK_NOT_READY", "未发送");
        assertEquals(status, registry.mark("terminal-request", State.QUEUED, "LATE", "迟到更新"));
        assertEquals(status, registry.mark("terminal-request", State.SUCCEEDED, "FALSE", "不应生效"));
    }
}
