#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAR="$ROOT/token-meter-app/target/token-meter-app-0.1.0-SNAPSHOT.jar"
COLLECTOR_JAR="$ROOT/token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/token-meter-p25.XXXXXX")"
SESSIONS="$WORK/sessions/2026/04/30"
SERVER_DB="$WORK/server-db"
DIRECT_DB="$WORK/direct-db"
DEFAULT_HOME="$WORK/default-home"
DEFAULT_SESSIONS="$DEFAULT_HOME/.codex/sessions/2026/04/30"
JSONL="$SESSIONS/rollout-team-smoke.jsonl"
DEFAULT_JSONL="$DEFAULT_SESSIONS/rollout-default-collector.jsonl"
PAYLOAD="$WORK/team-payload.json"
CONFLICT_PAYLOAD="$WORK/team-conflict-payload.json"
PORT=$((19080 + ($$ % 1000)))
TOKEN="p25-smoke-token"
BAD_TOKEN="p25-bad-token"
ADMIN_TOKEN="p25-admin-token"

test -f "$JAR"
test -f "$COLLECTOR_JAR"
if jar tf "$COLLECTOR_JAR" | grep -E '(^static/|local/token/meter/http/|local/token/meter/report/|local/token/meter/store/|local/token/meter/app/TokenMeterApp|DashboardServer|AdminService|AdminAuth|DashboardPage|org/sqlite/|sqlite-jdbc)' >/dev/null; then
  printf '%s\n' "collector jar contains dashboard/database classes or static assets" >&2
  exit 1
fi

mkdir -p "$SESSIONS" "$DEFAULT_SESSIONS"

cat > "$JSONL" <<'JSONL'
{"timestamp":"2026-04-30T01:00:00Z","type":"session_meta","payload":{"id":"p25-smoke-session"}}
{"timestamp":"2026-04-30T01:00:01Z","type":"turn_context","payload":{"model":"gpt-5-team-smoke"}}
{"timestamp":"2026-04-30T01:00:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":100,"cached_input_tokens":20,"output_tokens":30,"reasoning_output_tokens":5,"total_tokens":130}}}}
{"timestamp":"2026-04-30T01:00:03Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":150,"cached_input_tokens":20,"output_tokens":70,"reasoning_output_tokens":10,"total_tokens":220}}}}
JSONL

cat > "$DEFAULT_JSONL" <<'JSONL'
{"timestamp":"2026-04-30T02:00:00Z","type":"session_meta","payload":{"id":"p25-default-collector-session"}}
{"timestamp":"2026-04-30T02:00:01Z","type":"turn_context","payload":{"model":"gpt-5-team-smoke"}}
{"timestamp":"2026-04-30T02:00:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":20,"cached_input_tokens":0,"output_tokens":10,"reasoning_output_tokens":0,"total_tokens":30}}}}
JSONL

created="$(java -jar "$JAR" --create-device-token --db="$SERVER_DB" --timezone=Asia/Shanghai \
  --team-id=team-smoke --user-id=user-alice --device-id=device-alice \
  --device-name="Alice MacBook" 2>&1)"
