#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
APP_JAR="$ROOT/token-meter-app/target/token-meter-app-0.1.0-SNAPSHOT.jar"
COLLECTOR_JAR="$ROOT/token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/token-meter-p4.XXXXXX")"
SERVER_HOME="$WORK/server-home"
CLIENT_HOME="$WORK/client-home"
SERVER_DB="$WORK/server-db"
CODEX_DIR="$CLIENT_HOME/.codex/sessions/2026/05/24"
CLAUDE_DIR="$CLIENT_HOME/.claude/projects/project-a"
ENV_DIR="$CLIENT_HOME/.token-meter"
ENV_FILE="$ENV_DIR/collector.env"
PORT=$((20080 + ($$ % 1000)))
ADMIN_TOKEN="p4-admin-token"
USER_ID="p4-user"
DEVICE_ID="p4-device"
SERVER_PID=""

cleanup() {
  if [ -n "$SERVER_PID" ]; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

test -f "$APP_JAR"
test -f "$COLLECTOR_JAR"
if jar tf "$COLLECTOR_JAR" | grep -E '(^static/|local/token/meter/http/|local/token/meter/report/|local/token/meter/store/|local/token/meter/app/TokenMeterApp|DashboardServer|AdminService|AdminAuth|DashboardPage|org/sqlite/|sqlite-jdbc)' >/dev/null; then
  printf '%s\n' "collector jar contains dashboard/database classes or static assets" >&2
  exit 1
fi

mkdir -p "$SERVER_HOME" "$CODEX_DIR" "$CLAUDE_DIR" "$ENV_DIR"

cat > "$CODEX_DIR/session.jsonl" <<'JSONL'
{"timestamp":"2026-05-24T01:00:00Z","type":"session_meta","payload":{"id":"p4-codex-session"}}
{"timestamp":"2026-05-24T01:00:01Z","type":"turn_context","payload":{"model":"gpt-5-codex-smoke"}}
{"timestamp":"2026-05-24T01:00:02Z","type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":20,"cached_input_tokens":5,"output_tokens":10,"reasoning_output_tokens":2,"total_tokens":30}}}}
JSONL

cat > "$CLAUDE_DIR/session.jsonl" <<'JSONL'
{"type":"assistant","uuid":"uuid-1","sessionId":"p4-claude-session","timestamp":"2026-05-24T01:00:03Z","message":{"id":"p4-claude-message","type":"message","role":"assistant","model":"claude-sonnet-smoke","usage":{"input_tokens":11,"cache_creation_input_tokens":3,"cache_read_input_tokens":2,"output_tokens":7},"content":[{"type":"text","text":"must not be copied"}]}}
{"type":"assistant","uuid":"uuid-2","sessionId":"p4-claude-session","timestamp":"2026-05-24T01:00:04Z","message":{"id":"p4-claude-message","type":"message","role":"assistant","model":"claude-sonnet-smoke","usage":{"input_tokens":11,"cache_creation_input_tokens":3,"cache_read_input_tokens":2,"output_tokens":7},"content":[{"type":"text","text":"must not be copied duplicate"}]}}
JSONL

PORT="$PORT" java -Duser.home="$SERVER_HOME" -jar "$APP_JAR" \
  --db="$SERVER_DB" --timezone=Asia/Shanghai --admin-token="$ADMIN_TOKEN" --port="$PORT" \
  > "$WORK/server.log" 2>&1 &
SERVER_PID=$!
sleep 2

if ! curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
  if grep 'Operation not permitted' "$WORK/server.log" >/dev/null; then
    printf '%s\n' "P4 smoke test skipped server bind check: sandbox Operation not permitted"
    exit 0
  fi
  cat "$WORK/server.log" >&2
  exit 1
fi

login_status="$(curl --noproxy '*' -sS -c "$WORK/admin.cookie" -o "$WORK/admin-login.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"admin_token\":\"$ADMIN_TOKEN\"}" \
  "http://127.0.0.1:$PORT/api/admin/login")"
test "$login_status" = "200"

