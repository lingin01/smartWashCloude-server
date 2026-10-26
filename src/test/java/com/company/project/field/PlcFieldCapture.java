package com.company.project.field;

import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import com.company.project.info.buffer.cleaning.TelemetryHistoryBuffer;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.infrastructure.plc.ModbusTcpDevice;
import com.company.project.presentation.plc.PlcDevicePresentation;
import com.company.project.repository.cleaning.CleaningDeviceDao;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.repository.device.DeviceManager;
import com.company.project.service.cleaning.CleaningServiceImpl;
import com.company.project.service.command.CleaningCommandServiceImpl;
import com.company.project.service.lobby.CleaningLobbyServiceImpl;
import com.company.project.service.safety.CleaningSafetyServiceImpl;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/** 现场只读采集工具：IDE可直接运行main，也可由JUnit入口复用。 */
public final class PlcFieldCapture {
    static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    record Result(Path directory, long frames, long completeFrames) {
        boolean passed() { return frames > 0 && frames == completeFrames; }
    }

    /** 直接读取field-config/field-test.yml，不启动Spring Boot、数据库或登录模块。 */
    public static void main(String[] args) throws Exception {
        Result result = captureConfigured();
        if (!result.passed()) throw new IllegalStateException(
                "存在离线/无效点位或没有采样，请查看 " + result.directory().resolve("summary.json"));
        System.out.println("现场PLC采集完成，记录已保存到: " + result.directory().toAbsolutePath());
    }

    static Result captureConfigured() throws Exception {
        var configuration = FieldTestConfiguration.load();
        String deviceId = configuration.text("field.device-id");
        int seconds = configuration.integer("field.duration-seconds", 60);
        if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("采集时长范围为 1～86400 秒");
        var properties = loadConfiguration(configuration.path("field.plc.config", "field-config/plc.yml"), deviceId);
        return capture(properties, deviceId, Duration.ofSeconds(seconds),
                configuration.path("field.output-dir", "field-results"));
    }

    static PlcAccessProperties loadConfiguration(Path file, String deviceId) throws IOException {
        var environment = new StandardEnvironment();
        var sources = new YamlPropertySourceLoader().load("field-plc", new FileSystemResource(file));
        // YAML 中的 ${PLC_HOST:} 等占位符使用当前进程环境变量解析。
        for (int index = sources.size() - 1; index >= 0; index--)
            environment.getPropertySources().addFirst(sources.get(index));
        var properties = Binder.get(environment).bind("cleaning.plc", Bindable.of(PlcAccessProperties.class))
                .orElseThrow(() -> new IllegalArgumentException("点位文件缺少 cleaning.plc"));
        validateConfiguration(properties, deviceId);
        return properties;
    }

    static PlcAccessProperties.Device validateConfiguration(PlcAccessProperties properties, String deviceId) {
        properties.validate();
        var device = properties.activeDevice(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("指定设备及 cleaning.plc.enabled 必须启用"));
        if (device.isWritesEnabled()) throw new IllegalArgumentException("现场采集配置要求 writes-enabled=false");
        if (device.getPoints().stream().noneMatch(PlcAccessProperties.Point::isEnabled))
            throw new IllegalArgumentException("至少启用一个已核对地址、类型和倍率的读取点位");
        return device;
    }

    static Result capture(PlcAccessProperties properties, String deviceId, Duration duration, Path output)
            throws Exception {
        var definition = validateConfiguration(properties, deviceId);
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("采集时长必须大于零");
        Path directory = Files.createDirectories(output).resolve("plc-" + UUID.randomUUID());
        Files.createDirectory(directory);
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("formatVersion", 1);
        manifest.put("mode", "PLC_READ_ONLY_FIELD_CAPTURE");
        manifest.put("startedAt", Instant.now());
        manifest.put("durationSeconds", duration.toMillis() / 1000.0);
        manifest.put("device", definition);
        manifest.put("snapshotMeaning", "Decoded engineering values, not raw Modbus wire frames");
        manifest.put("historyMeaning", "Field evidence only; production upward-run history is not generated");
        JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("manifest.json").toFile(), manifest);

