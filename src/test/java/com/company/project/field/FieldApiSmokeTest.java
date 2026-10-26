package com.company.project.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在 IDE 中直接运行，参数读取 field-config/field-test.yml；普通 mvn test 排除此现场入口。 */
class FieldApiSmokeTest {
    @Test
    void verifyRunningBackendReadOnly() throws Exception {
        var result = new FieldApiSmokeSupport(FieldApiSmokeSupport.Options.fromConfiguration()).run();
        assertTrue(result.failures().isEmpty(), result.summary());
        System.out.println(result.summary());
    }
}

/** 与现场用例共用的有界 HTTP 探针；回归测试只将它指向本机 HTTP stub。 */
final class FieldApiSmokeSupport {
    record Options(URI baseUrl, String deviceId, Path outputRoot, String token, String username,
                   String password, int samples, Duration timeout, Duration pollInterval,
                   Duration maxSampleAge, String requestId) {
        Options {
            if (baseUrl == null || !Set.of("http", "https").contains(baseUrl.getScheme())
                    || baseUrl.getHost() == null || baseUrl.getRawUserInfo() != null
                    || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null)
                throw new IllegalArgumentException("field.api.base-url 必须是无账号、查询参数或片段的 HTTP(S) 地址");
            if (deviceId == null || !deviceId.matches("[A-Za-z0-9._-]{1,64}"))
                throw new IllegalArgumentException("请在 field-config/field-test.yml 的 field.device-id 填写实际设备编号");
            if (requestId != null && !requestId.matches("[A-Za-z0-9._-]{1,128}"))
                throw new IllegalArgumentException("field.api.request-id 格式无效");
            if (outputRoot == null || samples < 2 || samples > 100
                    || timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                    || timeout.compareTo(Duration.ofSeconds(60)) > 0
                    || pollInterval == null || pollInterval.isNegative()
                    || pollInterval.compareTo(Duration.ofSeconds(60)) > 0
                    || maxSampleAge == null || maxSampleAge.isZero() || maxSampleAge.isNegative()
                    || maxSampleAge.compareTo(Duration.ofHours(1)) > 0)
                throw new IllegalArgumentException("现场 HTTP 采样次数或超时参数超出允许范围");
            if (blank(token) && (blank(username) || blank(password)))
                throw new IllegalArgumentException("请在 field-config/field-test.yml 填写 field.api.token，或 field.api.username 与 field.api.password");
        }

        static Options fromConfiguration() throws IOException {
            var configuration = FieldTestConfiguration.load();
            String requestId = configuration.text("field.api.request-id", "");
            return new Options(URI.create(configuration.text("field.api.base-url", "http://127.0.0.1:8080")),
                    configuration.text("field.device-id"), configuration.path("field.output-dir", "field-results"),
                    configuration.text("field.api.token", ""), configuration.text("field.api.username", ""),
                    configuration.text("field.api.password", ""), configuration.integer("field.api.samples", 3),
                    Duration.ofSeconds(configuration.integer("field.api.timeout-seconds", 5)),
                    Duration.ofMillis(configuration.integer("field.api.poll-interval-ms", 1000)),
                    Duration.ofSeconds(configuration.integer("field.api.max-sample-age-seconds", 15)),
                    requestId.isBlank() ? null : requestId);
        }

        private static boolean blank(String value) { return value == null || value.isBlank(); }

        // Options 含有认证信息，禁止默认 record.toString() 将它们打印到日志。
        @Override public String toString() { return "FieldApiOptions[credentials redacted]"; }
    }

    record Outcome(Path directory, List<String> failures) {
        String summary() {
            return "现场 HTTP 只读检查" + (failures.isEmpty() ? "通过" : "失败：" + String.join("；", failures))
                    + "。报告：" + directory.resolve("report.json")
                    + "。statistics 仅为 PROCESS 统计，本检查不证明历史数据库保存。";
        }
    }

