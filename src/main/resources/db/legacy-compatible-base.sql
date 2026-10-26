-- SmartWash 当前可用数据基线：账号、PLC拓扑、历史批次/记录/测量，共7表。
-- 在已选定的目标库执行；不创建/切换数据库，不插入账号，不自动升级旧表。
-- 旧库先审阅 docs/database/migrate_legacy.sql；不要把本文件当作迁移。

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(256) NOT NULL COMMENT '密码(BCrypt加密)',
    real_name   VARCHAR(64)  DEFAULT NULL COMMENT '真实姓名',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    avatar      VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    role_code   VARCHAR(32)  DEFAULT 'VIEWER' COMMENT '角色 ADMIN/OPERATOR/VIEWER',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS cleaning_system (
    system_id VARCHAR(64) PRIMARY KEY,
    tank_id VARCHAR(64) NULL,
    device_id VARCHAR(64) NOT NULL UNIQUE,
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
    device_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    first_sequence BIGINT UNSIGNED NOT NULL,
    last_sequence BIGINT UNSIGNED NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cleaning_telemetry_segment (
    device_id VARCHAR(64) NOT NULL,
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
    PRIMARY KEY (device_id, batch_id, telemetry_sequence),
    KEY idx_device_time (device_id, started_at),
    KEY idx_session_round (session_id, round_no),
    CONSTRAINT fk_segment_batch FOREIGN KEY (device_id, batch_id)
        REFERENCES cleaning_telemetry_batch(device_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- metric_type: ROPE_TENSION_KG、WINCH_SPEED_M_PER_MIN、WINCH_TORQUE_FORCE_KG。
CREATE TABLE IF NOT EXISTS cleaning_segment_measurement (
    device_id VARCHAR(64) NOT NULL,
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
    PRIMARY KEY (device_id, batch_id, telemetry_sequence, metric_type, channel_no),
    KEY idx_target_metric (target_id, metric_type),
    CONSTRAINT fk_measurement_segment FOREIGN KEY (device_id, batch_id, telemetry_sequence)
        REFERENCES cleaning_telemetry_segment(device_id, batch_id, telemetry_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
