#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SOURCE_JAR_NAME="token-meter-collector-0.1.0-SNAPSHOT.jar"
DIST_JAR_NAME="token-meter-collector.jar"
JAR="$ROOT/token-meter-collector/target/$SOURCE_JAR_NAME"
DIST_ROOT="$ROOT/dist"
PACKAGE="$DIST_ROOT/token-meter-collector-windows"
LEGACY_PACKAGE_UNDATED="$DIST_ROOT/token-meter-collector"
LEGACY_PACKAGE="$DIST_ROOT/token-meter-collector-P2.5-2026-05-01"

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  printf '%s\n' "Run: mvn -DskipTests package" >&2
  exit 1
fi
if jar tf "$JAR" | grep -E '(^static/|local/token/meter/http/|local/token/meter/report/|local/token/meter/store/|local/token/meter/app/TokenMeterApp|DashboardServer|AdminService|AdminAuth|DashboardPage|org/sqlite/|sqlite-jdbc)' >/dev/null; then
  printf '%s\n' "collector jar contains dashboard/database classes or static assets: $JAR" >&2
  exit 1
fi

rm -rf "$LEGACY_PACKAGE" "$LEGACY_PACKAGE_UNDATED" "$PACKAGE"
mkdir -p "$PACKAGE"

cp "$JAR" "$PACKAGE/$DIST_JAR_NAME"
cat > "$PACKAGE/run-collector.cmd" <<'CMD'
@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "DEFAULT_JAR=%SCRIPT_DIR%token-meter-collector.jar"
if "%TOKEN_METER_JAR%"=="" set "TOKEN_METER_JAR=%DEFAULT_JAR%"
if "%TOKEN_METER_JAVA%"=="" (
  set "JAVA_BIN=java"
) else (
  set "JAVA_BIN=%TOKEN_METER_JAVA%"
)
if "%TOKEN_METER_DAYS%"=="" set "TOKEN_METER_DAYS=30"

if "%TOKEN_METER_SERVER_URL%"=="" (
  echo TOKEN_METER_SERVER_URL is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_DEVICE_TOKEN%"=="" (
  echo TOKEN_METER_DEVICE_TOKEN is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_USER_ID%"=="" (
  echo TOKEN_METER_USER_ID is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_DEVICE_ID%"=="" (
  echo TOKEN_METER_DEVICE_ID is required 1>&2
  exit /b 1
)
if not exist "%TOKEN_METER_JAR%" (
  echo collector jar not found: %TOKEN_METER_JAR% 1>&2
  exit /b 1
)

set "HEALTH_URL=%TOKEN_METER_SERVER_URL%"
if "%HEALTH_URL:~-1%"=="/" set "HEALTH_URL=%HEALTH_URL:~0,-1%"
echo Collector target: %TOKEN_METER_SERVER_URL% 1>&2
curl --noproxy "*" -fsS "%HEALTH_URL%/health" >nul 2>nul
if errorlevel 1 (
  echo Dashboard server is not reachable at %HEALTH_URL%/health 1>&2
  echo Start the dashboard server first, or set TOKEN_METER_SERVER_URL to the actual dashboard URL. 1>&2
  exit /b 1
)

"%JAVA_BIN%" -jar "%TOKEN_METER_JAR%" ^
  --collect-team ^
  --server-url="%TOKEN_METER_SERVER_URL%" ^
  --device-token="%TOKEN_METER_DEVICE_TOKEN%" ^
  --user-id="%TOKEN_METER_USER_ID%" ^
  --device-id="%TOKEN_METER_DEVICE_ID%" ^
  --days="%TOKEN_METER_DAYS%"
CMD
cat > "$PACKAGE/install-collector-task.cmd" <<'CMD'
@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "TASK_NAME=%TOKEN_METER_COLLECTOR_TASK_NAME%"
if "%TASK_NAME%"=="" set "TASK_NAME=TokenMeterCollector"
set "INTERVAL_MINUTES=%TOKEN_METER_COLLECTOR_INTERVAL_MINUTES%"
if "%INTERVAL_MINUTES%"=="" set "INTERVAL_MINUTES=5"
set "CONFIG_DIR=%USERPROFILE%\.token-meter"
set "CONFIG=%CONFIG_DIR%\collector.env.cmd"
set "RUNNER=%CONFIG_DIR%\run-collector-task.cmd"
set "PACKAGE_RUNNER=%SCRIPT_DIR%run-collector.cmd"
set "PACKAGE_JAR=%SCRIPT_DIR%token-meter-collector.jar"

if "%TOKEN_METER_SERVER_URL%"=="" (
  echo TOKEN_METER_SERVER_URL is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_DEVICE_TOKEN%"=="" (
  echo TOKEN_METER_DEVICE_TOKEN is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_USER_ID%"=="" (
  echo TOKEN_METER_USER_ID is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_DEVICE_ID%"=="" (
  echo TOKEN_METER_DEVICE_ID is required 1>&2
  exit /b 1
)
if not exist "%PACKAGE_RUNNER%" (
  echo collector runner not found: %PACKAGE_RUNNER% 1>&2
  exit /b 1
)
if not exist "%PACKAGE_JAR%" (
  echo collector jar not found: %PACKAGE_JAR% 1>&2
  exit /b 1
)

