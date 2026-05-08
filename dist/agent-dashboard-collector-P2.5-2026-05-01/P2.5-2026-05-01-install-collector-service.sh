#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
LABEL="${AGENT_DASHBOARD_COLLECTOR_LABEL:-local.agent.dashboard.collector}"
INTERVAL="${AGENT_DASHBOARD_COLLECTOR_INTERVAL_SECONDS:-300}"
CONFIG_DIR="$HOME/.agent-dashboard"
LOG_DIR="$CONFIG_DIR/logs"
CONFIG="$CONFIG_DIR/collector.env"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
if [ -f "$SCRIPT_DIR/P2.5-2026-05-01-run-collector-service.sh" ]; then
  RUNNER="$SCRIPT_DIR/P2.5-2026-05-01-run-collector-service.sh"
else
  RUNNER="$ROOT/scripts/P2.5-2026-05-01-run-collector-service.sh"
fi
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

require_env AGENT_DASHBOARD_SERVER_URL
require_env AGENT_DASHBOARD_DEVICE_TOKEN
require_env AGENT_DASHBOARD_USER_ID
require_env AGENT_DASHBOARD_DEVICE_ID

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  exit 1
fi
if [ ! -f "$RUNNER" ]; then
  printf '%s\n' "collector runner not found: $RUNNER" >&2
  exit 1
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  printf '%s\n' "java runtime not found. Set AGENT_DASHBOARD_JAVA or JAVA_HOME." >&2
  exit 1
fi

mkdir -p "$CONFIG_DIR" "$LOG_DIR" "$HOME/Library/LaunchAgents"
umask 077
{
  printf 'AGENT_DASHBOARD_JAVA=%s\n' "$(quote_value "$JAVA_BIN")"
  printf 'AGENT_DASHBOARD_JAR=%s\n' "$(quote_value "$JAR")"
  printf 'AGENT_DASHBOARD_SERVER_URL=%s\n' "$(quote_value "$AGENT_DASHBOARD_SERVER_URL")"
  printf 'AGENT_DASHBOARD_DEVICE_TOKEN=%s\n' "$(quote_value "$AGENT_DASHBOARD_DEVICE_TOKEN")"
  printf 'AGENT_DASHBOARD_USER_ID=%s\n' "$(quote_value "$AGENT_DASHBOARD_USER_ID")"
  printf 'AGENT_DASHBOARD_DEVICE_ID=%s\n' "$(quote_value "$AGENT_DASHBOARD_DEVICE_ID")"
  printf 'AGENT_DASHBOARD_DAYS=%s\n' "$(quote_value "${AGENT_DASHBOARD_DAYS:-30}")"
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
