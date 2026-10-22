package com.company.project.cleaning;

import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.domain.param.cleaning.EmergencyStopParam;
import com.company.project.info.buffer.cleaning.TelemetryHistoryBuffer;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.service.*;
import com.company.project.service.cleaning.CleaningServiceImpl;
import com.company.project.service.lobby.CleaningLobbyServiceImpl;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessOwnershipTest {
    static PlcDeviceSnapshotBo snapshot(long sequence, String tension, boolean valid) {
        return new PlcDeviceSnapshotBo("plc-001", true, sequence, Instant.parse("2026-10-22T00:00:00Z").plusSeconds(sequence),
                List.of(PlcMessageFlowTest.point("manualMode", 1, "", "", "", "0"),
                        PlcMessageFlowTest.point("running", 1, "", "", "", "1"),
                        new PlcDeviceSnapshotBo.Point("rope-1", "ropeTension", 1, "R1", "S1", "W1", "kg",
                                BigDecimal.ONE, tension == null ? null : new BigDecimal(tension), valid)));
    }

    @Test void cleaningOwnsLatestStateAndOnlyAcceptedFactsReachStatistics() {
        var devices = TestServices.devices(new PlcAccessProperties());
        var commands = TestServices.commands(new PlcAccessProperties(), new PlcMessageBuffer());
        var statistics = new CleaningStatisticsServiceImpl(new TelemetryHistoryBuffer(), mock(TelemetryHistoryDao.class));
        var cleaning = new CleaningServiceImpl(devices, commands, statistics);
        cleaning.acceptDeviceSnapshot(snapshot(1, "100", true));
        cleaning.acceptDeviceSnapshot(snapshot(2, "200", true));
        cleaning.acceptDeviceSnapshot(snapshot(1, "999", true));
        assertEquals(2, cleaning.latest("plc-001").orElseThrow().sequence());
        assertEquals(2, devices.snapshot("plc-001").orElseThrow().sequence());
        cleaning.acceptDeviceSnapshot(snapshot(3, null, false));
        var summary = statistics.statistics("plc-001");
        assertEquals(3, summary.acceptedSamples());
        assertEquals("PROCESS", summary.scope());
        var metric = summary.metrics().stream().filter(m -> m.metric().equals("ropeTension")).findFirst().orElseThrow();
        assertEquals(2, metric.validSamples());
        assertEquals(0, metric.minimum().compareTo(new BigDecimal("100")));
        assertEquals(0, metric.maximum().compareTo(new BigDecimal("200")));
        assertEquals(0, metric.sampleAverage().compareTo(new BigDecimal("150")));
        assertThrows(UnsupportedOperationException.class, () -> summary.metrics().clear());
        assertThrows(UnsupportedOperationException.class, () -> cleaning.latest("plc-001").orElseThrow().ropes().clear());
        assertEquals(0, statistics.statistics("other-plc").acceptedSamples());
    }

    @Test void operationsCheckSafetyBeforeCleaningAndDeniedOperationsNeverReachCleaning() {
        var cleaning = mock(CleaningService.class);
        var safety = mock(CleaningSafetyService.class);
        var statistics = mock(CleaningStatisticsService.class);
        var lobby = new CleaningLobbyServiceImpl(cleaning, safety, statistics);
        var context = new CleaningOperationContext("req-1", "plc-001", "alice", Set.of("device:write"));
        var request = new EmergencyStopParam(context, "test");
        lobby.emergencyStop(request);
        var order = inOrder(safety, cleaning);
        order.verify(safety).verifyEmergencyStop(context);
        order.verify(cleaning).emergencyStop(request);
        reset(cleaning, safety);
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(safety).verifyEmergencyStop(context);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> lobby.emergencyStop(request));
        verifyNoInteractions(cleaning, statistics);
    }

    @Test void ordinaryQueriesAndStatisticsUseTheirRespectiveServices() {
        var cleaning = mock(CleaningService.class);
        var safety = mock(CleaningSafetyService.class);
        var statistics = mock(CleaningStatisticsService.class);
        var lobby = new CleaningLobbyServiceImpl(cleaning, safety, statistics);
        var context = new CleaningOperationContext(null, "plc-001", "alice", Set.of("device:read"));
        lobby.queryRealtime(context);
        verify(cleaning).latest("plc-001");
        verifyNoInteractions(statistics);
        lobby.queryStatistics(context);
        verify(statistics).statistics("plc-001");
        verify(safety, times(2)).verifyRead(context);
    }
}
