#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
CONFIG="${TOKEN_METER_COLLECTOR_ENV:-$HOME/.token-meter/collector.env}"
if [ -f "$CONFIG" ]; then
  # shellcheck disable=SC1090
  . "$CONFIG"
fi
if [ -f "$SCRIPT_DIR/token-meter-collector.jar" ]; then
  DEFAULT_JAR="$SCRIPT_DIR/token-meter-collector.jar"
else
  DEFAULT_JAR="$ROOT/token-meter-collector/target/token-meter-collector.jar"
fi
JAR="${TOKEN_METER_JAR:-$DEFAULT_JAR}"
JAVA_BIN="${TOKEN_METER_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"
if [ -z "$JAVA_BIN" ]; then
  JAVA_BIN="$(command -v java || true)"
fi
TOKEN_METER_DAYS="${TOKEN_METER_DAYS:-30}"
export TOKEN_METER_DAYS

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  exit 1
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  printf '%s\n' "java runtime not found. Set TOKEN_METER_JAVA or JAVA_HOME." >&2
  exit 1
fi

if [ -n "${TOKEN_METER_SERVER_URL:-}" ]; then
  printf '%s\n' "Collector target: $TOKEN_METER_SERVER_URL" >&2
  if ! curl --noproxy '*' -fsS "${TOKEN_METER_SERVER_URL%/}/health" >/dev/null 2>&1; then
    printf '%s\n' "Dashboard server is not reachable at ${TOKEN_METER_SERVER_URL%/}/health" >&2
    printf '%s\n' "Start the dashboard server first, or set TOKEN_METER_SERVER_URL to the actual dashboard URL." >&2
    exit 1
  fi
fi

if [ -f "$CONFIG" ]; then
  "$JAVA_BIN" -jar "$JAR" \
    --collect-team \
    --collector-env-file="$CONFIG"
else
  "$JAVA_BIN" -jar "$JAR" \
    --collect-team
fi
