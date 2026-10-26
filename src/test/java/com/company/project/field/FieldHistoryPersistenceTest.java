package com.company.project.field;

import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.telemetry.MeasurementAggregateBo;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
import com.company.project.domain.bo.telemetry.TelemetrySegmentBo;
import com.company.project.domain.entity.cleaning.MeasurementAggregateEntity;
import com.company.project.domain.entity.cleaning.TelemetrySegmentEntity;
import com.company.project.info.buffer.cleaning.TelemetryHistoryBuffer;
import com.company.project.repository.cleaning.JdbcTelemetryHistoryDao;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** IDE 直接运行，从 field-config/field-test.yml 读取采集目录和测试库；普通 mvn test 排除此入口。 */
class FieldHistoryPersistenceTest {
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final List<String> TABLES = List.of("cleaning_telemetry_batch",
            "cleaning_telemetry_segment", "cleaning_segment_measurement");

    @Test
    void capturedSamplesCommitReadBackDeduplicateAndRollback() throws Exception {
        var configuration = FieldTestConfiguration.load();
        String runId = "field-" + UUID.randomUUID();
        Path output = configuration.path("field.output-dir", "field-results").resolve("history-" + runId);
        Files.createDirectories(output);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", runId);
        report.put("startedAt", Instant.now());
        report.put("scope", "LEGACY_HISTORY_DAO_FIELD_CAPTURE");
        report.put("productionHistoryPipelineVerified", false);
        report.put("tables", TABLES);
        report.put("verifiedBatches", 0);
        report.put("verifiedSegments", 0);
        report.put("verifiedMeasurements", 0);
        try {
            report.put("phase", "VALIDATE_CAPTURE");
            Path source = configuration.path("field.capture-dir", configuration.text("field.capture-dir"))
                    .resolve("realtime.ndjson");
            InputInfo input = validateInput(source);
            report.put("source", source.toString());
            report.put("deviceId", input.deviceId());
            report.put("inputSamples", input.samples());
            report.put("validStoredValues", input.validStoredValues());

            String url = configuration.text("field.history.datasource.url");
            if (!url.startsWith("jdbc:mysql://")) {
                throw new IllegalArgumentException("field.history.datasource.url must name an existing MySQL test database in field-config/field-test.yml");
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                    configuration.text("field.history.datasource.username"),
                    configuration.text("field.history.datasource.password", ""));
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            Properties connectionProperties = new Properties();
            connectionProperties.setProperty("connectTimeout", "5000");
            connectionProperties.setProperty("socketTimeout", "10000");
            dataSource.setConnectionProperties(connectionProperties);
            // Each verifier query obtains a fresh connection, outside the writer's transaction.
            JdbcTemplate verifier = new JdbcTemplate(dataSource);
            verifier.setQueryTimeout(10);
            report.put("phase", "VERIFY_EXISTING_SCHEMA");
            verifySchema(verifier);

            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class, () -> dataSource);
                context.register(TransactionConfiguration.class);
                context.refresh();
                TelemetryHistoryDao dao = context.getBean(TelemetryHistoryDao.class);
                var service = new CleaningStatisticsServiceImpl(new TelemetryHistoryBuffer(), dao);
                int batchNumber = 0;
                report.put("phase", "PERSIST_READ_BACK_AND_DEDUPLICATE");
                try (BufferedReader reader = Files.newBufferedReader(source)) {
                    List<TelemetrySample> pending = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        pending.add(JSON.readValue(line, TelemetrySample.class));
                        if (pending.size() == 100) {
                            persistAndVerify(service, verifier, batch(pending, runId, ++batchNumber), report);
                            pending.clear();
                        }
                    }
                    if (!pending.isEmpty()) {
                        persistAndVerify(service, verifier, batch(pending, runId, ++batchNumber), report);
                    }
                }
                report.put("phase", "VERIFY_ROLLBACK");
                verifyRollback(dao, verifier, input, runId);
                report.put("rollbackVerified", true);
            }
            report.put("phase", "COMPLETE");
            report.put("status", "PASSED");
        } catch (Throwable failure) {
            report.put("status", "FAILED");
            // JDBC exception messages can include a URL, username or server details.
            report.put("failureType", failure.getClass().getSimpleName());
            throw new AssertionError("Field history check failed; see " + output.resolve("summary.json")
                    + " (" + failure.getClass().getSimpleName() + ")");
        } finally {
            report.put("finishedAt", Instant.now());
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(), report);
            System.out.println("Field history report: " + output.resolve("summary.json"));
        }
    }

    private static void verifySchema(JdbcTemplate jdbc) {
        for (String table : TABLES) {
            String engine = jdbc.queryForObject("SELECT ENGINE FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", String.class, table);
            assertEquals("InnoDB", engine, "History transaction checks require existing InnoDB tables");
        }
        jdbc.queryForList("SELECT device_id,batch_id,first_sequence,last_sequence FROM cleaning_telemetry_batch LIMIT 0");
        jdbc.queryForList("SELECT device_id,batch_id,telemetry_sequence,session_id,round_no,direction,"
                + "started_at,finished_at,start_height_m,end_height_m,trigger_reason,policy_version "
                + "FROM cleaning_telemetry_segment LIMIT 0");
        jdbc.queryForList("SELECT device_id,batch_id,telemetry_sequence,metric_type,channel_no,target_id,"
                + "minimum_value,maximum_value,average_value,latest_value,sample_count "
                + "FROM cleaning_segment_measurement LIMIT 0");
    }

    private static void persistAndVerify(CleaningStatisticsServiceImpl service, JdbcTemplate verifier,
                                         TelemetryBatchBo batch, Map<String, Object> report) {
        service.acceptHistoryBatch(batch);
        service.runPersistenceCycle();
        verifyBatch(verifier, batch);
        long segments = count(verifier, "cleaning_telemetry_segment", batch.deviceId(), batch.batchId());
        long measurements = count(verifier, "cleaning_segment_measurement", batch.deviceId(), batch.batchId());
        service.acceptHistoryBatch(batch);
        service.runPersistenceCycle();
        assertEquals(1, count(verifier, "cleaning_telemetry_batch", batch.deviceId(), batch.batchId()));
        assertEquals(segments, count(verifier, "cleaning_telemetry_segment", batch.deviceId(), batch.batchId()));
        assertEquals(measurements, count(verifier, "cleaning_segment_measurement", batch.deviceId(), batch.batchId()));
        verifyBatch(verifier, batch);
        report.compute("verifiedBatches", (key, old) -> ((Number) old).longValue() + 1);
        report.compute("verifiedSegments", (key, old) -> ((Number) old).longValue() + segments);
        report.compute("verifiedMeasurements", (key, old) -> ((Number) old).longValue() + measurements);
        report.put("lastVerifiedBatchId", batch.batchId());
        report.put("idempotencyVerified", true);
    }

    private static void verifyBatch(JdbcTemplate jdbc, TelemetryBatchBo batch) {
        Map<String, Object> header = jdbc.queryForMap("SELECT first_sequence,last_sequence "
                + "FROM cleaning_telemetry_batch WHERE device_id=? AND batch_id=?", batch.deviceId(), batch.batchId());
        assertEquals(batch.firstSequence(), ((Number) header.get("first_sequence")).longValue());
        assertEquals(batch.lastSequence(), ((Number) header.get("last_sequence")).longValue());
        assertEquals(batch.segments().size(), count(jdbc, "cleaning_telemetry_segment", batch.deviceId(), batch.batchId()));
        long expectedMeasurements = 0;
        for (TelemetrySegmentBo segment : batch.segments()) {
            jdbc.queryForObject("SELECT session_id,round_no,direction,started_at,finished_at,start_height_m,"
                            + "end_height_m,trigger_reason,policy_version FROM cleaning_telemetry_segment "
                            + "WHERE device_id=? AND batch_id=? AND telemetry_sequence=?", (row, ignored) -> {
                        assertEquals(segment.sessionId(), row.getString("session_id"));
                        assertEquals(0, row.getInt("round_no"));
                        assertEquals(segment.direction(), row.getString("direction"));
                        assertEquals(segment.startedAt().truncatedTo(ChronoUnit.MILLIS), row.getTimestamp("started_at").toInstant());
                        assertEquals(segment.finishedAt().truncatedTo(ChronoUnit.MILLIS), row.getTimestamp("finished_at").toInstant());
                        decimal(segment.startHeightMeters(), row.getBigDecimal("start_height_m"), 3);
                        decimal(segment.endHeightMeters(), row.getBigDecimal("end_height_m"), 3);
                        assertEquals("FIELD_CAPTURE", row.getString("trigger_reason"));
                        assertEquals("field-v1", row.getString("policy_version"));
                        return true;
                    }, batch.deviceId(), batch.batchId(), segment.telemetrySequence());
            verifyMeasurements(jdbc, segment, "ROPE_TENSION_KG", segment.ropeTensions());
            verifyMeasurements(jdbc, segment, "WINCH_SPEED_M_PER_MIN", segment.winchSpeeds());
            verifyMeasurements(jdbc, segment, "WINCH_TORQUE_FORCE_KG", segment.winchTorqueForcesKg());
            expectedMeasurements += segment.ropeTensions().size() + segment.winchSpeeds().size()
                    + segment.winchTorqueForcesKg().size();
        }
        assertEquals(expectedMeasurements, count(jdbc, "cleaning_segment_measurement", batch.deviceId(), batch.batchId()));
    }

    private static void verifyMeasurements(JdbcTemplate jdbc, TelemetrySegmentBo segment,
                                           String metric, List<MeasurementAggregateBo> expected) {
        List<MeasurementAggregateEntity> actual = jdbc.query("SELECT channel_no,target_id,minimum_value,maximum_value,"
                        + "average_value,latest_value,sample_count FROM cleaning_segment_measurement "
                        + "WHERE device_id=? AND batch_id=? AND telemetry_sequence=? AND metric_type=? ORDER BY channel_no",
                (row, ignored) -> new MeasurementAggregateEntity(row.getInt("channel_no"), row.getString("target_id"),
                        row.getBigDecimal("minimum_value"), row.getBigDecimal("maximum_value"),
                        row.getBigDecimal("average_value"), row.getBigDecimal("latest_value"), row.getLong("sample_count")),
                segment.deviceId(), segment.batchId(), segment.telemetrySequence(), metric);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            assertEquals(want.channel(), got.channel());
            assertEquals(want.targetId(), got.targetId());
            assertEquals(want.sampleCount(), got.sampleCount());
            decimal(want.minimum(), got.minimum(), 4);
            decimal(want.maximum(), got.maximum(), 4);
            decimal(want.average(), got.average(), 4);
            decimal(want.latest(), got.latest(), 4);
        }
    }

    private static void verifyRollback(TelemetryHistoryDao dao, JdbcTemplate verifier, InputInfo input, String runId) {
        String batchId = runId + "-rollback";
        String wrongDevice = "field-wrong-" + UUID.randomUUID();
        var valid = new TelemetrySegmentEntity(input.deviceId(), batchId, 1, runId, 0, null,
                input.firstSampleAt(), input.firstSampleAt(), null, null, "FIELD_CAPTURE", "field-v1",
                List.of(new MeasurementAggregateEntity(1, "field-rollback", null, null, null, null, 0)), List.of(), List.of());
        var invalid = new TelemetrySegmentEntity(wrongDevice, batchId, 2, runId, 0, null,
                null, null, null, null, "FIELD_CAPTURE", "field-v1", List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> dao.insertBatch(input.deviceId(), batchId, 1, 2, List.of(valid, invalid)));
        for (String table : TABLES) {
            assertEquals(0, count(verifier, table, input.deviceId(), batchId), "Failed batch must fully roll back");
        }
    }

    private static long count(JdbcTemplate jdbc, String table, String device, String batch) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unexpected history table");
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE device_id=? AND batch_id=?",
                Long.class, device, batch);
        return Objects.requireNonNull(value);
    }

    private static void decimal(BigDecimal expected, BigDecimal actual, int scale) {
        if (expected == null) assertNull(actual);
        else {
            assertNotNull(actual);
            assertEquals(0, expected.setScale(scale, RoundingMode.HALF_UP).compareTo(actual));
        }
    }

    static InputInfo validateInput(Path source) throws Exception {
        long samples = 0, previousSequence = -1;
        long validStoredValues = 0;
        String deviceId = null;
        Instant first = null, previousTime = null;
        try (BufferedReader reader = Files.newBufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                TelemetrySample sample = JSON.readValue(line, TelemetrySample.class);
                validateSample(sample);
                if (deviceId != null && !deviceId.equals(sample.deviceId())) {
                    throw new IllegalArgumentException("Capture contains multiple device identities");
                }
                if (sample.sequence() <= previousSequence || previousTime != null && sample.sampledAt().isBefore(previousTime)) {
                    throw new IllegalArgumentException("Capture sequence or timestamp is out of order");
                }
                deviceId = sample.deviceId();
                previousSequence = sample.sequence();
                previousTime = sample.sampledAt();
                if (first == null) first = sample.sampledAt();
                validStoredValues += sample.ropes().stream().filter(TelemetrySample.RopeSample::valid).count();
                validStoredValues += sample.winches().stream().filter(TelemetrySample.WinchSample::speedValid).count();
                validStoredValues += sample.winches().stream().filter(TelemetrySample.WinchSample::torqueValid).count();
                if (sample.heightValid()) validStoredValues++;
                samples++;
            }
        }
        if (samples == 0) throw new IllegalArgumentException("Capture has no realtime samples");
        if (validStoredValues == 0) throw new IllegalArgumentException("Capture has no valid values supported by the legacy history DAO");
        return new InputInfo(deviceId, samples, first, validStoredValues);
    }

    private static void validateSample(TelemetrySample sample) {
        if (sample.deviceId() == null || sample.deviceId().isBlank() || sample.deviceId().length() > 64
                || sample.sampledAt() == null || sample.sequence() < 0
                || sample.ropes().size() > 8 || sample.winches().size() > 4) {
            throw new IllegalArgumentException("Capture identity, time or channel count is invalid");
        }
        for (var rope : sample.ropes()) validateMetric(rope.ropeId(), rope.valid(), rope.tension());
        for (var winch : sample.winches()) {
            validateMetric(winch.winchId(), winch.speedValid(), winch.speed());
            validateMetric(winch.winchId(), winch.torqueValid(), winch.torqueKg());
        }
        if (sample.heightValid() && sample.ringHeightMeters() == null) {
            throw new IllegalArgumentException("Valid ring height has no value");
        }
    }

    private static void validateMetric(String id, boolean valid, BigDecimal value) {
        if (id != null && id.length() > 64 || valid && (id == null || id.isBlank() || value == null)) {
            throw new IllegalArgumentException("Valid capture metric has no identity or value");
        }
    }

    static TelemetryBatchBo batch(List<TelemetrySample> samples, String runId, int number) {
        String batchId = runId + "-" + number;
        List<TelemetrySegmentBo> segments = new ArrayList<>();
        for (TelemetrySample sample : samples) {
            List<MeasurementAggregateBo> ropes = new ArrayList<>(), speeds = new ArrayList<>(), torques = new ArrayList<>();
            for (int i = 0; i < sample.ropes().size(); i++) {
                var rope = sample.ropes().get(i);
                metric(ropes, i + 1, rope.ropeId(), rope.valid(), rope.tension());
            }
            for (int i = 0; i < sample.winches().size(); i++) {
                var winch = sample.winches().get(i);
                metric(speeds, i + 1, winch.winchId(), winch.speedValid(), winch.speed());
                metric(torques, i + 1, winch.winchId(), winch.torqueValid(), winch.torqueKg());
            }
            BigDecimal height = sample.heightValid() ? sample.ringHeightMeters() : null;
            // Truncate explicitly so MySQL DATETIME(3) rounding cannot move a frame into the next millisecond.
            Instant time = sample.sampledAt().truncatedTo(ChronoUnit.MILLIS);
            segments.add(new TelemetrySegmentBo(sample.deviceId(), batchId, sample.sequence(), runId, 0,
                    sample.status() == null ? null : sample.status().direction(), time, time, height, height,
                    "FIELD_CAPTURE", "field-v1", ropes, speeds, torques));
        }
        return new TelemetryBatchBo(batchId, samples.get(0).deviceId(), samples.get(0).sequence(),
                samples.get(samples.size() - 1).sequence(), segments);
    }

    private static void metric(List<MeasurementAggregateBo> metrics, int channel, String id, boolean valid, BigDecimal value) {
        if (id == null || id.isBlank()) return;
        BigDecimal actual = valid ? value : null;
        metrics.add(new MeasurementAggregateBo(channel, id, actual, actual, actual, actual, valid ? 1 : 0));
    }

    record InputInfo(String deviceId, long samples, Instant firstSampleAt, long validStoredValues) { }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean TelemetryHistoryDao historyDao(DataSource source) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            return new JdbcTelemetryHistoryDao(jdbc);
        }
    }
}