if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
if errorlevel 1 exit /b 1

(
  echo @echo off
  echo set "TOKEN_METER_JAR=%PACKAGE_JAR%"
  echo set "TOKEN_METER_SERVER_URL=%TOKEN_METER_SERVER_URL%"
  echo set "TOKEN_METER_DEVICE_TOKEN=%TOKEN_METER_DEVICE_TOKEN%"
  echo set "TOKEN_METER_USER_ID=%TOKEN_METER_USER_ID%"
  echo set "TOKEN_METER_DEVICE_ID=%TOKEN_METER_DEVICE_ID%"
  echo set "TOKEN_METER_DAYS=%TOKEN_METER_DAYS%"
  echo set "TOKEN_METER_JAVA=%TOKEN_METER_JAVA%"
) > "%CONFIG%"

(
  echo @echo off
  echo call "%CONFIG%"
  echo call "%PACKAGE_RUNNER%"
) > "%RUNNER%"

schtasks /Create /TN "%TASK_NAME%" /SC MINUTE /MO "%INTERVAL_MINUTES%" /TR "\"%RUNNER%\"" /F
if errorlevel 1 exit /b 1

echo collector task installed: %TASK_NAME%
echo config: %CONFIG%
echo runner: %RUNNER%
CMD
cat > "$PACKAGE/uninstall-collector-task.cmd" <<'CMD'
@echo off
setlocal EnableExtensions

set "TASK_NAME=%TOKEN_METER_COLLECTOR_TASK_NAME%"
if "%TASK_NAME%"=="" set "TASK_NAME=TokenMeterCollector"
set "CONFIG=%USERPROFILE%\.token-meter\collector.env.cmd"

schtasks /Delete /TN "%TASK_NAME%" /F >nul 2>nul

echo collector task uninstalled: %TASK_NAME%
echo collector config remains at: %CONFIG%
CMD
cat > "$PACKAGE/README.md" <<'README'
# Token Meter Collector Teammate Guide

This Windows package uploads local Codex usage summaries to the team token-meter dashboard.

## Files

- `token-meter-collector.jar`: collector program.
- `run-collector.cmd`: run one upload manually.
- `install-collector-task.cmd`: install a Windows Task Scheduler task for periodic uploads.
- `uninstall-collector-task.cmd`: remove the scheduled task.

## Required Values

Ask the admin for these values:

- `TOKEN_METER_SERVER_URL`: dashboard server URL reachable from this machine.
- `TOKEN_METER_DEVICE_TOKEN`: teammate device token.
- `TOKEN_METER_USER_ID`: teammate user id.
- `TOKEN_METER_DEVICE_ID`: this device id.

Do not share `TOKEN_METER_DEVICE_TOKEN` with others.

## Run Once

In Command Prompt:

```bat
set TOKEN_METER_SERVER_URL=http://admin-machine:18080
set TOKEN_METER_DEVICE_TOKEN=your-device-token
set TOKEN_METER_USER_ID=your-user-id
set TOKEN_METER_DEVICE_ID=your-device-id

run-collector.cmd
```

If the dashboard runs on a different machine, do not use `127.0.0.1` unless the dashboard also runs on this teammate machine.

Optional settings:

```bat
set TOKEN_METER_DAYS=30
set TOKEN_METER_JAVA=C:\Program Files\Java\jdk-21\bin\java.exe
```

The script requires Java and `curl` to be available on `PATH`, unless `TOKEN_METER_JAVA` points to Java explicitly.

## Install Periodic Upload on Windows

In Command Prompt:

```bat
set TOKEN_METER_SERVER_URL=http://admin-machine:18080
set TOKEN_METER_DEVICE_TOKEN=your-device-token
set TOKEN_METER_USER_ID=your-user-id
set TOKEN_METER_DEVICE_ID=your-device-id

install-collector-task.cmd
```

Optional settings:

```bat
set TOKEN_METER_COLLECTOR_INTERVAL_MINUTES=5
set TOKEN_METER_DAYS=30
set TOKEN_METER_JAVA=C:\Program Files\Java\jdk-21\bin\java.exe
set TOKEN_METER_COLLECTOR_TASK_NAME=TokenMeterCollector
```

Installed files:

```text
%USERPROFILE%\.token-meter\collector.env.cmd
%USERPROFILE%\.token-meter\run-collector-task.cmd
```

`collector.env.cmd` contains the device token. Do not share this file.

## Check or Trigger the Task

```bat
schtasks /Query /TN TokenMeterCollector
schtasks /Run /TN TokenMeterCollector
```

## Uninstall

```bat
uninstall-collector-task.cmd
```

The uninstall script keeps `%USERPROFILE%\.token-meter\collector.env.cmd`. Delete it manually if the local token config should be removed.
README

printf '%s\n' "collector package: $PACKAGE"
printf '%s\n' "Windows package files:"
printf '%s\n' "  README.md"
printf '%s\n' "  token-meter-collector.jar"
printf '%s\n' "  run-collector.cmd"
printf '%s\n' "  install-collector-task.cmd"
printf '%s\n' "  uninstall-collector-task.cmd"