printf '%s\n' "$created" | grep '"registered":true' >/dev/null
TOKEN="$(printf '%s\n' "$created" | sed -n 's/.*"device_token":"\([^"]*\)".*/\1/p')"
test -n "$TOKEN"
test -f "$SERVER_DB/token-meter-team-registry.sqlite"

cat > "$PAYLOAD" <<'JSON'
{"collector_version":"0.1.0","client_user_id":"user-alice","client_device_id":"device-alice","events":[
{"event_key":"codex|p25-smoke-session|fixture|1|130|100|30","tool":"codex","session_id":"p25-smoke-session","model":"gpt-5-team-smoke","timestamp":"2026-04-30T01:00:02Z","input_tokens":100,"cached_input_tokens":20,"output_tokens":30,"reasoning_output_tokens":5,"total_tokens":130},
{"event_key":"codex|p25-smoke-session|fixture|2|220|150|70","tool":"codex","session_id":"p25-smoke-session","model":"gpt-5-team-smoke","timestamp":"2026-04-30T01:00:03Z","input_tokens":50,"cached_input_tokens":0,"output_tokens":40,"reasoning_output_tokens":5,"total_tokens":90},
{"event_key":"codex|p25-smoke-session|fixture|3|230|160|70","tool":"codex","session_id":"p25-smoke-session","model":"gpt-5-team-smoke","timestamp":"2026-05-08T06:23:03Z","input_tokens":10,"cached_input_tokens":0,"output_tokens":0,"reasoning_output_tokens":0,"total_tokens":10}
]}
JSON

cat > "$CONFLICT_PAYLOAD" <<'JSON'
{"collector_version":"0.1.0","client_user_id":"user-bob","client_device_id":"device-alice","events":[]}
JSON

java -jar "$JAR" --register-device-token --db="$DIRECT_DB" --timezone=Asia/Shanghai \
  --device-token="$TOKEN" --team-id=team-smoke --user-id=user-alice --device-id=device-alice \
  --device-name="Alice MacBook" 2>&1 | grep '"registered":true' >/dev/null
direct1="$(java -jar "$JAR" --db="$DIRECT_DB" --timezone=Asia/Shanghai --device-token="$TOKEN" --team-ingest-file="$PAYLOAD" 2>&1)"
printf '%s\n' "$direct1" | grep '"accepted":3' >/dev/null
test -f "$DIRECT_DB/token-meter-team-registry.sqlite"
test -f "$DIRECT_DB/token-meter-team-2026-04.sqlite"
direct2="$(java -jar "$JAR" --db="$DIRECT_DB" --timezone=Asia/Shanghai --device-token="$TOKEN" --team-ingest-file="$PAYLOAD" 2>&1)"
printf '%s\n' "$direct2" | grep '"duplicate":3' >/dev/null
direct_report="$(java -jar "$JAR" --team-report --days=30 --db="$DIRECT_DB" --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$direct_report" | grep '"total_tokens":230' >/dev/null
printf '%s\n' "$direct_report" | grep '"usage_event_count":3' >/dev/null
printf '%s\n' "$direct_report" | grep '"avg_tokens_per_call":76.67' >/dev/null
printf '%s\n' "$direct_report" | grep '"reasoning_ratio":0.142857' >/dev/null
printf '%s\n' "$direct_report" | grep -E '"summary":\{[^}]*"active_seconds":1' >/dev/null
printf '%s\n' "$direct_report" | grep -E '"user_id":"user-alice"[^}]*"active_seconds":1' >/dev/null
printf '%s\n' "$direct_report" | grep '"upload_health":' >/dev/null
printf '%s\n' "$direct_report" | grep '"user_id":"user-alice"' >/dev/null
printf '%s\n' "$direct_report" | grep '"device_id":"device-alice"' >/dev/null
direct_conflict="$(java -jar "$JAR" --db="$DIRECT_DB" --timezone=Asia/Shanghai --device-token="$TOKEN" --team-ingest-file="$CONFLICT_PAYLOAD" 2>&1)"
printf '%s\n' "$direct_conflict" | grep '"error_code":"identity_conflict"' >/dev/null
direct_unknown="$(java -jar "$JAR" --db="$DIRECT_DB" --timezone=Asia/Shanghai --device-token="$BAD_TOKEN" --team-ingest-file="$PAYLOAD" 2>&1)"
printf '%s\n' "$direct_unknown" | grep '"error_code":"unauthorized"' >/dev/null

PORT="$PORT" java -jar "$JAR" --sessions-dir="$SESSIONS" --db="$SERVER_DB" --timezone=Asia/Shanghai \
  --device-token="$TOKEN" --team-id=team-smoke --user-id=user-alice --device-id=device-alice \
  --device-name="Alice MacBook" --admin-token="$ADMIN_TOKEN" --port="$PORT" > "$WORK/server.log" 2>&1 &
SERVER_PID=$!
sleep 2

if ! curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
  kill "$SERVER_PID" >/dev/null 2>&1 || true
  wait "$SERVER_PID" >/dev/null 2>&1 || true
  if grep 'Operation not permitted' "$WORK/server.log" >/dev/null; then
    printf '%s\n' "P2.5 smoke test skipped server bind check: sandbox Operation not permitted"
    exit 0
  fi
  cat "$WORK/server.log" >&2
  exit 1
fi

admin_status="$(curl --noproxy '*' -sS -o "$WORK/admin-unauth.json" -w '%{http_code}' \
  "http://127.0.0.1:$PORT/admin.html")"
test "$admin_status" = "401"

login_status="$(curl --noproxy '*' -sS -c "$WORK/admin.cookie" -o "$WORK/admin-login.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"admin_token\":\"$ADMIN_TOKEN\"}" \
  "http://127.0.0.1:$PORT/api/admin/login")"
test "$login_status" = "200"

admin_page_status="$(curl --noproxy '*' -sS -b "$WORK/admin.cookie" -o "$WORK/admin.html" -w '%{http_code}' \
  "http://127.0.0.1:$PORT/admin.html")"
test "$admin_page_status" = "200"

admin_create="$(curl --noproxy '*' -fsS -b "$WORK/admin.cookie" \
  -H 'Content-Type: application/json' \
  -d '{"team_id":"team-smoke","user_id":"user-bob","device_id":"device-bob","device_name":"Bob MacBook"}' \
  "http://127.0.0.1:$PORT/api/admin/device-tokens")"
printf '%s\n' "$admin_create" | grep '"device_token":"' >/dev/null
printf '%s\n' "$admin_create" | grep '"user_id":"user-bob"' >/dev/null
admin_created_token="$(printf '%s\n' "$admin_create" | sed -n 's/.*"device_token":"\([^"]*\)".*/\1/p')"

admin_list="$(curl --noproxy '*' -fsS -b "$WORK/admin.cookie" \
  "http://127.0.0.1:$PORT/api/admin/device-tokens")"
printf '%s\n' "$admin_list" | grep '"user_id":"user-bob"' >/dev/null
printf '%s\n' "$admin_list" | grep '"token_preview":"' >/dev/null
printf '%s\n' "$admin_list" | grep '"token_recoverable":true' >/dev/null
if printf '%s\n' "$admin_list" | grep '"device_token"' >/dev/null; then
  printf '%s\n' "admin token list leaked device_token" >&2
  exit 1
fi
if printf '%s\n' "$admin_list" | grep '"token_secret"' >/dev/null; then
  printf '%s\n' "admin token list leaked token_secret" >&2
  exit 1
fi
bob_token_id="$(printf '%s\n' "$admin_list" | sed -n 's/.*{"token_id":\([0-9][0-9]*\).*"user_id":"user-bob".*/\1/p')"
test -n "$bob_token_id"
admin_copy="$(curl --noproxy '*' -fsS -b "$WORK/admin.cookie" \
  "http://127.0.0.1:$PORT/api/admin/device-tokens/$bob_token_id/token")"
printf '%s\n' "$admin_copy" | grep "\"device_token\":\"$admin_created_token\"" >/dev/null
admin_delete="$(curl --noproxy '*' -fsS -X DELETE -b "$WORK/admin.cookie" \
  "http://127.0.0.1:$PORT/api/admin/device-tokens/$bob_token_id")"
printf '%s\n' "$admin_delete" | grep '"deleted":true' >/dev/null
admin_list_after_delete="$(curl --noproxy '*' -fsS -b "$WORK/admin.cookie" \
  "http://127.0.0.1:$PORT/api/admin/device-tokens")"
if printf '%s\n' "$admin_list_after_delete" | grep '"user_id":"user-bob"' >/dev/null; then
  printf '%s\n' "admin delete did not remove device token binding" >&2
  exit 1
fi

upload1="$(java -jar "$COLLECTOR_JAR" --collect-team --sessions-dir="$SESSIONS" \
  --timezone=Asia/Shanghai --server-url="http://127.0.0.1:$PORT" --device-token="$TOKEN" \
  --user-id=user-alice --device-id=device-alice --days=30 --batch-size=1 2>&1)"
printf '%s\n' "$upload1" | grep '"status":"ok"' >/dev/null
printf '%s\n' "$upload1" | grep '"accepted":2' >/dev/null
printf '%s\n' "$upload1" | grep '"batches":2' >/dev/null
upload2="$(java -jar "$COLLECTOR_JAR" --collect-team --sessions-dir="$SESSIONS" \
  --timezone=Asia/Shanghai --server-url="http://127.0.0.1:$PORT" --device-token="$TOKEN" \
  --user-id=user-alice --device-id=device-alice --days=30 --batch-size=1 2>&1)"
printf '%s\n' "$upload2" | grep '"duplicate":2' >/dev/null

default_upload="$(java -Duser.home="$DEFAULT_HOME" -jar "$COLLECTOR_JAR" --collect-team \
  --timezone=Asia/Shanghai --server-url="http://127.0.0.1:$PORT" --device-token="$TOKEN" \
  --user-id=user-alice --device-id=device-alice --days=30 2>&1)"
