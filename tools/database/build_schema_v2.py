"""Render fresh-install and additive-upgrade SQL from the checked-in SQL sources."""
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'src/main/resources/db/legacy-compatible-base.sql'
NEW = ROOT / 'src/main/resources/db/cleaning-v2-tables.sql'
FRESH = ROOT / 'src/main/resources/schema.sql'
UPGRADE = ROOT / 'docs/database/migrations/V20261026__upgrade_cleaning_v2.sql'

ADDITIONS = {
    'cleaning_system': {
        'columns': {
            'system_code': 'VARCHAR(64) NULL',
            'system_name': 'VARCHAR(128) NULL',
            'configured_height_m': 'DECIMAL(10,3) NULL',
            'settings_saved_version': 'BIGINT UNSIGNED NULL',
            'settings_saved_snapshot': 'JSON NULL',
            'settings_saved_at': 'DATETIME(3) NULL',
            'settings_applied_version': 'BIGINT UNSIGNED NULL',
            'settings_applied_snapshot': 'JSON NULL',
            'settings_applied_at': 'DATETIME(3) NULL',
            'settings_apply_state': 'VARCHAR(24) NULL',
            'settings_message': 'VARCHAR(512) NULL',
            'settings_source_snapshot': 'JSON NULL',
        },
        'indexes': {'uk_system_public_code': ['system_code']},
        'checks': {'ck_system_public_code': "system_code IS NULL OR CHAR_LENGTH(TRIM(system_code)) > 0"},
    },
    'cleaning_winch': {
        'columns': {
            'winch_code': 'VARCHAR(64) NULL',
            'winch_name': 'VARCHAR(128) NULL',
            'last_maintenance_at': 'DATETIME(3) NULL',
            'threshold_saved_version': 'BIGINT UNSIGNED NULL',
            'threshold_saved_snapshot': 'JSON NULL',
            'threshold_saved_at': 'DATETIME(3) NULL',
            'threshold_applied_version': 'BIGINT UNSIGNED NULL',
            'threshold_applied_snapshot': 'JSON NULL',
            'threshold_applied_at': 'DATETIME(3) NULL',
            'threshold_apply_state': 'VARCHAR(24) NULL',
            'threshold_source_snapshot': 'JSON NULL',
        },
        'indexes': {'uk_winch_public_code': ['system_id', 'winch_code']},
        'checks': {
            'ck_winch_slot': 'slot_no BETWEEN 1 AND 4',
            'ck_winch_public_code': "winch_code IS NULL OR CHAR_LENGTH(TRIM(winch_code)) > 0",
        },
    },
    'cleaning_rope_sensor': {
        'columns': {
            'sensor_code': 'VARCHAR(64) NULL',
            'sensor_name': 'VARCHAR(128) NULL',
            'business_role': 'VARCHAR(24) NULL',
            'installation_position': 'VARCHAR(128) NULL',
            'warning_threshold_kg': 'DECIMAL(14,4) NULL',
            'protection_threshold_kg': 'DECIMAL(14,4) NULL',
            'threshold_version': 'BIGINT UNSIGNED NULL',
            'threshold_rule_snapshot': 'JSON NULL',
        },
        'indexes': {'uk_sensor_public_code': ['sensor_code']},
        'checks': {
            'ck_sensor_slot': 'slot_no BETWEEN 1 AND 2',
            'ck_sensor_public_code': "sensor_code IS NULL OR CHAR_LENGTH(TRIM(sensor_code)) > 0",
        },
    },
}

TABLE_RE = re.compile(r'CREATE TABLE IF NOT EXISTS\s+(\w+)\s*\((.*?)\)\s*ENGINE\s*=\s*InnoDB[^;]*;', re.I | re.S)
COLUMN_RE = re.compile(r'^\s{4}(\w+)\s+((?:BIGINT|SMALLINT|TINYINT|INT|VARCHAR|VARBINARY|BINARY|DECIMAL|DATETIME|DATE|JSON|BOOLEAN)(?:\([0-9,]+\))?(?:\s+UNSIGNED)?)(?:\s|,)', re.I | re.M)

