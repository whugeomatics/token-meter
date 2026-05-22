#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
if [ -f "$SCRIPT_DIR/token-meter-collector-0.1.0-SNAPSHOT.jar" ]; then
  DEFAULT_JAR="$SCRIPT_DIR/token-meter-collector-0.1.0-SNAPSHOT.jar"
else
  DEFAULT_JAR="$ROOT/token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar"
fi
JAR="${TOKEN_METER_JAR:-$DEFAULT_JAR}"
JAVA_BIN="${TOKEN_METER_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"
if [ -z "$JAVA_BIN" ]; then
  JAVA_BIN="$(command -v java || true)"
fi
SERVER_URL="${TOKEN_METER_SERVER_URL:?TOKEN_METER_SERVER_URL is required}"

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  exit 1
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  printf '%s\n' "java runtime not found. Set TOKEN_METER_JAVA or JAVA_HOME." >&2
  exit 1
fi

printf '%s\n' "Collector target: $SERVER_URL" >&2
if ! curl --noproxy '*' -fsS "${SERVER_URL%/}/health" >/dev/null 2>&1; then
  printf '%s\n' "Dashboard server is not reachable at ${SERVER_URL%/}/health" >&2
  printf '%s\n' "Start the dashboard server first, or set TOKEN_METER_SERVER_URL to the actual dashboard URL." >&2
  exit 1
fi

"$JAVA_BIN" -jar "$JAR" \
  --collect-team \
  --server-url="$SERVER_URL" \
  --device-token="${TOKEN_METER_DEVICE_TOKEN:?TOKEN_METER_DEVICE_TOKEN is required}" \
  --user-id="${TOKEN_METER_USER_ID:?TOKEN_METER_USER_ID is required}" \
  --device-id="${TOKEN_METER_DEVICE_ID:?TOKEN_METER_DEVICE_ID is required}" \
  --days="${TOKEN_METER_DAYS:-30}"
