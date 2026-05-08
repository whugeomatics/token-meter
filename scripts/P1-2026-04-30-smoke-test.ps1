param(
    [int]$Port = 18080
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "agent-dashboard-app\target\agent-dashboard-0.1.0-SNAPSHOT.jar"

if (-not (Test-Path -LiteralPath $jar)) {
    throw "Jar not found: $jar"
}

$process = Start-Process -FilePath "java" -ArgumentList @("-jar", $jar, "--port=$Port") -WorkingDirectory $root -PassThru -WindowStyle Hidden

try {
    $baseUrl = "http://127.0.0.1:$Port"
    $ready = $false
    for ($i = 0; $i -lt 20; $i++) {
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 2
            if ($health.status -eq "ok") {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }

    if (-not $ready) {
        throw "Service did not become ready"
    }

    $report = Invoke-RestMethod -Uri "$baseUrl/api/report?days=7" -TimeoutSec 5
    foreach ($field in @("range", "summary", "daily", "models", "sessions")) {
        if (-not $report.PSObject.Properties.Name.Contains($field)) {
            throw "Report missing field: $field"
        }
    }

    $html = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/" -TimeoutSec 5
    if ($html.StatusCode -ne 200) {
        throw "Dashboard status was $($html.StatusCode)"
    }
    if ($html.Content -notmatch 'id="app"') {
        throw "Dashboard root node not found"
    }
    if ($html.Content -notmatch 'Codex Usage Dashboard') {
        throw "Dashboard title not found"
    }

    Write-Output "P1 smoke test passed"
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
}