def literal(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"

def columns(body):
    return dict(COLUMN_RE.findall(body))

def type_condition(declaration):
    match = re.match(r'(\w+)(?:\(([0-9,]+)\))?(?:\s+(UNSIGNED))?', declaration, re.I)
    kind, size, unsigned = match.groups()
    kind = kind.lower()
    if kind == 'boolean':
        kind = 'tinyint'
    parts = [f"data_type={literal(kind)}"]
    if kind in ('varchar', 'varbinary', 'binary'):
        parts.append(f'character_maximum_length={size}')
    elif kind == 'decimal':
        precision, scale = size.split(',')
        parts.extend([f'numeric_precision={precision}', f'numeric_scale={scale}'])
    elif kind == 'datetime':
        parts.append(f'datetime_precision={size or 0}')
    if kind in ('bigint', 'int', 'smallint', 'tinyint'):
        parts.append("column_type " + ("LIKE" if unsigned else "NOT LIKE") + " '%unsigned%'")
    return ' AND '.join(parts)

def render_fresh(base, new):
    rendered = []
    for match in TABLE_RE.finditer(base):
        table, body = match.groups()
        extra = ADDITIONS.get(table)
        if extra:
            entries = [f'{name} {definition}' for name, definition in extra['columns'].items()]
            entries += [f"UNIQUE KEY {name} ({', '.join(names)})" for name, names in extra['indexes'].items()]
            entries += [f'CONSTRAINT {name} CHECK ({expression})' for name, expression in extra['checks'].items()]
            body = body.rstrip() + ',\n' + ',\n'.join('    ' + entry for entry in entries) + '\n'
        rendered.append(f'CREATE TABLE IF NOT EXISTS {table} ({body}) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;')
    if len(rendered) != 7:
        raise ValueError('The compatibility source must contain exactly seven legacy tables')
    return ('-- SmartWash V2 fresh install: 14 target tables plus 3 retained legacy-history tables = 17.\n'
            '-- Generated by tools/database/build_schema_v2.py; edit its sources, then regenerate.\n'
            '-- Select an EMPTY development database first. No database creation, seeding or old-data cleanup.\n'
            '-- Existing V1 databases: use docs/database/migrations/V20261026__upgrade_cleaning_v2.sql instead.\n'
            '-- UTC DATETIME(3); business calendar Asia/Shanghai; application must bind UTC explicitly.\n'
            "SET NAMES utf8mb4;\nSET time_zone='+00:00';\n\n" + '\n\n'.join(rendered) + '\n\n' + new)

def render_upgrade(base, new):
    statements = [
        '-- SmartWash V1 -> V2 additive upgrade; MySQL 8.0.16+, pause application writes and back up first.',
        '-- Generated by tools/database/build_schema_v2.py. No DROP TABLE, TRUNCATE, DELETE, or invented backfill.',
        '-- Requires the seven-table device_id V1 baseline. All compatibility checks run before the first ALTER.',
        '-- DDL auto-commits; on interruption repair the reported cause and rerun this idempotent script.',
        "SET NAMES utf8mb4;", "SET time_zone='+00:00';", 'DELIMITER //',
        'DROP PROCEDURE IF EXISTS smartwash_upgrade_cleaning_v20261026//',
        'CREATE PROCEDURE smartwash_upgrade_cleaning_v20261026()', 'BEGIN',
        '    DECLARE v_count BIGINT DEFAULT 0;', '    DECLARE v_collation VARCHAR(64);',
        '    DECLARE v_index_columns TEXT;',
        "    IF DATABASE() IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the intended V1 database first'; END IF;",
        "    IF VERSION() LIKE '%MariaDB%' OR CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8",
        "       OR (SUBSTRING_INDEX(VERSION(), '.', 2)='8.0' AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 3), '.', -1) AS UNSIGNED)<16)",
        "    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='MySQL 8.0.16 or newer is required'; END IF;",
    ]
    def emit(sql):
        statements.append('    ' + sql)
    def fail_if(expression, message):
        emit(f"IF {expression} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT={literal(message)}; END IF;")
    def execute(sql, adapt=False):
        value = literal(sql)
        if adapt:
            value = f"REPLACE({value}, 'utf8mb4_general_ci', v_collation)"
        emit(f'SET @_sw_v2_sql={value};')
        emit('PREPARE sw_v2_stmt FROM @_sw_v2_sql; EXECUTE sw_v2_stmt; DEALLOCATE PREPARE sw_v2_stmt;')
    def verify_primary_and_auto(table, body):
        primary = re.search(r'PRIMARY KEY\s*\(([^)]+)\)', body, re.I)
        if primary:
            expected = ','.join(part.strip() for part in primary.group(1).split(','))
        else:
            inline = re.search(r'^\s{4}(\w+)\s+[^\n]*\bPRIMARY KEY\b', body, re.I | re.M)
            if not inline:
                raise ValueError(f'No primary key defined for {table}')
            expected = inline.group(1)
        emit(f"SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO v_index_columns FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name={literal(table)} AND index_name='PRIMARY';")
        fail_if(f'v_index_columns IS NULL OR v_index_columns<>{literal(expected)}', f'Incompatible primary key: {table}')
        for name in re.findall(r'^\s{4}(\w+)\s+[^\n]*\bAUTO_INCREMENT\b', body, re.I | re.M):
            emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name={literal(table)} AND column_name={literal(name)} AND extra LIKE '%auto_increment%';")
            fail_if('v_count<>1', f'Missing AUTO_INCREMENT: {table}.{name}')
    def verify_constraints(table, body):
        for constraint, names, target, targets in re.findall(r'CONSTRAINT\s+(\w+)\s+FOREIGN KEY\s*\(([^)]+)\)\s+REFERENCES\s+(\w+)\s*\(([^)]+)\)', body, re.I):
            names = ','.join(part.strip() for part in names.split(','))
            targets = ','.join(part.strip() for part in targets.split(','))
            emit(f"SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) INTO v_index_columns FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name={literal(table)} AND constraint_name={literal(constraint)} AND referenced_table_schema=DATABASE() AND referenced_table_name={literal(target)};")
            fail_if(f'v_index_columns IS NULL OR v_index_columns<>{literal(names)}', f'Incompatible foreign key: {table}.{constraint}')
            emit(f"SELECT GROUP_CONCAT(referenced_column_name ORDER BY ordinal_position) INTO v_index_columns FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name={literal(table)} AND constraint_name={literal(constraint)};")
            fail_if(f'v_index_columns IS NULL OR v_index_columns<>{literal(targets)}', f'Incompatible referenced key: {table}.{constraint}')
            emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name={literal(table)} AND constraint_name={literal(constraint)} AND delete_rule='RESTRICT' AND update_rule='RESTRICT';")
            fail_if('v_count<>1', f'Incompatible foreign-key rules: {table}.{constraint}')
        for constraint in re.findall(r'CONSTRAINT\s+(\w+)\s+CHECK\s*\(', body, re.I):
            emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name={literal(table)} AND constraint_name={literal(constraint)} AND constraint_type='CHECK' AND enforced='YES';")
            fail_if('v_count<>1', f'Missing enforced check: {table}.{constraint}')
    for match in TABLE_RE.finditer(base):
        table, body = match.groups()
        names = columns(body)
        emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name={literal(table)} AND engine='InnoDB';")
        fail_if('v_count<>1', f'Incompatible V1 storage engine/table: {table}')
        emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name={literal(table)} AND column_name IN ({','.join(map(literal,names))});")
        fail_if(f'v_count <> {len(names)}', f'Incompatible V1 table: {table}; review identity/schema first')
        verify_primary_and_auto(table, body)
    emit("SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('cleaning_system','cleaning_telemetry_batch','cleaning_telemetry_segment','cleaning_segment_measurement') AND column_name='box_id';")
    fail_if('v_count>0', 'Legacy box_id identity requires a separate reviewed migration')
    emit("SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_user' AND column_name='id' AND data_type='bigint' AND column_type NOT LIKE '%unsigned%';")
    fail_if('v_count<>1', 'V1 t_user.id must be signed BIGINT for account references')
    emit("SELECT collation_name INTO v_collation FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='cleaning_system' AND column_name='system_id';")
    emit("SELECT COUNT(*) INTO v_count FROM information_schema.collations WHERE collation_name=v_collation AND character_set_name='utf8mb4';")
    fail_if('v_count<>1', 'V1 topology must use utf8mb4; review charset conversion separately')
    emit("SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='cleaning_system' AND column_name IN ('system_id','device_id')) OR (table_name='cleaning_winch' AND column_name IN ('system_id','winch_id')) OR (table_name='cleaning_rope_sensor' AND column_name IN ('winch_id','rope_id','tension_sensor_id'))) AND (data_type<>'varchar' OR character_maximum_length<>64 OR collation_name<>v_collation);")
    fail_if('v_count>0', 'V1 topology identity type/collation mismatch; review before upgrading')
    for table, lo, hi in [('cleaning_winch',1,4),('cleaning_rope_sensor',1,2)]:
        execute(f'SELECT COUNT(*) INTO @_sw_v2_bad FROM {table} WHERE slot_no NOT BETWEEN {lo} AND {hi}')
        fail_if('@_sw_v2_bad>0', f'Out-of-range slot in {table}; no automatic data repair')
    # Existing optional columns and indexes are accepted only if compatible; never silently replace them.
    for table, spec in ADDITIONS.items():
        for name, declaration in spec['columns'].items():
            where = f'table_schema=DATABASE() AND table_name={literal(table)} AND column_name={literal(name)}'
            emit(f'SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE {where};')
            emit('IF v_count>0 THEN')
            emit(f'    SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE {where} AND {type_condition(declaration)};')
            fail_if('v_count<>1', f'Incompatible column {table}.{name}')
            if name.endswith('_code'):
                execute(f"SELECT COUNT(*) INTO @_sw_v2_bad FROM {table} WHERE {name} IS NOT NULL AND CHAR_LENGTH(TRIM({name}))=0")
                fail_if('@_sw_v2_bad>0', f'Blank public code in {table}.{name}')
            emit('END IF;')
        for index, names in spec['indexes'].items():
            emit(f"SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index), MAX(non_unique) INTO v_index_columns,v_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name={literal(table)} AND index_name={literal(index)};")
            fail_if(f"v_index_columns IS NOT NULL AND (v_index_columns<>{literal(','.join(names))} OR v_count<>0)", f'Incompatible index {table}.{index}')
            public = names[-1]
            emit(f"IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name={literal(table)} AND column_name={literal(public)}) THEN")
            execute(f"SELECT COUNT(*) INTO @_sw_v2_bad FROM (SELECT {','.join(names)} FROM {table} WHERE {public} IS NOT NULL GROUP BY {','.join(names)} HAVING COUNT(*)>1) sw_duplicates")
            fail_if('@_sw_v2_bad>0', f'Duplicate public codes in {table}')
            emit('END IF;')
    new_tables = list(TABLE_RE.finditer(new))
    if len(new_tables) != 10:
        raise ValueError('Expected ten new V2 tables')
    for match in new_tables:
        table, body = match.groups()
        emit(f"IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name={literal(table)}) THEN")
        emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name={literal(table)} AND engine='InnoDB' AND table_collation=v_collation;")
        fail_if('v_count<>1', f'Incompatible existing V2 storage/collation: {table}')
        verify_primary_and_auto(table, body)
        for name, declaration in columns(body).items():
            full_line = re.search(r'^\s{4}'+re.escape(name)+r'\s+([^\n]*)', body, re.M).group(1).split(' COMMENT ')[0]
            nullable = 'NO' if re.search(r'\bNOT NULL\b', full_line, re.I) else 'YES'
            emit(f"SELECT COUNT(*) INTO v_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name={literal(table)} AND column_name={literal(name)} AND is_nullable={literal(nullable)} AND {type_condition(declaration)};")
            fail_if('v_count<>1', f'Incompatible existing V2 column {table}.{name}')
        for unique, index, names in re.findall(r'^\s+(UNIQUE )?KEY\s+(\w+)\s*\(([^)]+)\)', body, re.M):
            expected = ','.join(part.strip() for part in names.split(','))
            emit(f"SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index), MAX(non_unique) INTO v_index_columns,v_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name={literal(table)} AND index_name={literal(index)};")
            fail_if(f'v_index_columns IS NULL OR v_index_columns<>{literal(expected)} OR v_count<>{0 if unique else 1}', f'Incompatible existing V2 index {table}.{index}')
        verify_constraints(table, body)
        emit('END IF;')
    emit('-- All data/schema checks completed. Only additive DDL follows.')
    for table, spec in ADDITIONS.items():
        for name, declaration in spec['columns'].items():
            emit(f"IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name={literal(table)} AND column_name={literal(name)}) THEN")
            if declaration.startswith('VARCHAR'):
                declaration = re.sub(r'(VARCHAR\(\d+\))', r'\1 CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci', declaration)
            execute(f'ALTER TABLE {table} ADD COLUMN {name} {declaration}', adapt=True)
            emit('END IF;')
        for index, names in spec['indexes'].items():
            emit(f"IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name={literal(table)} AND index_name={literal(index)}) THEN")
            execute(f"ALTER TABLE {table} ADD UNIQUE KEY {index} ({','.join(names)})")
            emit('END IF;')
        for name, condition in spec['checks'].items():
            emit(f"IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name={literal(table)} AND constraint_name={literal(name)}) THEN")
            execute(f'ALTER TABLE {table} ADD CONSTRAINT {name} CHECK ({condition})')
            emit('END IF;')
    for match in new_tables:
        execute(match.group(0), adapt=True)
    statements += ['END//', 'CALL smartwash_upgrade_cleaning_v20261026()//',
                   'DROP PROCEDURE smartwash_upgrade_cleaning_v20261026//', 'DELIMITER ;',
                   '-- No old history is removed or converted into invented raw samples.', '']
    return '\n'.join(statements)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if generated SQL differs; do not write files')
    args = parser.parse_args()
    base, new = BASE.read_text(encoding='utf-8-sig'), NEW.read_text(encoding='utf-8-sig')
    outputs = {FRESH: render_fresh(base, new), UPGRADE: render_upgrade(base, new)}
    for path, expected in outputs.items():
        if args.check:
            if not path.exists() or path.read_text(encoding='utf-8-sig') != expected:
                raise SystemExit(f'Generated SQL differs: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected, encoding='utf-8', newline='\n')
        print(('checked ' if args.check else 'wrote ') + str(path.relative_to(ROOT)))

if __name__ == '__main__':
    main()
