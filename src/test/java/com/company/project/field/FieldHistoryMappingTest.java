package com.company.project.field;

import com.company.project.domain.bo.SystemRealtimeStatusBo;
import com.company.project.domain.bo.TelemetrySample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** No network or database: verify that the field adapter cannot invent measurements or round identities. */
class FieldHistoryMappingTest {
    @TempDir Path directory;

    private static TelemetrySample sample(String device, long sequence) {
        return new TelemetrySample(device, sequence, Instant.parse("2026-10-26T01:02:03.123456Z"),
                List.of(new TelemetrySample.RopeSample("rope-1", "sensor-1", "winch-1", new BigDecimal("12.34567"), true),
                        new TelemetrySample.RopeSample("rope-2", "sensor-2", "winch-1", new BigDecimal("999"), false),
                        new TelemetrySample.RopeSample("", "", "", null, false)),
                List.of(new TelemetrySample.WinchSample("winch-1", null, new BigDecimal("2.5"), null,
                        true, false, "", new BigDecimal("999"), false, false, true, "STANDBY", "STOP", 1)),
                null, null, null, null,
                new SystemRealtimeStatusBo(SystemRealtimeStatusBo.Status.STANDBY, true, true, false, "", "STOP"),
                new BigDecimal("999"), false, 1, 1, null);
    }

    @Test void keepsOnlyMeasuredValuesAndPreservesMissingChannelsAsNull() {
        var batch = FieldHistoryPersistenceTest.batch(List.of(sample("plc-1", 10)), "field-run", 1);
        assertEquals(10, batch.firstSequence());
        assertEquals(10, batch.lastSequence());
        var segment = batch.segments().get(0);
        assertEquals("field-run", segment.sessionId());
        assertEquals(0, segment.roundNo());
        assertEquals("STOP", segment.direction());
        assertEquals("FIELD_CAPTURE", segment.triggerReason());
        assertEquals(Instant.parse("2026-10-26T01:02:03.123Z"), segment.startedAt());
        assertNull(segment.startHeightMeters());
        assertEquals(2, segment.ropeTensions().size());
        assertEquals(new BigDecimal("12.34567"), segment.ropeTensions().get(0).latest());
        assertEquals(1, segment.ropeTensions().get(0).sampleCount());
        assertNull(segment.ropeTensions().get(1).latest());
        assertEquals(0, segment.ropeTensions().get(1).sampleCount());
        assertEquals(new BigDecimal("2.5"), segment.winchSpeeds().get(0).latest());
        assertNull(segment.winchTorqueForcesKg().get(0).latest());
    }

    @Test void rejectsEmptyDuplicateSequenceMixedDeviceOrMissingMetricIdentityBeforeDatabaseUse() throws Exception {
        Path source = directory.resolve("realtime.ndjson");
        Files.writeString(source, "\n");
        assertThrows(IllegalArgumentException.class, () -> FieldHistoryPersistenceTest.validateInput(source));
        write(source, sample("plc-1", 1), sample("plc-1", 1));
        assertThrows(IllegalArgumentException.class, () -> FieldHistoryPersistenceTest.validateInput(source));
        write(source, sample("plc-1", 1), sample("plc-2", 2));
        assertThrows(IllegalArgumentException.class, () -> FieldHistoryPersistenceTest.validateInput(source));
        String missingId = FieldHistoryPersistenceTest.JSON.writeValueAsString(sample("plc-1", 1))
                .replace("\"ropeId\":\"rope-1\"", "\"ropeId\":\"\"");
        Files.writeString(source, missingId + "\n");
        assertThrows(IllegalArgumentException.class, () -> FieldHistoryPersistenceTest.validateInput(source));
        var noMeasurements = new TelemetrySample("plc-1", 1, Instant.parse("2026-10-26T01:02:03Z"),
                List.of(), List.of(), null, null, null, null, null, null, false, 0, 0, null);
        write(source, noMeasurements);
        assertThrows(IllegalArgumentException.class, () -> FieldHistoryPersistenceTest.validateInput(source));
    }

    @Test void acceptsOrderedCaptureWithoutChangingSource() throws Exception {
        Path source = directory.resolve("realtime.ndjson");
        write(source, sample("plc-1", 1), sample("plc-1", 2));
        String before = Files.readString(source);
        var info = FieldHistoryPersistenceTest.validateInput(source);
        assertEquals("plc-1", info.deviceId());
        assertEquals(2, info.samples());
        assertEquals(before, Files.readString(source));
    }

    private static void write(Path source, TelemetrySample... samples) throws Exception {
        StringBuilder result = new StringBuilder();
        for (var sample : samples) result.append(FieldHistoryPersistenceTest.JSON.writeValueAsString(sample)).append('\n');
        Files.writeString(source, result);
    }
}