    private record Reply(int status, JsonNode body) { }
    private record Sample(long sequence, Instant sampledAt) { }
    @FunctionalInterface private interface Check { void run() throws Exception; }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CREDENTIAL_KEYS = Set.of("token", "refreshtoken", "accesstoken",
            "password", "oldpassword", "newpassword", "authorization", "secret", "jwt", "username");
    private static final Set<String> COMMAND_STATES = Set.of("CREATED", "QUEUED", "SUCCEEDED", "REJECTED",
            "FAILED", "UNCONFIRMED", "BLOCKED");
    private final Options options;
    private final HttpClient client;
    private final List<String> secrets = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final List<Sample> receivedSamples = new ArrayList<>();
    private final ObjectNode report = JSON.createObjectNode();
    private final ArrayNode checks = report.putArray("checks");
    private Path directory;
    private String accessToken;

    FieldApiSmokeSupport(Options options) {
        this.options = options;
        this.client = HttpClient.newBuilder().connectTimeout(options.timeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        rememberSecret(options.token());
        rememberSecret(options.username());
        rememberSecret(options.password());
    }

    Outcome run() throws IOException {
        Files.createDirectories(options.outputRoot());
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        directory = Files.createDirectory(options.outputRoot().resolve("api-" + timestamp + "-"
                + UUID.randomUUID().toString().substring(0, 8))).toAbsolutePath();
        report.put("kind", "FIELD_API_READ_ONLY_SMOKE");
        report.put("baseUrl", options.baseUrl().toString());
        report.put("deviceId", options.deviceId());
        report.put("startedAt", Instant.now().toString());
        report.put("historyPersistence", "NOT_VERIFIED: statistics scope=PROCESS; no history database query is performed");
        report.put("status", "RUNNING");
        saveReport();
        try {
            check("anonymous-realtime", () -> {
                var response = get("anonymous-realtime", devicePath("/realtime"), false);
                require(response.status() == 401, "未认证实时接口必须返回 HTTP 401");
            });
            check("authentication", this::authenticate);
            if (accessToken != null) {
                check("user-info", this::userInfo);
                for (int index = 1; index <= options.samples(); index++) {
                    final String name = "realtime-" + String.format("%02d", index);
                    if (index > 1) {
                        try { Thread.sleep(options.pollInterval().toMillis()); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            failures.add("采样被中断");
                            break;
                        }
                    }
                    check(name, () -> realtime(name));
                }
                check("realtime-progress", this::progress);
                check("settings", this::settings);
                check("statistics", this::statistics);
                if (options.requestId() != null) check("existing-command", this::existingCommand);
            } else {
                var skipped = checks.addObject();
                skipped.put("name", "authenticated-read-checks");
                skipped.put("status", "SKIPPED");
                skipped.put("detail", "认证失败，未执行后续读取");
            }
        } finally {
            report.put("status", failures.isEmpty() ? "PASSED" : "FAILED");
            report.put("completedAt", Instant.now().toString());
            report.put("acceptedRealtimeSamples", receivedSamples.size());
            saveReport();
        }
        return new Outcome(directory, List.copyOf(failures));
    }

    private void check(String name, Check action) throws IOException {
        var entry = checks.addObject();
        entry.put("name", name);
        try {
            action.run();
            entry.put("status", "PASSED");
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            String detail = failure instanceof ProbeFailure ? failure.getMessage()
                    : "检查未完成（" + failure.getClass().getSimpleName() + "）";
            detail = scrub(detail);
            entry.put("status", "FAILED");
            entry.put("detail", detail);
            failures.add(name + ": " + detail);
        }
        saveReport();
    }

    private void authenticate() throws Exception {
        if (!Options.blank(options.token())) {
            accessToken = options.token();
            return;
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("username", options.username());
        body.put("password", options.password());
        var request = request("/api/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8)).build();
        // 登录响应只在内存提取令牌，既不保存响应正文，也不保存认证请求。
        var response = send(request);
        require(response.statusCode() == 200, "登录必须返回 HTTP 200");
        JsonNode parsed;
        try { parsed = JSON.readTree(response.body()); }
        catch (Exception invalid) { throw new ProbeFailure("登录响应不是有效 JSON"); }
        var data = data(new Reply(response.statusCode(), parsed));
        JsonNode token = data.get("token");
        require(token != null && token.isTextual() && !token.textValue().isBlank(), "登录响应缺少访问令牌");
        accessToken = token.textValue();
        rememberSecret(accessToken);
        JsonNode refresh = data.get("refreshToken");
        if (refresh != null && refresh.isTextual()) rememberSecret(refresh.textValue());
    }

    private void userInfo() throws Exception {
        var data = data(get("user-info", "/api/auth/user-info", true));
        require(textPresent(data, "username"), "用户信息缺少实际账号");
        require(data.path("roles").isArray() && !data.path("roles").isEmpty(), "用户信息缺少角色");
        require(data.path("permissions").isArray(), "用户信息缺少权限数组");
        boolean readable = false;
        for (var permission : data.path("permissions")) readable |= permission.isTextual()
                && "device:read".equals(permission.textValue());
        require(readable, "账号没有 device:read 权限");
    }

    private void realtime(String name) throws Exception {
        var view = deviceData(get(name, devicePath("/realtime"), true));
        require(view.path("available").isBoolean() && view.path("available").booleanValue(), "尚未取得 PLC 实时快照");
        var sample = view.path("data");
        require(sample.isObject(), "实时响应缺少实际采样对象 data.data");
        require(nonnegativeLong(sample.path("sequence")), "采样序号无效");
        require(textPresent(sample, "sampledAt"), "采样缺少时间");
        Instant sampledAt;
        try { sampledAt = Instant.parse(sample.path("sampledAt").textValue()); }
        catch (Exception invalid) { throw new ProbeFailure("采样时间不是有效带时区时间"); }
        Instant now = Instant.now();
        require(!sampledAt.isBefore(now.minus(options.maxSampleAge())), "实时快照已过期，请检查 PLC 采集链路");
        require(!sampledAt.isAfter(now.plusSeconds(5)), "采样时间超前，请检查服务端时钟");
        var status = sample.path("status");
        require(status.isObject(), "PLC 状态对象缺失");
        require(status.path("online").isBoolean() && status.path("online").booleanValue(), "PLC 当前离线");
        require(status.path("statusValid").isBoolean() && status.path("statusValid").booleanValue(), "PLC 系统状态点无效");
        require(sample.path("ropes").isArray() && sample.path("winches").isArray(), "实时测量通道数组缺失");
        long validTensions = 0;
        long validSpeeds = 0;
        boolean validMeasurement = false;
        for (var rope : sample.path("ropes")) {
            require(rope.path("valid").isBoolean(), "索通道有效性缺失");
            if (rope.path("valid").booleanValue()) {
                require(textPresent(rope, "ropeId"), "有效索通道缺少实际标识");
                require(rope.path("tension").isNumber(), "有效索通道缺少数值拉力");
                validTensions++;
                validMeasurement = true;
            }
        }
        for (var winch : sample.path("winches")) {
            require(winch.path("speedValid").isBoolean() && winch.path("heightValid").isBoolean()
                    && winch.path("torqueValid").isBoolean(), "动力设备通道有效性缺失");
            if (winch.path("speedValid").booleanValue() || winch.path("heightValid").booleanValue()
                    || winch.path("torqueValid").booleanValue())
                require(textPresent(winch, "winchId"), "有效动力设备通道缺少实际标识");
            if (winch.path("speedValid").booleanValue()) {
                require(winch.path("speed").isNumber(), "有效动力设备通道缺少数值速度");
                validSpeeds++;
                validMeasurement = true;
            }
            if (winch.path("heightValid").booleanValue()) {
                require(winch.path("heightMeters").isNumber(), "有效高度通道缺少数值高度");
                validMeasurement = true;
            }
            if (winch.path("torqueValid").booleanValue()) {
                require(winch.path("torqueKg").isNumber(), "有效转矩通道缺少数值转矩");
                validMeasurement = true;
            }
        }
        require(sample.path("heightValid").isBoolean(), "环体高度有效性缺失");
        if (sample.path("heightValid").booleanValue()) {
            require(sample.path("ringHeightMeters").isNumber(), "有效环体高度缺少数值");
            validMeasurement = true;
        }
        require(nonnegativeLong(sample.path("validTensionCount"))
                && sample.path("validTensionCount").longValue() == validTensions, "有效索数量与通道不一致");
        require(nonnegativeLong(sample.path("validSpeedCount"))
                && sample.path("validSpeedCount").longValue() == validSpeeds, "有效速度数量与通道不一致");
        require(validMeasurement, "快照没有任何有效测量值，请检查 PLC 点位与现场传感器");
        receivedSamples.add(new Sample(sample.path("sequence").longValue(), sampledAt));
    }

    private void progress() {
        require(receivedSamples.size() >= 2, "至少需要两份有效实时快照才能核验持续采集");
        Sample previous = receivedSamples.get(0);
        for (int index = 1; index < receivedSamples.size(); index++) {
            Sample current = receivedSamples.get(index);
            require(current.sequence() >= previous.sequence(), "采样序号倒退，可能发生后端重启或状态异常");
            require(!current.sampledAt().isBefore(previous.sampledAt()), "采样时间倒退");
            previous = current;
        }
        require(previous.sequence() > receivedSamples.get(0).sequence(), "采样序号没有推进，请检查采集是否停滞");
        require(previous.sampledAt().isAfter(receivedSamples.get(0).sampledAt()), "采样时间没有推进");
    }

    private void settings() throws Exception {
        var settings = deviceData(get("settings", devicePath("/settings"), true));
        require(Set.of("NOT_SYNCHRONIZED", "WRITTEN_NOT_PERSISTED").contains(settings.path("state").asText()),
                "参数读取返回未知同步状态");
        var values = settings.get("settings");
        if (values != null && !values.isNull()) {
            require(values.isObject() && textPresent(values, "startTime"), "已返回的参数缺少开始时间");
            try { LocalTime.parse(values.path("startTime").textValue()); }
            catch (Exception invalid) { throw new ProbeFailure("参数开始时间格式无效"); }
            require(nonnegativeLong(values.path("intervalSeconds")) && values.path("intervalSeconds").longValue() > 0,
                    "参数间隔无效");
            require(values.path("groupSpeedMetersPerMinute").isNumber()
                    && values.path("groupSpeedMetersPerMinute").decimalValue().signum() > 0, "参数速度无效");
        }
    }

    private void statistics() throws Exception {
        var statistics = deviceData(get("statistics", devicePath("/statistics"), true));
        require("PROCESS".equals(statistics.path("scope").asText()), "当前统计接口必须明确为 PROCESS 统计");
        require(nonnegativeLong(statistics.path("acceptedSamples"))
                && statistics.path("acceptedSamples").longValue() > 0, "过程统计尚未接收实际采样");
        require(statistics.path("metrics").isArray(), "过程统计缺少指标数组");
        for (var metric : statistics.path("metrics")) {
            require(textPresent(metric, "metric") && textPresent(metric, "targetId")
                    && textPresent(metric, "unit") && nonnegativeLong(metric.path("validSamples")), "统计指标结构无效");
            if (metric.path("validSamples").longValue() > 0)
                require(metric.path("minimum").isNumber() && metric.path("maximum").isNumber()
                        && metric.path("sampleAverage").isNumber(), "有效统计指标缺少数值");
        }
    }

    private void existingCommand() throws Exception {
        var command = deviceData(get("existing-command", devicePath("/commands/" + options.requestId()), true));
        require(options.requestId().equals(command.path("requestId").asText()), "命令查询请求编号不匹配");
        require(textPresent(command, "commandId") && textPresent(command, "code"), "命令身份或状态码缺失");
        require(COMMAND_STATES.contains(command.path("state").asText()), "命令业务状态无效");
        require(Set.of("emergency_stop", "update_cleaning_parameters").contains(command.path("type").asText()),
                "命令类型无效");
    }

    private Reply get(String name, String path, boolean authenticated) throws Exception {
        var request = request(path).GET();
        if (authenticated) request.header("Authorization", "Bearer " + accessToken);
        var evidence = JSON.createObjectNode();
        evidence.put("method", "GET");
        evidence.put("path", path);
        evidence.put("requestedAt", Instant.now().toString());
        try {
            var response = send(request.build());
            evidence.put("httpStatus", response.statusCode());
            JsonNode parsed = null;
            if (!response.body().isBlank()) {
                try { parsed = JSON.readTree(response.body()); }
                catch (Exception invalid) { evidence.put("bodyFormat", "NON_JSON"); }
            }
            if (parsed != null) evidence.set("body", redact(parsed));
            write(name + ".json", evidence);
            return new Reply(response.statusCode(), parsed);
        } catch (Exception failure) {
            evidence.put("errorType", failure.getClass().getSimpleName());
            write(name + ".json", evidence);
            throw failure;
        }
    }

    private HttpRequest.Builder request(String path) {
        String base = options.baseUrl().toString().replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(options.timeout())
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        // 同时限制响应正文的等待时间，防止收到响应头后正文停滞而无限等待。
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return pending.get(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw new HttpTimeoutException("HTTP response deadline exceeded");
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            throw interrupted;
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw new IOException("HTTP request failed", failure.getCause());
        }
    }

    private String devicePath(String suffix) { return "/api/cleaning/devices/" + options.deviceId() + suffix; }

    private JsonNode deviceData(Reply reply) {
        var data = data(reply);
        require(options.deviceId().equals(data.path("deviceId").asText()), "响应设备编号与指定设备不匹配");
        return data;
    }

    private JsonNode data(Reply reply) {
        require(reply.status() == 200, "读取必须返回 HTTP 200，实际为 " + reply.status());
        require(reply.body() != null && reply.body().isObject(), "响应必须是 JSON 对象");
        require(reply.body().path("code").isIntegralNumber() && reply.body().path("code").canConvertToInt()
                        && reply.body().path("code").intValue() == 200,
                "响应业务 code 必须为 200");
        require(reply.body().path("data").isObject(), "响应缺少 data 对象");
        return reply.body().path("data");
    }

    private static boolean textPresent(JsonNode object, String field) {
        return object.path(field).isTextual() && !object.path(field).textValue().isBlank();
    }

    private static boolean nonnegativeLong(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0;
    }

    private void rememberSecret(String value) {
        if (value != null && !value.isEmpty()) secrets.add(value);
    }

    private JsonNode redact(JsonNode input) {
        if (input.isObject()) {
            ObjectNode output = JSON.createObjectNode();
            input.fields().forEachRemaining(field -> {
                String key = field.getKey().toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
                output.set(field.getKey(), CREDENTIAL_KEYS.contains(key)
                        ? JSON.getNodeFactory().textNode("[REDACTED]") : redact(field.getValue()));
            });
            return output;
        }
        if (input.isArray()) {
            ArrayNode output = JSON.createArrayNode();
            input.forEach(item -> output.add(redact(item)));
            return output;
        }
        return input.isTextual() ? JSON.getNodeFactory().textNode(scrub(input.textValue())) : input.deepCopy();
    }

    private String scrub(String value) {
        for (String secret : secrets) value = value.replace(secret, "[REDACTED]");
        return value;
    }

    private void write(String name, JsonNode value) throws IOException {
        Files.writeString(directory.resolve(name), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(redact(value)),
                StandardCharsets.UTF_8);
    }

    private void saveReport() throws IOException { write("report.json", report); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ProbeFailure(message);
    }

    private static final class ProbeFailure extends RuntimeException {
        ProbeFailure(String message) { super(message); }
    }
}
