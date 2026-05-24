#!/usr/bin/env sh
set -eu

CONFIG="${TOKEN_METER_COLLECTOR_ENV:-$HOME/.token-meter/collector.env}"
if [ ! -f "$CONFIG" ]; then
  printf '%s\n' "collector config not found: $CONFIG" >&2
  exit 1
fi

# shellcheck disable=SC1090
. "$CONFIG"

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
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

"$JAVA_BIN" -jar "$JAR" \
  --collect-team \
  --collector-env-file="$CONFIG"
