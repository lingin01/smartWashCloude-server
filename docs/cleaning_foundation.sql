-- 在 application-dev.yml 指向的目标数据库执行；不使用旧 init.sql 中硬编码的数据库名。
-- 本脚本只建立清洗系统拓扑和压缩历史拉力的冷数据地基，不会自动迁移旧表。

CREATE TABLE IF NOT EXISTS cleaning_system (
    system_id VARCHAR(64) PRIMARY KEY,
    tank_id VARCHAR(64) NULL,
    box_id VARCHAR(64) NOT NULL UNIQUE,
    ring_id VARCHAR(64) NOT NULL,
    ring_own_weight_kg DECIMAL(12,3) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cleaning_winch (
    winch_id VARCHAR(64) PRIMARY KEY,
    system_id VARCHAR(64) NOT NULL,
    slot_no TINYINT UNSIGNED NOT NULL,
    UNIQUE KEY uk_system_slot (system_id, slot_no),
    CONSTRAINT fk_winch_system FOREIGN KEY (system_id) REFERENCES cleaning_system(system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cleaning_rope_sensor (
    rope_id VARCHAR(64) PRIMARY KEY,
    winch_id VARCHAR(64) NOT NULL,
    slot_no TINYINT UNSIGNED NOT NULL,
    tension_sensor_id VARCHAR(64) NOT NULL UNIQUE,
    UNIQUE KEY uk_winch_rope_slot (winch_id, slot_no),
    CONSTRAINT fk_rope_winch FOREIGN KEY (winch_id) REFERENCES cleaning_winch(winch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cleaning_telemetry_batch (
    box_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    first_sequence BIGINT UNSIGNED NOT NULL,
    last_sequence BIGINT UNSIGNED NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (box_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cleaning_telemetry_segment (
    box_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    telemetry_sequence BIGINT UNSIGNED NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    round_no INT NOT NULL DEFAULT 0,
    direction VARCHAR(16) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    start_height_m DECIMAL(10,3) NULL,
    end_height_m DECIMAL(10,3) NULL,
    trigger_reason VARCHAR(64) NULL,
    policy_version VARCHAR(64) NULL,
    PRIMARY KEY (box_id, batch_id, telemetry_sequence),
    KEY idx_box_time (box_id, started_at),
    KEY idx_session_round (session_id, round_no),
    CONSTRAINT fk_segment_batch FOREIGN KEY (box_id, batch_id)
        REFERENCES cleaning_telemetry_batch(box_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- metric_type: ROPE_TENSION_KG、WINCH_SPEED_M_PER_MIN、WINCH_TORQUE_FORCE_KG。
-- 旧盒子历史报文不带这些数组，因此旧批次不会生成测点行，不能补造历史拉力。
CREATE TABLE IF NOT EXISTS cleaning_segment_measurement (
    box_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    telemetry_sequence BIGINT UNSIGNED NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    channel_no TINYINT UNSIGNED NOT NULL,
    target_id VARCHAR(64) NULL,
    minimum_value DECIMAL(14,4) NULL,
    maximum_value DECIMAL(14,4) NULL,
    average_value DECIMAL(14,4) NULL,
    latest_value DECIMAL(14,4) NULL,
    sample_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (box_id, batch_id, telemetry_sequence, metric_type, channel_no),
    KEY idx_target_metric (target_id, metric_type),
    CONSTRAINT fk_measurement_segment FOREIGN KEY (box_id, batch_id, telemetry_sequence)
        REFERENCES cleaning_telemetry_segment(box_id, batch_id, telemetry_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
