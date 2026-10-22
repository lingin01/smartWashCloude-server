package com.company.project.cleaning;

import com.company.project.domain.bo.*;
import com.company.project.domain.param.cleaning.*;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.config.PlcAccessProperties;
import com.company.project.service.command.CleaningCommandServiceImpl;
import com.company.project.service.cleaning.CleaningServiceImpl;
import com.company.project.service.lobby.CleaningLobbyServiceImpl;
import com.company.project.service.safety.CleaningSafetyServiceImpl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import static com.company.project.domain.bo.CleaningCommandStatusBo.State;
import static org.junit.jupiter.api.Assertions.*;

/** 不连接设备：清理旧通信后，验证未就绪、幂等与终态不会伪装成发送成功。 */
class CleaningLocalCommandReadinessTest {
    private static CleaningCommandServiceImpl commands() {
        return TestServices.commands(new PlcAccessProperties(),new PlcMessageBuffer());
    }
    private static CleaningCommand request(String requestId, String reason) {
        Instant now = Instant.now();
        return new CleaningCommand(UUID.randomUUID().toString(), "plc-001",
                "emergency_stop", Map.of("reason", reason), now, requestId, "alice",
                now.plusSeconds(30));
    }

    @Test
    void unavailableChannelIsBlockedAndQueryable() {
        var commands = commands();
        var status = commands.submit(request("req-1", "现场确认"));
        assertEquals(State.BLOCKED, status.state());
        assertEquals("PLC_DOWNLINK_NOT_READY", status.code());
        assertEquals(status, commands.find("plc-001", "req-1").orElseThrow());
        assertTrue(commands.find("plc-other", "req-1").isEmpty());
    }

    @Test
    void bothAllowedOperationsRemainBlockedWithoutDeviceChannel() {
        var commands = commands();
        var cleaning = TestServices.cleaning(commands);
        var lobby = TestServices.lobby(cleaning);
        var stopContext = new CleaningOperationContext("stop-1", "plc-001", "alice", Set.of("device:write"));
        var settingsContext = new CleaningOperationContext("settings-1", "plc-001", "alice", Set.of("device:write"));
        var emergency = lobby.emergencyStop(new EmergencyStopParam(stopContext, "现场急停"));
        var settings = new CleaningSettingsBo(LocalTime.of(8, 0), 3600L, new BigDecimal("6"));
        var update = lobby.updateSettings(new SettingsUpdateParam(settingsContext, 0, settings));
        assertEquals(State.BLOCKED, emergency.state());
        assertEquals(State.BLOCKED, update.state());
        assertEquals("PLC_DOWNLINK_NOT_READY", emergency.code());
        assertEquals("PLC_DOWNLINK_NOT_READY", update.code());
        assertEquals("NOT_SYNCHRONIZED", cleaning.settings("plc-001").state());
    }

    @Test
    void retriesKeepOriginalIdentityAndRejectChangedContent() {
        var commands = commands();
        var original = commands.submit(request("same-request", "同一操作"));
        assertEquals(original, commands.submit(request("same-request", "同一操作")));
        assertThrows(IllegalArgumentException.class,
                () -> commands.submit(request("same-request", "不同操作")));
    }

    @Test
    void concurrentRetriesNeverObserveCreatedOrQueuedState() throws Exception {
        var commands = commands();
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        List<Future<CleaningCommandStatusBo>> futures = new ArrayList<>();
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
                if (commandId == null) commandId = result.commandId();
                assertEquals(commandId, result.commandId());
            }
            } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void terminalStateCannotBeChangedByLateStateUpdates() {
        var commands=commands();
        var request=request("terminal-request","测试");
        var status=commands.submit(request);
        var late=new com.company.project.domain.bo.plc.PlcOperationBo(request.requestId(),request.commandId(),
                request.deviceId(),request.type(),request.expiresAt(),0,null,null,List.of());
        commands.acceptDeviceResult(new com.company.project.domain.bo.plc.PlcOperationResultBo(late,true,false,1,"LATE"));
        assertEquals(status,commands.find(request.deviceId(),request.requestId()).orElseThrow());
    }
}
