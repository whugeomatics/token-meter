$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "agent-dashboard-app\target\agent-dashboard-0.1.0-SNAPSHOT.jar"
$runId = [Guid]::NewGuid().ToString("N")
$work = Join-Path ([System.IO.Path]::GetTempPath()) "agent-dashboard-p2-$runId"
$sessions = Join-Path $work "sessions\2026\04\30"
$db = Join-Path $work "agent-dashboard.sqlite"
$jsonl = Join-Path $sessions "rollout-smoke.jsonl"

New-Item -ItemType Directory -Path $sessions -Force | Out-Null

function Convert-JsonLog {
    param([string[]]$Lines)
    $text = ($Lines -join "`n")
    if ($text -match '(\{.*\})') {
        return $Matches[1] | ConvertFrom-Json
    }
    throw "JSON payload not found: $text"
}

@'
{"timestamp":"2026-04-30T01:00:00Z","type":"session_meta","payload":{"id":"p2-smoke-session"}}
{"timestamp":"2026-04-30T01:00:01Z","type":"turn_context","payload":{"model":"gpt-5-smoke"}}
{"timestamp":"2026-04-30T01:00:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":100,"cached_input_tokens":20,"output_tokens":30,"reasoning_output_tokens":5,"total_tokens":130}}}}
{"timestamp":"2026-04-30T01:00:03Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":150,"cached_input_tokens":20,"output_tokens":70,"reasoning_output_tokens":10,"total_tokens":220}}}}
'@ | Set-Content -Path $jsonl -Encoding UTF8

$ingest1 = & java -jar $jar --ingest --sessions-dir=$sessions --db=$db --timezone=Asia/Shanghai 2>&1
$ingest1Json = Convert-JsonLog $ingest1
if ($ingest1Json.status -ne "ok") {
    throw "first ingestion failed: $ingest1"
}
if ($ingest1Json.events_inserted -ne 2) {
    throw "expected first ingestion to insert 2 events, got $($ingest1Json.events_inserted)"
}

$ingest2 = & java -jar $jar --ingest --sessions-dir=$sessions --db=$db --timezone=Asia/Shanghai 2>&1
$ingest2Json = Convert-JsonLog $ingest2
if ($ingest2Json.status -ne "ok") {
    throw "second ingestion failed: $ingest2"
}
if ($ingest2Json.events_inserted -ne 0) {
    throw "expected second ingestion to insert 0 events, got $($ingest2Json.events_inserted)"
}

$report = & java -jar $jar --report --days=30 --db=$db --timezone=Asia/Shanghai 2>&1
$reportJson = Convert-JsonLog $report
if ($reportJson.summary.total_tokens -ne 220) {
    throw "expected report total_tokens=220, got $($reportJson.summary.total_tokens)"
}
if ($reportJson.models.Count -ne 1) {
    throw "expected one model row, got $($reportJson.models.Count)"
}
if ($reportJson.sessions.Count -ne 1) {
    throw "expected one session row, got $($reportJson.sessions.Count)"
}

Write-Output "P2 smoke test passed"