created="$(curl --noproxy '*' -fsS -b "$WORK/admin.cookie" \
  -H 'Content-Type: application/json' \
  -d "{\"team_id\":\"team-p4\",\"user_id\":\"$USER_ID\",\"device_id\":\"$DEVICE_ID\",\"device_name\":\"P4 Smoke Device\"}" \
  "http://127.0.0.1:$PORT/api/admin/device-tokens")"
printf '%s\n' "$created" | grep '"device_token":"' >/dev/null
TOKEN="$(printf '%s\n' "$created" | sed -n 's/.*"device_token":"\([^"]*\)".*/\1/p')"
test -n "$TOKEN"

cat > "$ENV_FILE" <<ENV
export TOKEN_METER_SERVER_URL="http://127.0.0.1:$PORT"
export TOKEN_METER_USER_ID="$USER_ID"
export TOKEN_METER_DEVICE_ID="$DEVICE_ID"
export TOKEN_METER_DEVICE_TOKEN="$TOKEN"
export TOKEN_METER_DAYS="7"
ENV
chmod 600 "$ENV_FILE"

upload="$(java -Duser.home="$CLIENT_HOME" -jar "$COLLECTOR_JAR" --collect-team --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$upload" | grep '"status":"ok"' >/dev/null
printf '%s\n' "$upload" | grep '"events":2' >/dev/null
printf '%s\n' "$upload" | grep '"accepted":2' >/dev/null
printf '%s\n' "$upload" | grep '"client_user_id":"p4-user"' >/dev/null
printf '%s\n' "$upload" | grep '"client_device_id":"p4-device"' >/dev/null
if printf '%s\n' "$upload" | grep "$TOKEN" >/dev/null; then
  printf '%s\n' "collector output leaked device token" >&2
  exit 1
fi

duplicate_upload="$(java -Duser.home="$CLIENT_HOME" -jar "$COLLECTOR_JAR" --collect-team --timezone=Asia/Shanghai 2>&1)"
printf '%s\n' "$duplicate_upload" | grep '"duplicate":2' >/dev/null

team_report="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/team/report?days=7")"
printf '%s\n' "$team_report" | grep '"tools":' >/dev/null
printf '%s\n' "$team_report" | grep '"tool":"codex"' >/dev/null
printf '%s\n' "$team_report" | grep '"tool":"claude-code"' >/dev/null
printf '%s\n' "$team_report" | grep '"total_tokens":53' >/dev/null
printf '%s\n' "$team_report" | grep '"model":"gpt-5-codex-smoke"' >/dev/null
printf '%s\n' "$team_report" | grep '"model":"claude-sonnet-smoke"' >/dev/null
if printf '%s\n' "$team_report" | grep 'must not be copied' >/dev/null; then
  printf '%s\n' "team report leaked Claude content" >&2
  exit 1
fi

claude_report="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/team/report?days=7&tool=claude-code")"
printf '%s\n' "$claude_report" | grep '"tool":"claude-code"' >/dev/null
printf '%s\n' "$claude_report" | grep '"total_tokens":23' >/dev/null
if printf '%s\n' "$claude_report" | grep '"tool":"codex"' >/dev/null; then
  printf '%s\n' "claude-code tool filter returned codex data" >&2
  exit 1
fi

codex_report="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/team/report?days=7&tool=codex")"
printf '%s\n' "$codex_report" | grep '"tool":"codex"' >/dev/null
printf '%s\n' "$codex_report" | grep '"total_tokens":30' >/dev/null
if printf '%s\n' "$codex_report" | grep '"tool":"claude-code"' >/dev/null; then
  printf '%s\n' "codex tool filter returned claude-code data" >&2
  exit 1
fi

comparison="$(curl --noproxy '*' -fsS "http://127.0.0.1:$PORT/api/team/report?period=day&compare=previous")"
printf '%s\n' "$comparison" | grep '"comparison":' >/dev/null
printf '%s\n' "$comparison" | grep '"tools":' >/dev/null
printf '%s\n' "$comparison" | grep '"tool":"codex"' >/dev/null
printf '%s\n' "$comparison" | grep '"tool":"claude-code"' >/dev/null

printf '%s\n' "P4 Claude Code team smoke test passed"
