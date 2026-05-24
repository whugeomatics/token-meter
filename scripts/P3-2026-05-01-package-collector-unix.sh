#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SOURCE_JAR_NAME="token-meter-collector-0.1.0-SNAPSHOT.jar"
DIST_JAR_NAME="token-meter-collector.jar"
JAR="$ROOT/token-meter-collector/target/$SOURCE_JAR_NAME"
DIST_ROOT="$ROOT/dist"
PACKAGE="$DIST_ROOT/token-meter-collector-mac-linux"
UNDATED_PACKAGE="$DIST_ROOT/token-meter-collector"
DATED_PACKAGE="$DIST_ROOT/token-meter-collector-P3-2026-05-01"

cleanup_package_dir() {
  dir="$1"
  shift
  if [ ! -d "$dir" ]; then
    return
  fi
  for name in "$@"; do
    path="$dir/$name"
    if [ -f "$path" ]; then
      rm "$path"
    fi
  done
  if ! rmdir "$dir" 2>/dev/null; then
    printf '%s\n' "package directory is not empty: $dir" >&2
    printf '%s\n' "Remove unexpected files manually, then rerun this script." >&2
    exit 1
  fi
}

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  printf '%s\n' "Run: mvn -DskipTests package" >&2
  exit 1
fi
if jar tf "$JAR" | grep -E '(^static/|local/token/meter/http/|local/token/meter/report/|local/token/meter/store/|local/token/meter/app/TokenMeterApp|DashboardServer|AdminService|AdminAuth|DashboardPage|org/sqlite/|sqlite-jdbc)' >/dev/null; then
  printf '%s\n' "collector jar contains dashboard/database classes or static assets: $JAR" >&2
  exit 1
fi

cleanup_package_dir "$DATED_PACKAGE" \
  README.md token-meter-collector.jar run-collector.sh run-collector-service.sh \
  install-collector-service.sh uninstall-collector-service.sh run-collector.cmd \
  install-collector-task.cmd uninstall-collector-task.cmd
cleanup_package_dir "$UNDATED_PACKAGE" \
  README.md token-meter-collector.jar run-collector.sh run-collector-service.sh \
  install-collector-service.sh uninstall-collector-service.sh run-collector.cmd \
  install-collector-task.cmd uninstall-collector-task.cmd
cleanup_package_dir "$PACKAGE" \
  README.md token-meter-collector.jar run-collector.sh run-collector-service.sh \
  install-collector-service.sh uninstall-collector-service.sh
mkdir -p "$PACKAGE"

cp "$JAR" "$PACKAGE/$DIST_JAR_NAME"
cp "$ROOT/scripts/P3-2026-05-01-run-collector.sh" "$PACKAGE/run-collector.sh"
cp "$ROOT/scripts/P3-2026-05-01-run-collector-service.sh" "$PACKAGE/run-collector-service.sh"
cp "$ROOT/scripts/P3-2026-05-01-install-collector-service.sh" "$PACKAGE/install-collector-service.sh"
cp "$ROOT/scripts/P3-2026-05-01-uninstall-collector-service.sh" "$PACKAGE/uninstall-collector-service.sh"
sed -i '' "s/$SOURCE_JAR_NAME/$DIST_JAR_NAME/g" "$PACKAGE/run-collector.sh" "$PACKAGE/run-collector-service.sh" "$PACKAGE/install-collector-service.sh"
chmod +x "$PACKAGE"/*.sh
cat > "$PACKAGE/README.md" <<'README'
# Token Meter Collector Teammate Guide

This macOS/Linux package uploads local Codex usage summaries to the team token-meter dashboard.

## Files

- `token-meter-collector.jar`: collector program.
- `run-collector.sh`: run one upload manually.
- `install-collector-service.sh`: install a macOS LaunchAgent for periodic uploads.
- `run-collector-service.sh`: LaunchAgent runner.
- `uninstall-collector-service.sh`: remove the LaunchAgent.

## Required Values

Ask the admin for these values:

- `TOKEN_METER_SERVER_URL`: dashboard server URL reachable from this machine.
- `TOKEN_METER_DEVICE_TOKEN`: teammate device token.
- `TOKEN_METER_USER_ID`: teammate user id.
- `TOKEN_METER_DEVICE_ID`: this device id.

Do not share `TOKEN_METER_DEVICE_TOKEN` with others.

## Run Once

```sh
mkdir -p ~/.token-meter
# Save the teammate .env from admin.html to ~/.token-meter/collector.env
sh run-collector.sh
```

If the dashboard runs on a different machine, do not use `127.0.0.1` unless the dashboard also runs on this teammate machine.

## Install Periodic Upload on macOS

```sh
mkdir -p ~/.token-meter
# Save the teammate .env from admin.html to ~/.token-meter/collector.env
sh install-collector-service.sh
```

Optional settings:

```sh
export TOKEN_METER_COLLECTOR_INTERVAL_SECONDS=300
export TOKEN_METER_DAYS=30
export TOKEN_METER_JAVA="/absolute/path/to/java"
```

Installed files:

```text
~/Library/LaunchAgents/local.token.meter.collector.plist
~/.token-meter/collector.env
~/.token-meter/logs/collector.out.log
~/.token-meter/logs/collector.err.log
```

`~/.token-meter/collector.env` contains the device token and is created with `600` permissions.

## Check or Trigger the Service

```sh
launchctl print "gui/$(id -u)/local.token.meter.collector"
launchctl kickstart -k "gui/$(id -u)/local.token.meter.collector"
```

If `kickstart` is denied by macOS, the service can still run on its next interval.

## Uninstall

```sh
sh uninstall-collector-service.sh
```

The uninstall script keeps `~/.token-meter/collector.env`. To remove the local token config:

```sh
rm ~/.token-meter/collector.env
```
README

printf '%s\n' "collector package: $PACKAGE"
printf '%s\n' "macOS/Linux package files:"
printf '%s\n' "  README.md"
printf '%s\n' "  token-meter-collector.jar"
printf '%s\n' "  run-collector.sh"
printf '%s\n' "  run-collector-service.sh"
printf '%s\n' "  install-collector-service.sh"
printf '%s\n' "  uninstall-collector-service.sh"
