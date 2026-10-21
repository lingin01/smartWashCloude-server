param([string]$SchemaPath = (Join-Path $PSScriptRoot 'cleaning_schema_v1.sql'))
$ErrorActionPreference = 'Stop'

# 只做文件结构核对，不连接数据库，不执行SQL，不生成或打印校验码。
function Read-Tables([string]$Sql) {
    $tables = @{}
    $blocks = [regex]::Matches($Sql, '(?ms)^CREATE TABLE IF NOT EXISTS (?<name>[a-z_]+)\s*\((?<body>.*?)^\) ENGINE=InnoDB[^;]*;')
    foreach ($block in $blocks) {
        $name = $block.Groups['name'].Value
        if ($tables.ContainsKey($name)) { throw "重复表名: $name" }
        $body = $block.Groups['body'].Value
        $columns = @{}
        foreach ($column in [regex]::Matches($body, '(?m)^\s{4}(?<name>[a-z_]+)\s+(?<type>[A-Z]+(?:\([^)]*\))?(?: UNSIGNED)?)(?=\s|,)')) {
            $field = $column.Groups['name'].Value
            if ($columns.ContainsKey($field)) { throw "重复列: $name.$field" }
            $columns[$field] = $column.Groups['type'].Value
        }
        $tables[$name] = @{ Body = $body; Columns = $columns }
    }
    return $tables
}

$sql = Get-Content -LiteralPath $SchemaPath -Raw -Encoding UTF8
$tables = Read-Tables $sql
$expected = @('cleaning_system','cleaning_winch','cleaning_rope_sensor','cleaning_box_access',
    'cleaning_settings_revision','cleaning_session','cleaning_round','cleaning_telemetry_batch',
    'cleaning_telemetry_segment','cleaning_segment_measurement','cleaning_recovery_event',
    'cleaning_alarm_event','cleaning_alarm_handling_event','cleaning_command_record')
if ($tables.Count -ne 14) { throw "应有14张表，实际: $($tables.Count)" }
foreach ($name in $expected) { if (!$tables.ContainsKey($name)) { throw "缺少表: $name" } }

$withoutComments = [regex]::Replace($sql, '(?m)^\s*--.*$', '')
$withoutLiterals = [regex]::Replace($withoutComments, "'(?:''|[^'])*'", "''")
if ($withoutLiterals -match '(?i)\b(DROP|TRUNCATE|DELETE|INSERT|UPDATE|ALTER|USE)\b|CREATE\s+DATABASE') {
    throw 'V1脚本不得包含执行数据修改、删除或数据库切换的语句'
}

$constraintNames = @{}
$fkCount = 0
foreach ($entry in $tables.GetEnumerator()) {
    $name = $entry.Key; $body = $entry.Value.Body; $columns = $entry.Value.Columns
    foreach ($constraint in [regex]::Matches($body, 'CONSTRAINT\s+(?<name>[a-z_]+)')) {
        $symbol = $constraint.Groups['name'].Value
        if ($constraintNames.ContainsKey($symbol)) { throw "重复约束名: $symbol" }
        $constraintNames[$symbol] = $true
    }
    foreach ($key in [regex]::Matches($body, '(?:PRIMARY KEY|UNIQUE KEY\s+[a-z_]+|(?m)^\s*KEY\s+[a-z_]+)\s*\((?<cols>[^)]+)\)')) {
        foreach ($field in $key.Groups['cols'].Value.Split(',')) {
            if (!$columns.ContainsKey($field.Trim())) { throw "索引引用缺失列: $name.$field" }
        }
    }
    foreach ($fk in [regex]::Matches($body, 'FOREIGN KEY\s*\((?<local>[^)]+)\)\s+REFERENCES\s+(?<parent>[a-z_]+)\s*\((?<remote>[^)]+)\)')) {
        ++$fkCount
        $parentName = $fk.Groups['parent'].Value
        if (!$tables.ContainsKey($parentName)) { throw "外键目标表缺失: $parentName" }
        $local = @($fk.Groups['local'].Value.Split(',') | ForEach-Object { $_.Trim() })
        $remote = @($fk.Groups['remote'].Value.Split(',') | ForEach-Object { $_.Trim() })
        if ($local.Count -ne $remote.Count) { throw "外键列数不符: $name" }
        for ($i = 0; $i -lt $local.Count; ++$i) {
            if (!$columns.ContainsKey($local[$i]) -or !$tables[$parentName].Columns.ContainsKey($remote[$i])) { throw "外键列缺失: $name" }
            if ($columns[$local[$i]] -ne $tables[$parentName].Columns[$remote[$i]]) { throw "外键列类型不一致: $name" }
        }
        # 所有外键目标必须有明确主键或唯一键，不能依赖非唯一索引宽松行为。
        $keyPatterns = [regex]::Matches($tables[$parentName].Body, '(?:PRIMARY KEY|UNIQUE KEY\s+[a-z_]+)\s*\((?<cols>[^)]+)\)')
        $matchingKey = @($keyPatterns | Where-Object { (($_.Groups['cols'].Value.Split(',') | ForEach-Object { $_.Trim() }) -join ',') -eq ($remote -join ',') })
        if ($matchingKey.Count -eq 0) { throw "外键目标没有匹配唯一键: $name -> $parentName" }
    }
}

$foundationPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'cleaning_foundation.sql'
$foundation = Read-Tables (Get-Content -LiteralPath $foundationPath -Raw -Encoding UTF8)
foreach ($entry in $foundation.GetEnumerator()) {
    foreach ($field in $entry.Value.Columns.Keys) {
        if (!$tables[$entry.Key].Columns.ContainsKey($field)) { throw "旧DAO基础列被移除: $($entry.Key).$field" }
        if ($tables[$entry.Key].Columns[$field] -ne $entry.Value.Columns[$field]) { throw "旧基础列类型被改变: $($entry.Key).$field" }
    }
}
Write-Output "静态结构检查通过：14张表、$fkCount 个外键；表列/索引/约束/旧基础列核对通过。"
Write-Output '未连接MySQL，未执行DDL；不代表数据库引擎或真实DAO集成验证通过。'
