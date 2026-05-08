#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAR="$ROOT/agent-dashboard-app/target/agent-dashboard-0.1.0-SNAPSHOT.jar"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/agent-dashboard-p2.XXXXXX")"
SESSIONS="$WORK/sessions/2026/04/30"
DB_FILE="$WORK/agent-dashboard.sqlite"
DB_DIR="$WORK/sqlite"
JSONL="$SESSIONS/rollout-smoke.jsonl"
NEXT_DAY_JSONL="$SESSIONS/rollout-next-day-smoke.jsonl"
SECOND_MODEL_JSONL="$SESSIONS/rollout-second-model-session.jsonl"

mkdir -p "$SESSIONS"

cat > "$JSONL" <<'JSONL'
{"timestamp":"2026-04-30T01:00:00Z","type":"session_meta","payload":{"id":"p2-smoke-session"}}
{"timestamp":"2026-04-30T01:00:01Z","type":"turn_context","payload":{"model":"gpt-5-smoke"}}
{"timestamp":"2026-04-30T01:00:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":100,"cached_input_tokens":20,"output_tokens":30,"reasoning_output_tokens":5,"total_tokens":130}}}}
{"timestamp":"2026-04-30T01:00:03Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":150,"cached_input_tokens":20,"output_tokens":70,"reasoning_output_tokens":10,"total_tokens":220}}}}
JSONL

cat > "$NEXT_DAY_JSONL" <<'JSONL'
{"timestamp":"2026-04-30T15:59:00Z","type":"session_meta","payload":{"id":"p2-next-day-session"}}
{"timestamp":"2026-04-30T15:59:01Z","type":"turn_context","payload":{"model":"gpt-5.5"}}
{"timestamp":"2026-04-30T16:00:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":200,"cached_input_tokens":40,"output_tokens":50,"reasoning_output_tokens":5,"total_tokens":250}}}}
JSONL

cat > "$SECOND_MODEL_JSONL" <<'JSONL'
{"timestamp":"2026-04-30T01:10:00Z","type":"session_meta","payload":{"id":"p2-second-model-session"}}
{"timestamp":"2026-04-30T01:10:01Z","type":"turn_context","payload":{"model":"gpt-5-smoke"}}
{"timestamp":"2026-04-30T01:10:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":10,"cached_input_tokens":0,"output_tokens":5,"reasoning_output_tokens":0,"total_tokens":15}}}}
{"timestamp":"2026-04-30T01:10:03Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":20,"cached_input_tokens":0,"output_tokens":10,"reasoning_output_tokens":0,"total_tokens":30}}}}
JSONL

ingest1="$(java -jar "$JAR" --ingest --sessions-dir="$SESSIONS" --db="$DB_FILE" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$ingest1" | grep '"status":"ok"' >/dev/null
printf '%s\n' "$ingest1" | grep '"events_inserted":5' >/dev/null

ingest2="$(java -jar "$JAR" --ingest --sessions-dir="$SESSIONS" --db="$DB_FILE" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$ingest2" | grep '"status":"ok"' >/dev/null
printf '%s\n' "$ingest2" | grep '"events_inserted":0' >/dev/null

report_file="$(java -jar "$JAR" --report --days=30 --db="$DB_FILE" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$report_file" | grep '"total_tokens":500' >/dev/null
if printf '%s\n' "$report_file" | grep ',,' >/dev/null; then
    printf '%s\n' "report JSON contains duplicate comma" >&2
    exit 1
fi
printf '%s\n' "$report_file" | grep '"usage_event_count":5' >/dev/null
printf '%s\n' "$report_file" | grep '"session_id":"p2-smoke-session"' >/dev/null
printf '%s\n' "$report_file" | grep '"session_id":"p2-second-model-session"' >/dev/null
printf '%s\n' "$report_file" | grep '"model":"gpt-5.5"' >/dev/null
printf '%s\n' "$report_file" | grep -E '"model":"gpt-5-smoke"[^}]*"active_seconds":2' >/dev/null

ingest_sharded="$(java -jar "$JAR" --ingest --sessions-dir="$SESSIONS" --db="$DB_DIR" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$ingest_sharded" | grep '"status":"ok"' >/dev/null
printf '%s\n' "$ingest_sharded" | grep '"events_inserted":5' >/dev/null
test -f "$DB_DIR/agent-dashboard-2026-04.sqlite"

report_sharded="$(java -jar "$JAR" --report --days=30 --db="$DB_DIR" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$report_sharded" | grep '"total_tokens":500' >/dev/null
if printf '%s\n' "$report_sharded" | grep ',,' >/dev/null; then
    printf '%s\n' "sharded report JSON contains duplicate comma" >&2
    exit 1
fi
printf '%s\n' "$report_sharded" | grep '"usage_event_count":5' >/dev/null
printf '%s\n' "$report_sharded" | grep '"model":"gpt-5-smoke"' >/dev/null
printf '%s\n' "$report_sharded" | grep -E '"model":"gpt-5-smoke"[^}]*"active_seconds":2' >/dev/null
printf '%s\n' "$report_sharded" | grep '"model":"gpt-5.5"' >/dev/null

report_sharded_today="$(java -jar "$JAR" --report --month=2026-05 --db="$DB_DIR" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$report_sharded_today" | grep '"total_tokens":250' >/dev/null
printf '%s\n' "$report_sharded_today" | grep '"model":"gpt-5.5"' >/dev/null

SERVER_DB_DIR="$WORK/startup-sqlite"
PORT=$((18080 + ($$ % 1000)))
java -jar "$JAR" --sessions-dir="$SESSIONS" --db="$SERVER_DB_DIR" --timezone=Asia/Shanghai --port="$PORT" > "$WORK/server.log" 2>&1 &
SERVER_PID=$!
sleep 2
if server_report="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/report?days=30" 2>/dev/null)"; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
    printf '%s\n' "$server_report" | grep '"total_tokens":220' >/dev/null
    test -f "$SERVER_DB_DIR/agent-dashboard-2026-04.sqlite"
else
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
    if ! grep 'Operation not permitted' "$WORK/server.log" >/dev/null; then
        cat "$WORK/server.log" >&2
        exit 1
    fi
fi

printf '%s\n' "P2 smoke test passed"
