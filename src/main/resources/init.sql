-- ============================================
-- IoT Platform 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS `new`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `new`;

-- ============================================
-- 用户表
-- ============================================
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

-- 管理员用户需手动创建或通过注册流程创建

-- ============================================
-- 设备表
-- ============================================
CREATE TABLE IF NOT EXISTS t_device (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    device_code      VARCHAR(64)  NOT NULL COMMENT '设备编码',
    device_name      VARCHAR(128) NOT NULL COMMENT '设备名称',
    device_type      VARCHAR(64)  DEFAULT NULL COMMENT '设备类型',
    model_key        VARCHAR(64)  DEFAULT NULL COMMENT '3D模型标识',
    location         VARCHAR(256) DEFAULT NULL COMMENT '安装位置',
    pos_x            DOUBLE       DEFAULT 0 COMMENT '3D坐标X',
    pos_y            DOUBLE       DEFAULT 0 COMMENT '3D坐标Y',
    pos_z            DOUBLE       DEFAULT 0 COMMENT '3D坐标Z',
    rotation_x       DOUBLE       DEFAULT 0 COMMENT '旋转X',
    rotation_y       DOUBLE       DEFAULT 0 COMMENT '旋转Y',
    rotation_z       DOUBLE       DEFAULT 0 COMMENT '旋转Z',
    scale_x          DOUBLE       DEFAULT 1 COMMENT '缩放X',
    scale_y          DOUBLE       DEFAULT 1 COMMENT '缩放Y',
    scale_z          DOUBLE       DEFAULT 1 COMMENT '缩放Z',
    status           TINYINT      DEFAULT 0 COMMENT '状态 0=离线 1=在线 2=告警',
    firmware_version VARCHAR(32)  DEFAULT NULL COMMENT '固件版本',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- ============================================
-- 告警表
-- ============================================
CREATE TABLE IF NOT EXISTS t_alarm (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    device_id     BIGINT       DEFAULT NULL COMMENT '设备ID',
    alarm_code    VARCHAR(64)  NOT NULL COMMENT '告警编码',
    alarm_name    VARCHAR(128) NOT NULL COMMENT '告警名称',
    alarm_level   TINYINT      DEFAULT 0 COMMENT '告警级别 0=提示 1=一般 2=严重 3=紧急',
    alarm_type    TINYINT      DEFAULT 0 COMMENT '告警类型',
    alarm_content TEXT         DEFAULT NULL COMMENT '告警内容',
    status        TINYINT      DEFAULT 0 COMMENT '状态 0=未处理 1=已处理',
    trigger_time  DATETIME     DEFAULT NULL COMMENT '触发时间',
    recover_time  DATETIME     DEFAULT NULL COMMENT '恢复时间',
    handled_by    VARCHAR(64)  DEFAULT NULL COMMENT '处理人',
    handle_remark VARCHAR(512) DEFAULT NULL COMMENT '处理备注',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_device_id (device_id),
    KEY idx_alarm_level (alarm_level),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警表';
