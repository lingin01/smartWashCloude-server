package com.company.project.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** 只访问随机端口的 127.0.0.1 HTTP stub，不需要数据库、PLC 或现场开关。 */
class FieldApiSmokeSupportTest {
    private static final String DEVICE = "field-plc";
    private static final String TOKEN = "private-field-access-token";
    private static final String PASSWORD = "private-field-password";
    private static final String USERNAME = "private-field-username";
    private static final String REFRESH = "private-field-refresh-token";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path output;

    @Test
    void authenticatedReadChecksKeepEvidenceAndQueryOnlyAnExistingCommand() throws Exception {
        try (var backend = new StubBackend()) {
            var outcome = new FieldApiSmokeSupport(options(backend, TOKEN, "existing-request", Duration.ofSeconds(2))).run();
            assertTrue(outcome.failures().isEmpty(), outcome.summary());
            assertTrue(Files.exists(outcome.directory().resolve("anonymous-realtime.json")));
            assertTrue(Files.exists(outcome.directory().resolve("realtime-01.json")));
            assertTrue(Files.exists(outcome.directory().resolve("realtime-02.json")));
            assertTrue(Files.exists(outcome.directory().resolve("settings.json")));
            assertTrue(Files.exists(outcome.directory().resolve("statistics.json")));
            assertTrue(Files.exists(outcome.directory().resolve("existing-command.json")));
            var report = JSON.readTree(outcome.directory().resolve("report.json").toFile());
            assertEquals("PASSED", report.path("status").asText());
            assertEquals(2, report.path("acceptedRealtimeSamples").asInt());
            assertTrue(report.path("historyPersistence").asText().contains("NOT_VERIFIED"));
            assertTrue(backend.requests.stream().allMatch(request -> request.startsWith("GET ")));
            assertTrue(backend.requests.contains("GET /api/cleaning/devices/field-plc/commands/existing-request"));
            assertCredentialsAbsent(outcome.directory());
        }
    }

    @Test
    void loginCredentialsAndReturnedTokensAreNeverWrittenToEvidence() throws Exception {
        try (var backend = new StubBackend()) {
            var outcome = new FieldApiSmokeSupport(options(backend, null, null, Duration.ofSeconds(2))).run();
            assertTrue(outcome.failures().isEmpty(), outcome.summary());
            assertTrue(backend.validLoginReceived);
            assertEquals(1, backend.requests.stream().filter(request -> request.startsWith("POST ")).count());
            assertTrue(backend.requests.contains("POST /api/auth/login"));
            assertTrue(backend.requests.stream().noneMatch(request -> request.startsWith("PUT ")
                    || request.startsWith("DELETE ") || request.contains("emergency-stop")));
            assertFalse(Files.exists(outcome.directory().resolve("login.json")));
            assertCredentialsAbsent(outcome.directory());
            String userInfo = Files.readString(outcome.directory().resolve("user-info.json"));
            assertTrue(userInfo.contains("[REDACTED]"));
        }
    }

    @Test
    void partiallyConfiguredEightRopesAndFourWinchesAllowInvalidEmptySlots() throws Exception {
        try (var backend = new StubBackend()) {
            backend.scenario = "partial-points";
            var outcome = new FieldApiSmokeSupport(options(backend, TOKEN, null, Duration.ofSeconds(2))).run();
            assertTrue(outcome.failures().isEmpty(), outcome.summary());
            var sample = JSON.readTree(outcome.directory().resolve("realtime-01.json").toFile())
                    .path("body").path("data").path("data");
            assertEquals(8, sample.path("ropes").size());
            assertEquals(4, sample.path("winches").size());
            assertEquals("", sample.path("ropes").get(7).path("ropeId").asText());
            assertFalse(sample.path("ropes").get(7).path("valid").asBoolean());
            assertEquals("", sample.path("winches").get(3).path("winchId").asText());
            assertFalse(sample.path("winches").get(3).path("speedValid").asBoolean());
            assertEquals(1, sample.path("validTensionCount").asInt());
            assertEquals(1, sample.path("validSpeedCount").asInt());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unavailable", "offline", "invalid-status", "stale", "stationary", "wrong-device",
            "no-measurements", "non-json", "valid-missing-id", "missing-validity"})
    void invalidPlcSnapshotsFailWhileLaterReadEvidenceIsPreserved(String scenario) throws Exception {
        try (var backend = new StubBackend()) {
            backend.scenario = scenario;
            var outcome = new FieldApiSmokeSupport(options(backend, TOKEN, null, Duration.ofSeconds(2))).run();
            assertFalse(outcome.failures().isEmpty(), scenario + " must fail");
            assertTrue(outcome.failures().stream().anyMatch(failure -> failure.startsWith("realtime-")));
            assertTrue(Files.exists(outcome.directory().resolve("settings.json")), "one bad read must not stop all evidence capture");
            assertTrue(Files.exists(outcome.directory().resolve("statistics.json")));
            assertEquals("FAILED", JSON.readTree(outcome.directory().resolve("report.json").toFile()).path("status").asText());
            assertCredentialsAbsent(outcome.directory());
        }
    }

