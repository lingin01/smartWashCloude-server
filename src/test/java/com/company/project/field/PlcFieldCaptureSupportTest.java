package com.company.project.field;

import com.company.project.config.PlcAccessProperties;
import com.company.project.config.PlcAccessProperties.*;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 只访问本机回环端口，验证现场工具确实沿用实际驱动与业务转换。 */
class PlcFieldCaptureSupportTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mainReadsYamlAndReportsSuccessOrInvalidMeasurements(boolean rejectMeasurements) throws Exception {
        try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            var peer = reply(server, rejectMeasurements);
            Path plc = directory.resolve("plc.yml");
            Path config = directory.resolve("field-test.yml");
            Path output = directory.resolve("results");
            // JSON也是有效YAML；此配置刻意不包含数据库、JWT或接口登录信息。
            var pointMapJson = PlcFieldCapture.JSON.copy().setSerializationInclusion(
                    com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
            Files.writeString(plc, pointMapJson.writeValueAsString(
                    java.util.Map.of("cleaning", java.util.Map.of("plc", configured(server.getLocalPort())))));
            Files.writeString(config, PlcFieldCapture.JSON.writeValueAsString(java.util.Map.of("field",
                    java.util.Map.of("device-id", "field-plc", "duration-seconds", 1,
                            "output-dir", output.toString(), "plc", java.util.Map.of("config", plc.toString())))));
            String old = System.getProperty("field.config");
            try {
                System.setProperty("field.config", config.toString());
                if (rejectMeasurements) assertThrows(IllegalStateException.class,
                        () -> PlcFieldCapture.main(new String[0]));
                else assertDoesNotThrow(() -> PlcFieldCapture.main(new String[0]));
            } finally {
                if (old == null) System.clearProperty("field.config");
                else System.setProperty("field.config", old);
            }
            peer.get(3, TimeUnit.SECONDS);
            try (var results = Files.list(output)) {
                var summary = PlcFieldCapture.JSON.readTree(results.findFirst().orElseThrow()
                        .resolve("summary.json").toFile());
                assertEquals(!rejectMeasurements, summary.path("passed").asBoolean());
                assertTrue(summary.path("frames").asLong() > 0);
            }
        }
    }

    @Test void loopbackCapturePreservesMeasurementsNullsAndReplayableFrames() throws Exception {
        try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            var peer = reply(server, false);
            var properties = configured(server.getLocalPort());
            var result = PlcFieldCapture.capture(properties, "field-plc", Duration.ofMillis(500), directory);
            peer.get(3, TimeUnit.SECONDS);
            assertTrue(result.passed());
            assertTrue(result.frames() >= 2);
            var frames = Files.readAllLines(result.directory().resolve("snapshots.ndjson"));
            var samples = Files.readAllLines(result.directory().resolve("realtime.ndjson"));
            assertEquals(result.frames(), frames.size());
            assertEquals(frames.size(), samples.size());
            var pipeline = new PlcFieldCapture.Pipeline(new PlcAccessProperties());
            for (int index = 0; index < frames.size(); index++) {
                var frame = PlcFieldCapture.JSON.readValue(frames.get(index), PlcReadSnapshotDto.class);
                var stored = PlcFieldCapture.JSON.readValue(samples.get(index), TelemetrySample.class);
                assertEquals(PlcFieldCapture.JSON.valueToTree(stored),
                        PlcFieldCapture.JSON.valueToTree(pipeline.accept(frame)));
                assertEquals(0, new BigDecimal("150").compareTo(stored.winches().get(0).tensionAverage()));
                assertNull(stored.ringHeightMeters());
                assertFalse(stored.heightValid());
                assertEquals("RUNNING", stored.status().systemStatus().name());
            }
            assertTrue(Files.readString(result.directory().resolve("realtime.ndjson")).contains("\"ringHeightMeters\":null"));
            assertEquals(1 + result.frames() * 4, Files.readAllLines(result.directory().resolve("points.csv")).size());
            var summary = PlcFieldCapture.JSON.readTree(result.directory().resolve("summary.json").toFile());
            assertTrue(summary.path("passed").asBoolean());
            assertEquals(result.frames(), summary.path("processStatistics").path("acceptedSamples").asLong());
            assertEquals("PLC_READ_ONLY_FIELD_CAPTURE", PlcFieldCapture.JSON
                    .readTree(result.directory().resolve("manifest.json").toFile()).path("mode").asText());
            Path replayRoot = directory.resolve("replays");
            assertEquals(result.frames(), PlcFieldCapture.replay(result.directory(), replayRoot));
            try (var outputs = Files.list(replayRoot)) {
                assertEquals(samples, Files.readAllLines(outputs.findFirst().orElseThrow().resolve("realtime.ndjson")));
            }
        }
    }

    @Test void protocolRejectionIsRecordedAsInvalidRatherThanZeroOrSuccess() throws Exception {
        try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            var peer = reply(server, true);
            var result = PlcFieldCapture.capture(configured(server.getLocalPort()), "field-plc",
                    Duration.ofMillis(100), directory);
            peer.get(3, TimeUnit.SECONDS);
            assertFalse(result.passed());
            var frame = PlcFieldCapture.JSON.readValue(Files.readAllLines(result.directory()
                    .resolve("snapshots.ndjson")).get(0), PlcReadSnapshotDto.class);
            assertTrue(frame.online());
            assertNull(frame.points().get(2).value());
            assertFalse(frame.points().get(2).valid());
            var summary = PlcFieldCapture.JSON.readTree(result.directory().resolve("summary.json").toFile());
            assertFalse(summary.path("passed").asBoolean());
            assertEquals(result.frames(), summary.path("points").path("rope-1").path("invalidReadings").asLong());
        }
    }

    @Test void disconnectedPlcStillLeavesOfflineEvidence() throws Exception {
        int closedPort;
        try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            closedPort = server.getLocalPort();
        }
        var result = PlcFieldCapture.capture(configured(closedPort), "field-plc", Duration.ofMillis(100), directory);
        assertFalse(result.passed());
        var summary = PlcFieldCapture.JSON.readTree(result.directory().resolve("summary.json").toFile());
        assertEquals(result.frames(), summary.path("offlineFrames").asLong());
        assertTrue(Files.size(result.directory().resolve("snapshots.ndjson")) > 0);
    }

    @Test void unsafeOrEmptyConfigurationIsRejectedBeforeConnecting() {
        var properties = configured(502);
        properties.getDevices().get(0).setWritesEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> PlcFieldCapture.validateConfiguration(properties, "field-plc"));
        properties.getDevices().get(0).setWritesEnabled(false);
        properties.getDevices().get(0).setPoints(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> PlcFieldCapture.validateConfiguration(properties, "field-plc"));
    }

    @Test void yamlBindingResolvesEnvironmentStylePlaceholdersAndValidatesPointMap() throws Exception {
        Path file = directory.resolve("plc.yml");
        Files.writeString(file, """
                cleaning:
                  plc:
                    enabled: true
                    devices:
                      - id: field-plc
                        enabled: true
                        writes-enabled: false
                        host: ${FIELD_TEST_UNUSED_HOST:127.0.0.1}
                        points:
                          - id: running
                            enabled: true
                            field: running
                            address: 20
                            function-code: 1
                            data-type: BOOLEAN
                """);
        var properties = PlcFieldCapture.loadConfiguration(file, "field-plc");
        assertEquals("127.0.0.1", properties.activeDevice("field-plc").orElseThrow().getHost());
        assertThrows(IllegalArgumentException.class, () -> PlcFieldCapture.loadConfiguration(file, "wrong-device"));
    }

    @Test void replayRejectsDuplicateSequencesAndRetainsMissingDataQuality() {
        var pipeline = new PlcFieldCapture.Pipeline(new PlcAccessProperties());
        var frame = new PlcReadSnapshotDto("field-plc", false, 1, java.time.Instant.now(), java.util.List.of());
        assertFalse(pipeline.accept(frame).heightValid());
        assertThrows(IllegalArgumentException.class, () -> pipeline.accept(frame));
        assertThrows(IllegalArgumentException.class, () -> pipeline.accept(
                new PlcReadSnapshotDto("field-plc", false, 2, null, java.util.List.of())));
        assertThrows(IllegalArgumentException.class, () -> pipeline.accept(
                new PlcReadSnapshotDto("field-plc", false, 2, frame.sampledAt().minusSeconds(1), java.util.List.of())));
    }

    @Test void replayRejectsCorruptEmptyAndMixedDeviceFilesAndLeavesFailureReport() throws Exception {
        Path source = Files.createDirectory(directory.resolve("input"));
        Path output = directory.resolve("output");
        Path snapshots = source.resolve("snapshots.ndjson");
        var time = java.time.Instant.now();
        var first = new PlcReadSnapshotDto("field-plc", false, 1, time, java.util.List.of());
        var other = new PlcReadSnapshotDto("another-plc", false, 2, time, java.util.List.of());
        Files.writeString(snapshots, PlcFieldCapture.JSON.writeValueAsString(first) + "\n"
                + PlcFieldCapture.JSON.writeValueAsString(other) + "\n");
        assertThrows(IllegalArgumentException.class, () -> PlcFieldCapture.replay(source, output));
        Files.writeString(snapshots, "");
        assertThrows(IllegalArgumentException.class, () -> PlcFieldCapture.replay(source, output));
        Files.writeString(snapshots, "{incomplete");
        assertThrows(IOException.class, () -> PlcFieldCapture.replay(source, output));
        try (var outputs = Files.list(output)) {
            var reports = outputs.toList();
            assertEquals(3, reports.size());
            for (var report : reports) {
                var summary = PlcFieldCapture.JSON.readTree(report.resolve("summary.json").toFile());
                assertEquals("FAILED", summary.path("status").asText());
                assertFalse(summary.path("businessCorrectnessVerified").asBoolean());
            }
        }
    }

    private static PlcAccessProperties configured(int port) {
        var properties = new PlcAccessProperties();
        properties.setEnabled(true);
        var device = new Device();
        device.setId("field-plc");
        device.setEnabled(true);
        device.setHost("127.0.0.1");
        device.setPort(port);
        device.setPollIntervalMs(50);
        device.setTimeoutMs(500);
        var points = new ArrayList<Point>();
        for (int index = 0; index < 4; index++) {
            var point = new Point();
            point.setEnabled(true);
            point.setId(index < 2 ? "mode-" + index : "rope-" + (index - 1));
            point.setField(index == 0 ? "manualMode" : index == 1 ? "running" : "ropeTension");
            point.setAddress(index < 2 ? index : index + 38);
            point.setFunctionCode(index < 2 ? 1 : 3);
            point.setDataType(index < 2 ? DataType.BOOLEAN : DataType.UINT16);
            if (index >= 2) {
                point.setIndex(index - 1);
                point.setWinchId("W1");
                point.setRopeId("R" + (index - 1));
                point.setSensorId("S" + (index - 1));
                point.setUnit("kg");
            }
            points.add(point);
        }
        device.setPoints(points);
        properties.setDevices(java.util.List.of(device));
        return properties;
    }

    private static CompletableFuture<Void> reply(ServerSocket server, boolean rejectMeasurements) {
        return CompletableFuture.runAsync(() -> {
            try (var socket = server.accept()) {
                socket.setSoTimeout(2000);
                while (true) {
                    byte[] request = socket.getInputStream().readNBytes(12);
                    if (request.length == 0) break;
                    if (request.length != 12) throw new IOException("incomplete request");
                    byte[] data;
                    if (request[7] == 1) data = new byte[]{1, 1, 2};
                    else if (request[7] == 3) data = rejectMeasurements ? new byte[]{(byte) 0x83, 2}
                            : new byte[]{3, 4, 0, 100, 0, (byte) 200};
                    else throw new IOException("Field capture attempted a write");
                    byte[] response = Arrays.copyOf(request, 7 + data.length);
                    response[4] = 0;
                    response[5] = (byte) (data.length + 1);
                    System.arraycopy(data, 0, response, 7, data.length);
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                }
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }
}
