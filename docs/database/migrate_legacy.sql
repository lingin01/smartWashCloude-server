-- 显式迁移，适用于仓库旧建表脚本；不会随应用启动自动执行。
-- 执行前备份目标库并暂停应用写入。若任何待移除表含数据，预检将拒绝所有DDL。
-- 请先归档含数据的旧表，再重新执行；不会自动清空数据或关闭外键检查。
-- 保留现有设备归属值，只将列名统一；PLC配置id应与迁移后的device_id一致。
DROP PROCEDURE IF EXISTS smartwash_cleanup_legacy;
DELIMITER //
CREATE PROCEDURE smartwash_cleanup_legacy()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE target_table VARCHAR(64);
    DECLARE has_old INT;
    DECLARE has_new INT;
    DECLARE identity_tables CURSOR FOR
        SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ('cleaning_system','cleaning_telemetry_batch','cleaning_telemetry_segment','cleaning_segment_measurement');
    DECLARE retired_tables CURSOR FOR
        SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ('cleaning_alarm_event','cleaning_alarm_handling_event','cleaning_box_access','cleaning_command_record','cleaning_exception_event','cleaning_history_point','cleaning_maintenance_operation','cleaning_metric_aggregate','cleaning_motor_force_calibration','cleaning_recovery_event','cleaning_rope_binding_history','cleaning_round','cleaning_round_metric','cleaning_safety_policy','cleaning_session','cleaning_settings_revision','cleaning_system_profile','cleaning_winch_profile','t_alarm','t_device');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;

    -- V2复用部分旧表名；出现新链路表后禁止再运行这份历史清理脚本。
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ('cleaning_cycle_record','cleaning_run_record','cleaning_up_sample','cleaning_operation_audit')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V2 schema detected; legacy cleanup is disabled. Use versioned upgrades.';
    END IF;

    OPEN retired_tables;
    retired_preflight: LOOP
        FETCH retired_tables INTO target_table;
        IF finished THEN LEAVE retired_preflight; END IF;
        SET @legacy_rows=0;
        SET @legacy_sql=CONCAT('SELECT COUNT(*) INTO @legacy_rows FROM `',target_table,'`');
        PREPARE legacy_stmt FROM @legacy_sql;
        EXECUTE legacy_stmt;
        DEALLOCATE PREPARE legacy_stmt;
        IF @legacy_rows>0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Retired table contains data; archive it before cleanup. No cleanup DDL has run.';
        END IF;
    END LOOP;
    CLOSE retired_tables;
    SET finished=FALSE;
    OPEN identity_tables;
    identity_preflight: LOOP
        FETCH identity_tables INTO target_table;
        IF finished THEN LEAVE identity_preflight; END IF;
        SELECT COUNT(*) INTO has_old FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name=target_table AND column_name='box_id';
        SELECT COUNT(*) INTO has_new FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name=target_table AND column_name='device_id';
        IF has_old>0 AND has_new>0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Both identity columns exist; resolve mapping before cleanup. No cleanup DDL has run.';
        END IF;
    END LOOP;
    CLOSE identity_tables;

    DROP TABLE IF EXISTS `cleaning_alarm_handling_event`;
    DROP TABLE IF EXISTS `cleaning_box_access`;
    DROP TABLE IF EXISTS `cleaning_command_record`;
    DROP TABLE IF EXISTS `cleaning_exception_event`;
    DROP TABLE IF EXISTS `cleaning_history_point`;
    DROP TABLE IF EXISTS `cleaning_maintenance_operation`;
    DROP TABLE IF EXISTS `cleaning_metric_aggregate`;
    DROP TABLE IF EXISTS `cleaning_motor_force_calibration`;
    DROP TABLE IF EXISTS `cleaning_recovery_event`;
    DROP TABLE IF EXISTS `cleaning_rope_binding_history`;
    DROP TABLE IF EXISTS `cleaning_round_metric`;
    DROP TABLE IF EXISTS `cleaning_safety_policy`;
    DROP TABLE IF EXISTS `cleaning_settings_revision`;
    DROP TABLE IF EXISTS `cleaning_system_profile`;
    DROP TABLE IF EXISTS `cleaning_winch_profile`;
    DROP TABLE IF EXISTS `t_alarm`;
    DROP TABLE IF EXISTS `t_device`;
    DROP TABLE IF EXISTS `cleaning_alarm_event`;
    DROP TABLE IF EXISTS `cleaning_round`;
    DROP TABLE IF EXISTS `cleaning_session`;

    SET finished=FALSE;
    OPEN identity_tables;
    rename_identity: LOOP
        FETCH identity_tables INTO target_table;
        IF finished THEN LEAVE rename_identity; END IF;
        SELECT COUNT(*) INTO has_old FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name=target_table AND column_name='box_id';
        IF has_old>0 THEN
            SET @legacy_sql=CONCAT('ALTER TABLE `',target_table,'` RENAME COLUMN box_id TO device_id');
            PREPARE legacy_stmt FROM @legacy_sql;
            EXECUTE legacy_stmt;
            DEALLOCATE PREPARE legacy_stmt;
        END IF;
    END LOOP;
    CLOSE identity_tables;
END//
DELIMITER ;
CALL smartwash_cleanup_legacy();
DROP PROCEDURE smartwash_cleanup_legacy;
