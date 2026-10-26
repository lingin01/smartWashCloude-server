[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Capture', 'History', 'Api', 'Replay', 'Local')]
    [string]$Mode,
    [string]$DeviceId,
    [string]$PlcConfig,
    [string]$CaptureDir,
    [ValidateRange(1, 86400)]
    [int]$DurationSeconds = 60,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$RequestId,
    [ValidateRange(2, 100)]
    [int]$ApiSamples = 3,
    [string]$OutputDir = 'field-results',
    [string]$Config,
    [string]$MavenCommand = 'mvn'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))

# Resolve caller-supplied paths before switching to the project directory.
if ($PlcConfig) { $PlcConfig = (Resolve-Path -LiteralPath $PlcConfig).Path }
if ($CaptureDir) { $CaptureDir = (Resolve-Path -LiteralPath $CaptureDir).Path }
$maven = Get-Command $MavenCommand -ErrorAction Stop
$arguments = @()
if ($Config) { $arguments += "-Dfield.config=$((Resolve-Path -LiteralPath $Config).Path)" }
if ($PSBoundParameters.ContainsKey('OutputDir')) {
    $OutputDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
    $arguments += "-Dfield.output-dir=$OutputDir"
}
if ($DeviceId) { $arguments += "-Dfield.device-id=$DeviceId" }
if ($CaptureDir) { $arguments += "-Dfield.capture-dir=$CaptureDir" }

switch ($Mode) {
    'Capture' {
        $arguments += '-Dtest=PlcFieldCaptureTest'
        if ($PlcConfig) { $arguments += "-Dfield.plc.config=$PlcConfig" }
        if ($PSBoundParameters.ContainsKey('DurationSeconds')) { $arguments += "-Dfield.duration-seconds=$DurationSeconds" }
    }
    'History' {
        $arguments += '-Dtest=FieldHistoryPersistenceTest'
    }
    'Api' {
        $arguments += '-Dtest=FieldApiSmokeTest'
        if ($PSBoundParameters.ContainsKey('BaseUrl')) { $arguments += "-Dfield.api.base-url=$BaseUrl" }
        if ($PSBoundParameters.ContainsKey('ApiSamples')) { $arguments += "-Dfield.api.samples=$ApiSamples" }
        if ($RequestId) { $arguments += "-Dfield.api.request-id=$RequestId" }
    }
    'Replay' {
        $arguments += '-Dtest=PlcFieldReplayTest'
    }
    'Local' {
        # Full application/SSE tests require database and JWT configuration; keep this entry offline.
        $arguments += '-Dtest=com.company.project.field.*Test,com.company.project.cleaning.*Test,!com.company.project.cleaning.RealtimePushIntegrationTest,!com.company.project.field.PlcFieldCaptureTest,!com.company.project.field.FieldHistoryPersistenceTest,!com.company.project.field.FieldApiSmokeTest,!com.company.project.field.PlcFieldReplayTest'
    }
}

Push-Location -LiteralPath $projectRoot
try {
    Write-Host "Running field test mode: $Mode"
    & $maven @arguments test
    $resultCode = $LASTEXITCODE
    if ($resultCode -ne 0) { throw "Field test failed (exit $resultCode). Review field results and target/surefire-reports." }
} finally {
    Pop-Location
}
