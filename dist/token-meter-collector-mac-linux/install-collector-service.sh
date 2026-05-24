#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
LABEL="${TOKEN_METER_COLLECTOR_LABEL:-local.token.meter.collector}"
INTERVAL="${TOKEN_METER_COLLECTOR_INTERVAL_SECONDS:-300}"
CONFIG_DIR="$HOME/.token-meter"
LOG_DIR="$CONFIG_DIR/logs"
CONFIG="$CONFIG_DIR/collector.env"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
RUNNER=""
for candidate in "$SCRIPT_DIR/run-collector-service.sh" "$ROOT"/scripts/*-run-collector-service.sh; do
  if [ -f "$candidate" ]; then
    RUNNER="$candidate"
    break
  fi
done
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

require_env() {
  name="$1"
  eval "value=\${$name:-}"
  if [ -z "$value" ]; then
    printf '%s\n' "$name is required" >&2
    exit 1
  fi
}

quote_value() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

if [ -f "$CONFIG" ]; then
  # shellcheck disable=SC1090
  . "$CONFIG"
fi

require_env TOKEN_METER_SERVER_URL
require_env TOKEN_METER_DEVICE_TOKEN
require_env TOKEN_METER_USER_ID
require_env TOKEN_METER_DEVICE_ID

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  exit 1
fi
if [ ! -f "$RUNNER" ]; then
  printf '%s\n' "collector runner not found: $RUNNER" >&2
  exit 1
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  printf '%s\n' "java runtime not found. Set TOKEN_METER_JAVA or JAVA_HOME." >&2
  exit 1
fi

mkdir -p "$CONFIG_DIR" "$LOG_DIR" "$HOME/Library/LaunchAgents"
umask 077
{
  printf 'TOKEN_METER_JAVA=%s\n' "$(quote_value "$JAVA_BIN")"
  printf 'TOKEN_METER_JAR=%s\n' "$(quote_value "$JAR")"
  printf 'TOKEN_METER_SERVER_URL=%s\n' "$(quote_value "$TOKEN_METER_SERVER_URL")"
  printf 'TOKEN_METER_DEVICE_TOKEN=%s\n' "$(quote_value "$TOKEN_METER_DEVICE_TOKEN")"
  printf 'TOKEN_METER_USER_ID=%s\n' "$(quote_value "$TOKEN_METER_USER_ID")"
  printf 'TOKEN_METER_DEVICE_ID=%s\n' "$(quote_value "$TOKEN_METER_DEVICE_ID")"
  printf 'TOKEN_METER_DAYS=%s\n' "$(quote_value "${TOKEN_METER_DAYS:-30}")"
} > "$CONFIG"
chmod 600 "$CONFIG"

cat > "$PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/sh</string>
    <string>$RUNNER</string>
  </array>
  <key>StartInterval</key>
  <integer>$INTERVAL</integer>
  <key>RunAtLoad</key>
  <true/>
  <key>StandardOutPath</key>
  <string>$LOG_DIR/collector.out.log</string>
  <key>StandardErrorPath</key>
  <string>$LOG_DIR/collector.err.log</string>
</dict>
</plist>
PLIST

launchctl bootout "gui/$(id -u)" "$PLIST" >/dev/null 2>&1 || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
if ! launchctl kickstart -k "gui/$(id -u)/$LABEL"; then
  printf '%s\n' "collector service installed, but launchctl kickstart was denied." >&2
  printf '%s\n' "macOS will run it on the next StartInterval, or you can run this manually:" >&2
  printf '%s\n' "launchctl kickstart -k gui/$(id -u)/$LABEL" >&2
fi

printf '%s\n' "collector service installed: $LABEL"
printf '%s\n' "config: $CONFIG"
printf '%s\n' "logs: $LOG_DIR"
printf '%s\n' "jar: $JAR"
printf '%s\n' "java: $JAVA_BIN"