    @Test
    void anUnexpectedAnonymousSuccessIsAnAuthenticationFailure() throws Exception {
        try (var backend = new StubBackend()) {
            backend.anonymousStatus = 200;
            var outcome = new FieldApiSmokeSupport(options(backend, TOKEN, null, Duration.ofSeconds(2))).run();
            assertTrue(outcome.failures().stream().anyMatch(failure -> failure.startsWith("anonymous-realtime:")));
            assertTrue(Files.exists(outcome.directory().resolve("statistics.json")));
        }
    }

    @Test
    void failedLoginKeepsReportAndDoesNotPersistSensitiveErrorBody() throws Exception {
        try (var backend = new StubBackend()) {
            backend.loginStatus = 401;
            var outcome = new FieldApiSmokeSupport(options(backend, null, null, Duration.ofSeconds(2))).run();
            assertEquals(1, outcome.failures().size());
            assertTrue(outcome.failures().get(0).startsWith("authentication:"));
            assertEquals(2, backend.requests.size());
            assertFalse(Files.exists(outcome.directory().resolve("user-info.json")));
            assertCredentialsAbsent(outcome.directory());
            assertTrue(Files.readString(outcome.directory().resolve("report.json")).contains("SKIPPED"));
        }
    }

    @Test
    void wrongBusinessScopeAndMissingRequestedCommandFailIndependently() throws Exception {
        try (var backend = new StubBackend()) {
            backend.statisticsMutation = data -> data.put("scope", "DATABASE_HISTORY");
            backend.settingsMutation = data -> data.put("deviceId", "other-device");
            backend.commandStatus = 404;
            var outcome = new FieldApiSmokeSupport(options(backend, TOKEN, "existing-request", Duration.ofSeconds(2))).run();
            assertEquals(3, outcome.failures().size(), outcome.summary());
            assertTrue(outcome.failures().stream().anyMatch(failure -> failure.startsWith("settings:")));
            assertTrue(outcome.failures().stream().anyMatch(failure -> failure.startsWith("statistics:")));
            assertTrue(outcome.failures().stream().anyMatch(failure -> failure.startsWith("existing-command:")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"headers", "body"})
    void aSlowReadTimesOutAndStillCollectsOtherEndpoints(String delayedPart) throws Exception {
        try (var backend = new StubBackend()) {
            if (delayedPart.equals("headers")) backend.realtimeDelayMillis = 500;
            else backend.bodyDelayMillis = 500;
            Instant started = Instant.now();
            var outcome = new FieldApiSmokeSupport(options(backend, TOKEN, null, Duration.ofMillis(150))).run();
            assertTrue(outcome.failures().stream().anyMatch(failure -> failure.contains("HttpTimeoutException")));
            assertTrue(Duration.between(started, Instant.now()).compareTo(Duration.ofSeconds(5)) < 0);
            assertTrue(Files.exists(outcome.directory().resolve("settings.json")));
            assertTrue(Files.exists(outcome.directory().resolve("statistics.json")));
        }
    }

    @Test
    void eachRunUsesItsOwnDirectoryAndOptionsNeverPrintCredentials() throws Exception {
        try (var backend = new StubBackend()) {
            var options = options(backend, TOKEN, null, Duration.ofSeconds(2));
            assertFalse(options.toString().contains(TOKEN));
            assertFalse(options.toString().contains(USERNAME));
            assertFalse(options.toString().contains(PASSWORD));
            var first = new FieldApiSmokeSupport(options).run();
            var second = new FieldApiSmokeSupport(options).run();
            assertNotEquals(first.directory(), second.directory());
            assertTrue(Files.exists(first.directory().resolve("report.json")));
            assertTrue(Files.exists(second.directory().resolve("report.json")));
        }
    }

    private FieldApiSmokeSupport.Options options(StubBackend backend, String token, String requestId, Duration timeout) {
        return new FieldApiSmokeSupport.Options(backend.url(), DEVICE, output, token, USERNAME, PASSWORD,
                2, timeout, Duration.ZERO, Duration.ofSeconds(15), requestId);
    }

    private void assertCredentialsAbsent(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String content = Files.readString(file);
                for (String secret : List.of(TOKEN, PASSWORD, USERNAME, REFRESH, "unexpected-secret-token"))
                    assertFalse(content.contains(secret), "credentials must not be persisted in " + file.getFileName());
            }
        }
    }

    private static ObjectNode ok(JsonNode data) {
        ObjectNode root = JSON.createObjectNode();
        root.put("code", 200);
        root.put("message", "success");
        root.set("data", data);
        return root;
    }

    private static final class StubBackend implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger sequence = new AtomicInteger();
        private final Instant sampleTimeBase = Instant.now().minusSeconds(1);
        private String scenario = "valid";
        private int anonymousStatus = 401;
        private int loginStatus = 200;
        private int commandStatus = 200;
        private long realtimeDelayMillis;
        private long bodyDelayMillis;
        private boolean validLoginReceived;
        private Consumer<ObjectNode> statisticsMutation = ignored -> { };
        private Consumer<ObjectNode> settingsMutation = ignored -> { };

