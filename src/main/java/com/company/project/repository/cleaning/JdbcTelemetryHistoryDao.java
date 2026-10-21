package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.MeasurementAggregateEntity;
import com.company.project.domain.entity.cleaning.TelemetrySegmentEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 历史批次、分段和逐测点统计在同一事务保存；重复 batchId 不重复计数。 */
@Repository
public class JdbcTelemetryHistoryDao implements TelemetryHistoryDao {
    private final JdbcTemplate jdbc;

    public JdbcTelemetryHistoryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean batchExists(String boxId, String batchId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cleaning_telemetry_batch WHERE box_id=? AND batch_id=?",
                Integer.class, boxId, batchId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void insertBatch(String boxId, String batchId, long firstSequence, long lastSequence,
                            List<TelemetrySegmentEntity> segments) {
        int inserted = jdbc.update("INSERT IGNORE INTO cleaning_telemetry_batch "
                        + "(box_id,batch_id,first_sequence,last_sequence) VALUES (?,?,?,?)",
                boxId, batchId, firstSequence, lastSequence);
        if (inserted == 0) return;

        for (TelemetrySegmentEntity segment : segments) {
            if (!boxId.equals(segment.boxId()) || !batchId.equals(segment.batchId())) {
                throw new IllegalArgumentException("历史分段不属于当前批次");
            }
            jdbc.update("INSERT INTO cleaning_telemetry_segment "
                            + "(box_id,batch_id,telemetry_sequence,session_id,round_no,direction,"
                            + "started_at,finished_at,start_height_m,end_height_m,trigger_reason,policy_version) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    boxId, batchId, segment.telemetrySequence(), segment.sessionId(),
                    segment.roundNo(), segment.direction(), timestamp(segment.startedAt()),
                    timestamp(segment.finishedAt()), segment.startHeightMeters(),
                    segment.endHeightMeters(), segment.triggerReason(), segment.policyVersion());
            insertMeasurements(segment, "ROPE_TENSION_KG", segment.ropeTensions());
            insertMeasurements(segment, "WINCH_SPEED_M_PER_MIN", segment.winchSpeeds());
            insertMeasurements(segment, "WINCH_TORQUE_FORCE_KG", segment.winchTorqueForcesKg());
        }
    }

    private void insertMeasurements(TelemetrySegmentEntity segment, String metricType,
                                    List<MeasurementAggregateEntity> values) {
        for (MeasurementAggregateEntity value : values) {
            // sampleCount=0 代表盒子无有效点位，保留通道但不伪造测量值。
            jdbc.update("INSERT INTO cleaning_segment_measurement "
                            + "(box_id,batch_id,telemetry_sequence,metric_type,channel_no,target_id,"
                            + "minimum_value,maximum_value,average_value,latest_value,sample_count) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    segment.boxId(), segment.batchId(), segment.telemetrySequence(), metricType,
                    value.channel(), value.targetId(), value.minimum(), value.maximum(),
                    value.average(), value.latest(), value.sampleCount());
        }
    }

    @Override
    public List<TelemetrySegmentEntity> findByRange(String boxId, Instant fromInclusive,
                                                    Instant toExclusive) {
        return jdbc.query("SELECT box_id,batch_id,telemetry_sequence,session_id,round_no,direction,"
                        + "started_at,finished_at,start_height_m,end_height_m,trigger_reason,policy_version "
                        + "FROM cleaning_telemetry_segment WHERE box_id=? AND started_at>=? "
                        + "AND started_at<? ORDER BY started_at,telemetry_sequence LIMIT 5000",
                (row, ignored) -> {
                    String batchId = row.getString("batch_id");
                    long sequence = row.getLong("telemetry_sequence");
                    return new TelemetrySegmentEntity(row.getString("box_id"), batchId, sequence,
                            row.getString("session_id"), row.getInt("round_no"),
                            row.getString("direction"), instant(row.getTimestamp("started_at")),
                            instant(row.getTimestamp("finished_at")),
                            row.getBigDecimal("start_height_m"), row.getBigDecimal("end_height_m"),
                            row.getString("trigger_reason"), row.getString("policy_version"),
                            findMeasurements(boxId, batchId, sequence, "ROPE_TENSION_KG"),
                            findMeasurements(boxId, batchId, sequence, "WINCH_SPEED_M_PER_MIN"),
                            findMeasurements(boxId, batchId, sequence, "WINCH_TORQUE_FORCE_KG"));
                }, boxId, timestamp(fromInclusive), timestamp(toExclusive));
    }

    private List<MeasurementAggregateEntity> findMeasurements(String boxId, String batchId,
                                                                long sequence, String metricType) {
        return jdbc.query("SELECT channel_no,target_id,minimum_value,maximum_value,average_value,"
                        + "latest_value,sample_count FROM cleaning_segment_measurement "
                        + "WHERE box_id=? AND batch_id=? AND telemetry_sequence=? AND metric_type=? "
                        + "ORDER BY channel_no",
                (row, ignored) -> new MeasurementAggregateEntity(row.getInt("channel_no"),
                        row.getString("target_id"), row.getBigDecimal("minimum_value"),
                        row.getBigDecimal("maximum_value"), row.getBigDecimal("average_value"),
                        row.getBigDecimal("latest_value"), row.getLong("sample_count")),
                boxId, batchId, sequence, metricType);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
