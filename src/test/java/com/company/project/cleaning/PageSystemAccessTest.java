package com.company.project.cleaning;

import com.company.project.config.PageAccessProperties;
import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.CleaningCommand;
import com.company.project.domain.exception.PageApiException;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.repository.device.DeviceManager;
import com.company.project.repository.device.PageSystemRegistry;
import com.company.project.service.CleaningCommandService;
import com.company.project.service.CleaningStatisticsService;
import com.company.project.service.cleaning.CleaningServiceImpl;
import com.company.project.service.lobby.CleaningLobbyServiceImpl;
import com.company.project.service.safety.CleaningSafetyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PageSystemAccessTest {
    @Test void publicIdentityResolvesOnlyExplicitMappingsAndSeparatesSystemPermission() {
        var lobby = lobby(registry());
        var reader = context("alice", "device:read");
        var system = lobby.pageSystem(reader, "PUBLIC-SYSTEM", "device:read");
        assertEquals("internal-plc", system.deviceId());
        assertEquals("PUBLIC-W1", system.winches().get(0).winchCode());
        assertEquals("internal-w1", system.winches().get(0).winchId());
        assertEquals(2, system.winches().get(0).sensors().get(1).ropeNo());
        assertEquals("SYSTEM_NOT_FOUND", assertThrows(PageApiException.class,
                () -> lobby.pageSystem(reader, "internal-plc", "device:read")).errorCode());
        assertThrows(AccessDeniedException.class,
                () -> lobby.pageSystem(context("bob", "device:read"), "PUBLIC-SYSTEM", "device:read"));
        assertThrows(AccessDeniedException.class,
                () -> lobby.pageSystem(reader, "PUBLIC-SYSTEM", "device:write"));
        assertEquals(1, lobby.pageSystems(reader).size());
        assertTrue(lobby.pageSystems(context("bob", "device:read")).isEmpty());
        assertTrue(lobby.pageSystems(context("alice", "user:read")).isEmpty());
    }

    @Test void missingMappingsNeverCreateDefaultSystemAndPermissionIsCheckedFirst() {
        var lobby = lobby(new PageSystemRegistry(new PageAccessProperties()));
        assertTrue(lobby.pageSystems(context("alice", "device:read")).isEmpty());
        var error = assertThrows(PageApiException.class,
                () -> lobby.pageSystem(context("alice", "device:read"), "PUBLIC-SYSTEM", "device:read"));
        assertEquals(503, error.status());
        assertEquals("SYSTEM_MAPPING_NOT_READY", error.errorCode());
        assertThrows(AccessDeniedException.class,
                () -> lobby.pageSystem(context("alice", "user:read"), "PUBLIC-SYSTEM", "device:read"));
    }

    @Test void commandLookupUsesCommandIdentityAndCannotCrossDevices() {
        var commands = TestServices.commands(new PlcAccessProperties(), new PlcMessageBuffer());
        var now = Instant.now();
        var command = new CleaningCommand("CMD-actual", "internal-plc", "emergency_stop", Map.of(),
                now, "REQ-different", "alice", now.plusSeconds(30));
        commands.submit(command);
        assertEquals("REQ-different", commands.findByCommandCode("internal-plc", "CMD-actual")
                .orElseThrow().requestId());
        assertTrue(commands.findByCommandCode("other-plc", "CMD-actual").isEmpty());
        assertTrue(commands.findByCommandCode("internal-plc", "REQ-different").isEmpty());
    }

    private static CleaningOperationContext context(String username, String permission) {
        return new CleaningOperationContext(null, null, username, Set.of(permission));
    }

    private static CleaningLobbyServiceImpl lobby(PageSystemRegistry registry) {
        var statistics = mock(CleaningStatisticsService.class);
        var cleaning = new CleaningServiceImpl(mock(DeviceManager.class), mock(CleaningCommandService.class), statistics, registry);
        return new CleaningLobbyServiceImpl(cleaning, new CleaningSafetyServiceImpl(), statistics);
    }

    private static PageSystemRegistry registry() {
        var properties = new PageAccessProperties();
        var system = new PageAccessProperties.SystemMapping();
        system.setSystemCode("PUBLIC-SYSTEM");
        system.setSystemName("实际现场名称");
        system.setDeviceId("internal-plc");
        system.setAllowedUsernames(Set.of("alice"));
        for (int group = 1; group <= 4; group++) {
            var winch = new PageAccessProperties.WinchMapping();
            winch.setWinchCode("PUBLIC-W" + group);
            winch.setWinchId("internal-w" + group);
            winch.setGroupNo(group);
            for (int rope = 1; rope <= 2; rope++) {
                var sensor = new PageAccessProperties.SensorMapping();
                sensor.setSensorCode("PUBLIC-S" + group + "-" + rope);
                sensor.setSensorId("internal-s" + group + "-" + rope);
                sensor.setRopeId("internal-r" + group + "-" + rope);
                sensor.setRopeNo(rope);
                winch.getSensors().add(sensor);
            }
            system.getWinches().add(winch);
        }
        properties.setSystems(List.of(system));
        return new PageSystemRegistry(properties);
    }
}