printf '%s\n' "$default_upload" | grep '"accepted":1' >/dev/null
if find "$DEFAULT_HOME/.token-meter" -type f -name '*.sqlite' 2>/dev/null | grep . >/dev/null; then
  printf '%s\n' "collector should not create local sqlite files" >&2
  exit 1
fi

conflict_status="$(curl --noproxy '*' -sS -o "$WORK/conflict.json" -w '%{http_code}' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"collector_version":"0.1.0","client_user_id":"user-bob","client_device_id":"device-alice","events":[]}' \
  "http://127.0.0.1:$PORT/api/team/ingest")"
test "$conflict_status" = "409"
grep 'identity_conflict' "$WORK/conflict.json" >/dev/null

unknown_status="$(curl --noproxy '*' -sS -o "$WORK/unknown.json" -w '%{http_code}' \
  -H "Authorization: Bearer $BAD_TOKEN" -H 'Content-Type: application/json' \
  -d '{"collector_version":"0.1.0","client_user_id":"user-alice","client_device_id":"device-alice","events":[]}' \
  "http://127.0.0.1:$PORT/api/team/ingest")"
test "$unknown_status" = "401"

team_report="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/team/report?days=30")"
printf '%s\n' "$team_report" | grep '"total_tokens":250' >/dev/null
printf '%s\n' "$team_report" | grep '"user_id":"user-alice"' >/dev/null
printf '%s\n' "$team_report" | grep '"device_id":"device-alice"' >/dev/null
printf '%s\n' "$team_report" | grep '"model":"gpt-5-team-smoke"' >/dev/null
printf '%s\n' "$team_report" | grep '"teams":' >/dev/null
printf '%s\n' "$team_report" | grep '"team_models":' >/dev/null
printf '%s\n' "$team_report" | grep '"usage_event_count":3' >/dev/null
printf '%s\n' "$team_report" | grep '"avg_tokens_per_session":125.00' >/dev/null
printf '%s\n' "$team_report" | grep '"avg_tokens_per_call":83.33' >/dev/null
printf '%s\n' "$team_report" | grep '"cache_hit_rate":0.117647' >/dev/null
printf '%s\n' "$team_report" | grep '"reasoning_ratio":0.125000' >/dev/null
printf '%s\n' "$team_report" | grep '"active_seconds":1' >/dev/null
printf '%s\n' "$team_report" | grep -E '"user_id":"user-alice"[^}]*"active_seconds":1' >/dev/null
printf '%s\n' "$team_report" | grep -E '"device_id":"device-alice"[^}]*"active_seconds":1' >/dev/null
printf '%s\n' "$team_report" | grep -E '"model":"gpt-5-team-smoke"[^}]*"active_seconds":1' >/dev/null
printf '%s\n' "$team_report" | grep '"upload_health":' >/dev/null
printf '%s\n' "$team_report" | grep '"health_status":"' >/dev/null
printf '%s\n' "$team_report" | grep '"recent_uploads":' >/dev/null
printf '%s\n' "$team_report" | grep '"uploads":' >/dev/null
printf '%s\n' "$team_report" | grep '"upload_date":' >/dev/null
printf '%s\n' "$team_report" | grep '"team_id":"team-smoke"' >/dev/null
filtered_team_report="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/team/report?days=30&team_id=team-smoke")"
printf '%s\n' "$filtered_team_report" | grep '"total_tokens":250' >/dev/null

kill "$SERVER_PID" >/dev/null 2>&1 || true
wait "$SERVER_PID" >/dev/null 2>&1 || true

printf '%s\n' "P2.5 smoke test passed"