        var pipeline = new Pipeline(properties);
        var counters = new Counters();
        String failure = null;
        System.out.println("现场 PLC 采集目录: " + directory.toAbsolutePath());
        try (var device = new ModbusTcpDevice(definition);
             var snapshots = Files.newBufferedWriter(directory.resolve("snapshots.ndjson"), StandardCharsets.UTF_8);
             var realtime = Files.newBufferedWriter(directory.resolve("realtime.ndjson"), StandardCharsets.UTF_8);
             var points = Files.newBufferedWriter(directory.resolve("points.csv"), StandardCharsets.UTF_8)) {
            points.write("sampledAt,sequence,deviceId,frameOnline,pointId,field,index,symbol,functionCode,address,dataType,byteOrder,scale,offset,unit,value,valid\n");
            long started = System.nanoTime();
            long lastProgress = started - Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() - started < duration.toNanos()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("现场采集已中断");
                if (!device.due(System.nanoTime())) {
                    Thread.sleep(10);
                    continue;
                }
                // 此测试入口只有 poll；不消费命令队列、不调用设备 write。
                var frame = device.poll(() -> false);
                if (frame == null) continue;
                writeLine(snapshots, frame);
                var sample = pipeline.accept(frame);
                writeLine(realtime, sample);
                for (var point : frame.points()) {
                    var mapping = definition.getPoints().stream()
                            .filter(p -> p.isEnabled() && p.getId().equals(point.pointId())).findFirst().orElseThrow();
                    csv(points, frame.sampledAt(), frame.sequence(), frame.deviceId(), frame.online(),
                            point.pointId(), point.field(), point.index(), mapping.getSymbol(), mapping.getFunctionCode(),
                            mapping.getAddress(), mapping.getDataType(), mapping.getByteOrder(), mapping.getScale(),
                            mapping.getOffset(), point.unit(), point.value(), point.valid());
                }
                // 每帧立即刷新，现场中止后仍可保留已完成帧。
                points.flush();
                counters.accept(frame);
                if (System.nanoTime() - lastProgress >= Duration.ofSeconds(5).toNanos()) {
                    System.out.printf("已采集 %d 帧，完整 %d 帧，当前在线=%s，有效点=%d/%d%n",
                            counters.frames, counters.completeFrames, frame.online(),
                            frame.points().stream().filter(p -> p.valid() && p.value() != null).count(), frame.points().size());
                    lastProgress = System.nanoTime();
                }
            }
        } catch (Exception error) {
            failure = error.getClass().getSimpleName();
            throw error;
        } finally {
            var summary = counters.summary();
            summary.put("finishedAt", Instant.now());
            summary.put("failureType", failure);
            if (failure != null) summary.put("passed", false);
            summary.put("processStatistics", pipeline.statistics.statistics(deviceId));
            JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(), summary);
        }
        return new Result(directory, counters.frames, counters.completeFrames);
    }

    /** 同一入口用于现场和离线回放，复用真实表示层、大厅、清洗转换与统计。 */
    static final class Pipeline {
        private final CleaningServiceImpl cleaning;
        private final PlcDevicePresentation presentation;
        private final Map<String, Long> sequences = new LinkedHashMap<>();
        private final Map<String, Instant> sampleTimes = new LinkedHashMap<>();
        final CleaningStatisticsServiceImpl statistics;

        Pipeline(PlcAccessProperties properties) {
            var devices = new DeviceManager(properties, mock(CleaningDeviceDao.class));
            statistics = new CleaningStatisticsServiceImpl(new TelemetryHistoryBuffer(), mock(TelemetryHistoryDao.class));
            cleaning = new CleaningServiceImpl(devices,
                    new CleaningCommandServiceImpl(devices, new PlcMessageBuffer()), statistics);
            presentation = new PlcDevicePresentation(new CleaningLobbyServiceImpl(cleaning,
                    new CleaningSafetyServiceImpl(), statistics));
        }

        TelemetrySample accept(PlcReadSnapshotDto frame) {
            if (frame == null || frame.deviceId() == null || frame.deviceId().isBlank()
                    || frame.sampledAt() == null || frame.sequence() < 0)
                throw new IllegalArgumentException("采集帧缺少有效设备编号、时间或序号");
            Long previous = sequences.get(frame.deviceId());
            if (previous != null && frame.sequence() <= previous)
                throw new IllegalArgumentException("采集帧序号没有严格递增");
            Instant previousTime = sampleTimes.get(frame.deviceId());
            if (previousTime != null && frame.sampledAt().isBefore(previousTime))
                throw new IllegalArgumentException("采集帧时间倒退");
            presentation.onSnapshot(frame);
            var sample = cleaning.latest(frame.deviceId()).orElseThrow();
            if (sample.sequence() != frame.sequence()) throw new IllegalArgumentException("采集帧序号没有严格递增");
            sequences.put(frame.deviceId(), frame.sequence());
            sampleTimes.put(frame.deviceId(), frame.sampledAt());
            return sample;
        }
    }

    static long replay(Path input, Path output) throws Exception {
        Path directory = Files.createDirectories(output).resolve("replay-" + UUID.randomUUID());
        Files.createDirectory(directory);
        var summary = new LinkedHashMap<String, Object>();
        summary.put("mode", "OFFLINE_BUSINESS_REPLAY");
        summary.put("businessCorrectnessVerified", false);
        summary.put("source", input.toAbsolutePath().toString());
        summary.put("startedAt", Instant.now());
        long frames = 0;
        String deviceId = null;
        var pipeline = new Pipeline(new PlcAccessProperties());
        try (var reader = Files.newBufferedReader(input.resolve("snapshots.ndjson"));
             var writer = Files.newBufferedWriter(directory.resolve("realtime.ndjson"))) {
            for (String line; (line = reader.readLine()) != null;) {
                var snapshot = JSON.readValue(line, PlcReadSnapshotDto.class);
                if (snapshot == null) throw new IllegalArgumentException("采集帧不能为空");
                if (deviceId != null && !deviceId.equals(snapshot.deviceId()))
                    throw new IllegalArgumentException("单次现场采集文件包含多个设备");
                var sample = pipeline.accept(snapshot);
                writeLine(writer, sample);
                deviceId = snapshot.deviceId();
                frames++;
            }
            if (frames == 0) throw new IllegalArgumentException("采集文件为空");
            summary.put("status", "PASSED");
        } catch (Exception failure) {
            summary.put("status", "FAILED");
            summary.put("failureType", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            summary.put("frames", frames);
            summary.put("deviceId", deviceId);
            summary.put("finishedAt", Instant.now());
            JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(), summary);
            System.out.println("离线回放 " + frames + " 帧，结果目录: " + directory.toAbsolutePath());
        }
        return frames;
    }

    static void writeLine(BufferedWriter writer, Object value) throws IOException {
        writer.write(JSON.writeValueAsString(value));
        writer.newLine();
        writer.flush();
    }

    private static void csv(BufferedWriter writer, Object... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) writer.write(',');
            writer.write('"');
            if (values[index] != null) writer.write(values[index].toString().replace("\"", "\"\""));
            writer.write('"');
        }
        writer.newLine();
    }

    private static final class Counters {
        long frames, onlineFrames, completeFrames;
        final Map<String, PointSummary> points = new LinkedHashMap<>();

        void accept(PlcReadSnapshotDto frame) {
            frames++;
            if (frame.online()) onlineFrames++;
            if (frame.online() && !frame.points().isEmpty()
                    && frame.points().stream().allMatch(p -> p.valid() && p.value() != null)) completeFrames++;
            frame.points().forEach(p -> points.computeIfAbsent(p.pointId(), ignored -> new PointSummary())
                    .accept(p));
        }

        Map<String, Object> summary() {
            var result = new LinkedHashMap<String, Object>();
            result.put("frames", frames);
            result.put("onlineFrames", onlineFrames);
            result.put("offlineFrames", frames - onlineFrames);
            result.put("completeFrames", completeFrames);
            result.put("passed", frames > 0 && frames == completeFrames);
            result.put("points", points);
            return result;
        }
    }

    static final class PointSummary {
        public long validReadings, invalidReadings;
        public BigDecimal minimum, maximum, latest;

        void accept(PlcReadSnapshotDto.PointValue value) {
            latest = value.value();
            if (value.valid() && value.value() != null) {
                validReadings++;
                minimum = minimum == null ? value.value() : minimum.min(value.value());
                maximum = maximum == null ? value.value() : maximum.max(value.value());
            } else invalidReadings++;
        }
    }
}