        StubBackend() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        URI url() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                requests.add(exchange.getRequestMethod() + " " + path);
                if (path.equals("/api/auth/login")) {
                    var credentials = JSON.readTree(exchange.getRequestBody());
                    validLoginReceived = USERNAME.equals(credentials.path("username").asText())
                            && PASSWORD.equals(credentials.path("password").asText());
                    var data = JSON.createObjectNode().put("token", TOKEN).put("refreshToken", REFRESH)
                            .put("password", PASSWORD);
                    send(exchange, loginStatus, ok(data));
                    return;
                }
                if (!exchange.getRequestHeaders().containsKey("Authorization")) {
                    exchange.sendResponseHeaders(anonymousStatus, -1);
                    return;
                }
                if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                if (path.equals("/api/auth/user-info")) {
                    var user = JSON.createObjectNode().put("username", USERNAME).put("password", PASSWORD)
                            .put("debug", TOKEN + " / " + PASSWORD).put("token", "unexpected-secret-token");
                    user.putArray("roles").add("VIEWER");
                    user.putArray("permissions").add("device:read");
                    send(exchange, 200, ok(user));
                } else if (path.endsWith("/realtime")) {
                    if (realtimeDelayMillis > 0) {
                        try { Thread.sleep(realtimeDelayMillis); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                    }
                    if (scenario.equals("non-json")) {
                        byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    } else send(exchange, 200, realtime());
                } else if (path.endsWith("/settings")) {
                    var settings = JSON.createObjectNode().put("deviceId", DEVICE).put("state", "NOT_SYNCHRONIZED");
                    settingsMutation.accept(settings);
                    send(exchange, 200, ok(settings));
                } else if (path.endsWith("/statistics")) {
                    var statistics = JSON.createObjectNode().put("deviceId", DEVICE).put("scope", "PROCESS")
                            .put("acceptedSamples", sequence.get());
                    statistics.putArray("metrics");
                    statisticsMutation.accept(statistics);
                    send(exchange, 200, ok(statistics));
                } else if (path.endsWith("/commands/existing-request")) {
                    var command = JSON.createObjectNode().put("deviceId", DEVICE).put("requestId", "existing-request")
                            .put("commandId", "existing-command").put("type", "emergency_stop")
                            .put("state", "SUCCEEDED").put("code", "PLC_EMERGENCY_WRITE_CONFIRMED");
                    send(exchange, commandStatus, ok(command));
                } else exchange.sendResponseHeaders(404, -1);
            }
        }

        private ObjectNode realtime() {
            int current = sequence.incrementAndGet();
            var view = JSON.createObjectNode().put("deviceId", scenario.equals("wrong-device") ? "other-device" : DEVICE)
                    .put("available", !scenario.equals("unavailable"));
            var sample = view.putObject("data");
            sample.put("sequence", scenario.equals("stationary") ? 1 : current);
            // 回环请求可能在同一个系统时钟刻度内完成，测试帧时间按序号明确推进。
            sample.put("sampledAt", sampleTimeBase.plusMillis(current)
                    .minusSeconds(scenario.equals("stale") ? 3600 : 0).toString());
            sample.putObject("status").put("online", !scenario.equals("offline"))
                    .put("statusValid", !scenario.equals("invalid-status"));
            boolean measured = !scenario.equals("no-measurements");
            var ropes = sample.putArray("ropes");
            var rope = ropes.addObject().put("ropeId", "R1").put("valid", measured).put("tension", 123);
            var winches = sample.putArray("winches");
            var winch = winches.addObject().put("winchId", "W1").put("speedValid", measured)
                    .put("speed", 6).put("heightValid", false).put("torqueValid", false);
            if (scenario.equals("partial-points")) {
                for (int index = 1; index < 8; index++)
                    ropes.addObject().put("ropeId", "").put("sensorId", "").put("winchId", "").put("valid", false);
                for (int index = 1; index < 4; index++)
                    winches.addObject().put("winchId", "").put("speedValid", false)
                            .put("heightValid", false).put("torqueValid", false);
            }
            if (scenario.equals("valid-missing-id")) rope.put("ropeId", "");
            if (scenario.equals("missing-validity")) winch.remove("heightValid");
            sample.put("heightValid", false);
            sample.put("validTensionCount", measured ? 1 : 0);
            sample.put("validSpeedCount", measured ? 1 : 0);
            return ok(view);
        }

        private void send(HttpExchange exchange, int status, JsonNode response) throws IOException {
            byte[] body = JSON.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            if (bodyDelayMillis > 0 && exchange.getRequestURI().getPath().endsWith("/realtime")) {
                exchange.getResponseBody().write(body, 0, body.length / 2);
                exchange.getResponseBody().flush();
                try { Thread.sleep(bodyDelayMillis); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                exchange.getResponseBody().write(body, body.length / 2, body.length - body.length / 2);
                return;
            }
            exchange.getResponseBody().write(body);
        }

        @Override public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
