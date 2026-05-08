#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
if [ -f "$SCRIPT_DIR/agent-dashboard-collector-0.1.0-SNAPSHOT.jar" ]; then
  DEFAULT_JAR="$SCRIPT_DIR/agent-dashboard-collector-0.1.0-SNAPSHOT.jar"
else
  DEFAULT_JAR="$ROOT/agent-dashboard-collector/target/agent-dashboard-collector-0.1.0-SNAPSHOT.jar"
fi
JAR="${AGENT_DASHBOARD_JAR:-$DEFAULT_JAR}"
JAVA_BIN="${AGENT_DASHBOARD_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"
if [ -z "$JAVA_BIN" ]; then
  JAVA_BIN="$(command -v java || true)"
fi
SERVER_URL="${AGENT_DASHBOARD_SERVER_URL:?AGENT_DASHBOARD_SERVER_URL is required}"

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  exit 1
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  printf '%s\n' "java runtime not found. Set AGENT_DASHBOARD_JAVA or JAVA_HOME." >&2
  exit 1
fi

printf '%s\n' "Collector target: $SERVER_URL" >&2
if ! curl --noproxy '*' -fsS "${SERVER_URL%/}/health" >/dev/null 2>&1; then
  printf '%s\n' "Dashboard server is not reachable at ${SERVER_URL%/}/health" >&2
  printf '%s\n' "Start the dashboard server first, or set AGENT_DASHBOARD_SERVER_URL to the actual dashboard URL." >&2
  exit 1
fi

"$JAVA_BIN" -jar "$JAR" \
  --collect-team \
  --server-url="$SERVER_URL" \
  --device-token="${AGENT_DASHBOARD_DEVICE_TOKEN:?AGENT_DASHBOARD_DEVICE_TOKEN is required}" \
  --user-id="${AGENT_DASHBOARD_USER_ID:?AGENT_DASHBOARD_USER_ID is required}" \
  --device-id="${AGENT_DASHBOARD_DEVICE_ID:?AGENT_DASHBOARD_DEVICE_ID is required}" \
  --days="${AGENT_DASHBOARD_DAYS:-30}"
