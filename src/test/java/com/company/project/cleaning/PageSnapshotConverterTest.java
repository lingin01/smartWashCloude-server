package com.company.project.cleaning;

import com.company.project.domain.bo.SystemRealtimeStatusBo;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.presentation.web.PageSnapshotConverter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageSnapshotConverterTest {
    private final PageSnapshotConverter converter = new PageSnapshotConverter();

    private PageSystemBo system() {
        return new PageSystemBo("SYS-01", "实际系统名称", "plc-internal", List.of(
                new PageSystemBo.Winch("WINCH-01", "现场一号", "axis-A", 1, List.of(
                        new PageSystemBo.Sensor("SENSOR-01", "左索", "load-A", "rope-A", 1),
                        new PageSystemBo.Sensor("SENSOR-02", "右索", "load-B", "rope-B", 2)))), Set.of("alice"));
    }

    private TelemetrySample sample(SystemRealtimeStatusBo status) {
        return new TelemetrySample("plc-internal", 12L, Instant.parse("2000-01-01T00:00:00Z"),
                List.of(new TelemetrySample.RopeSample("rope-A", "load-A", "axis-A", BigDecimal.ZERO, true),
                        new TelemetrySample.RopeSample("rope-B", "load-B", "axis-A", new BigDecimal("1200"), true)),
                List.of(new TelemetrySample.WinchSample("axis-A", new BigDecimal("8"), new BigDecimal("12"),
                        new BigDecimal("600"), true, false, "", new BigDecimal("2200"), true, true, true,
                        "STANDBY", "STOP", 2)),
                new BigDecimal("600"), new BigDecimal("12"), new BigDecimal("8"), BigDecimal.ZERO,
                status, null, false, 2, 1, null);
    }

    @Test void convertsActualIdentitiesAndKgOnceWithoutDiscardingOldTimestampOrValidZero() throws Exception {
        var state = new SystemRealtimeStatusBo(SystemRealtimeStatusBo.Status.STANDBY, true, true, false, "", "STOP");
        var view = converter.home(system(), Optional.of(sample(state)));
        assertEquals("IDLE", view.systemStatus());
        assertEquals("现场一号", view.winches().get(0).winchName());
        assertEquals("WINCH-01", view.sensors().get(0).winchCode());
        assertEquals(0, view.sensors().get(0).pressure().compareTo(BigDecimal.ZERO));
        assertEquals(0, view.sensors().get(1).pressure().compareTo(new BigDecimal("1.2")));
        assertEquals(0, view.averageTension().compareTo(new BigDecimal("0.6")));
        assertEquals("t", view.sensors().get(0).unit());
        assertEquals("AXIS", view.winches().get(0).speedSource());
        assertEquals(Instant.parse("2000-01-01T00:00:00Z"), view.updatedAt());
        assertEquals("12", view.sequence());
        assertNull(view.isMoving(), "overall or axis state is not independent movement evidence");
        assertNull(view.systemCurrentHeight(), "axis average cannot replace ring height");
        assertNull(view.sensors().get(0).status(), "a valid tension is not a PLC NORMAL signal");
        assertFalse(view.sensors().get(0).statusValid());
        var json = new ObjectMapper().findAndRegisterModules().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .valueToTree(view);
        assertTrue(json.has("systemHeight"));
        assertTrue(json.get("systemHeight").isNull());
        assertTrue(json.get("sensors").get(0).has("alarmThreshold"));
        assertTrue(json.get("sensors").get(0).get("alarmThreshold").isNull());
    }

    @Test void missingSnapshotAndUnknownStateDoNotFabricateOfflineOrNormalReadings() {
        var empty = converter.home(system(), Optional.empty());
        assertFalse(empty.available());
        assertNull(empty.online());
        assertNull(empty.systemStatus());
        assertFalse(empty.statusValid());
        assertEquals(1, empty.winches().size());
        assertEquals(2, empty.sensors().size());
        assertNull(empty.sensors().get(0).pressure());
        assertEquals("NO_MEASUREMENT", empty.sensors().get(0).missingReason());

        var unknown = converter.home(system(), Optional.of(sample(new SystemRealtimeStatusBo(
                SystemRealtimeStatusBo.Status.OFFLINE, true, false, false, "", "UNKNOWN"))));
        assertTrue(unknown.online());
        assertNull(unknown.systemStatus());
        assertNull(unknown.winches().get(0).operatingStatus());
        assertNull(unknown.movementDirection());
    }

    @Test void confirmedConnectionFailureProducesOfflineWithoutUsingElapsedTime() {
        var view = converter.home(system(), Optional.of(sample(new SystemRealtimeStatusBo(
                SystemRealtimeStatusBo.Status.OFFLINE, false, false, false, "", "UNKNOWN"))));
        assertEquals("OFFLINE", view.systemStatus());
        assertTrue(view.statusValid());
        assertNull(view.averageTension());
        assertNull(view.sensors().get(1).pressure());
        assertEquals("OFFLINE", view.sensors().get(1).quality());
    }
}
