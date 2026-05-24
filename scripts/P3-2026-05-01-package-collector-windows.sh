#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SOURCE_JAR_NAME="token-meter-collector-0.1.0-SNAPSHOT.jar"
DIST_JAR_NAME="token-meter-collector.jar"
JAR="$ROOT/token-meter-collector/target/$SOURCE_JAR_NAME"
DIST_ROOT="$ROOT/dist"
PACKAGE="$DIST_ROOT/token-meter-collector-windows"
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
  README.md token-meter-collector.jar run-collector.cmd install-collector-task.cmd \
  uninstall-collector-task.cmd
mkdir -p "$PACKAGE"

cp "$JAR" "$PACKAGE/$DIST_JAR_NAME"
cat > "$PACKAGE/run-collector.cmd" <<'CMD'
@echo off
setlocal

set "CONFIG=%TOKEN_METER_COLLECTOR_ENV%"
if "%CONFIG%"=="" set "CONFIG=%USERPROFILE%\.token-meter\collector.env"
if not exist "%CONFIG%" if exist "%USERPROFILE%\.token-meter\collector.env.cmd" set "CONFIG=%USERPROFILE%\.token-meter\collector.env.cmd"
if exist "%CONFIG%" (
  if /I "%CONFIG:~-4%"==".cmd" call "%CONFIG%"
)

set "SCRIPT_DIR=%~dp0"
set "DEFAULT_JAR=%SCRIPT_DIR%token-meter-collector.jar"
if "%TOKEN_METER_JAR%"=="" set "TOKEN_METER_JAR=%DEFAULT_JAR%"
if "%TOKEN_METER_JAVA%"=="" (
  set "JAVA_BIN=java"
) else (
  set "JAVA_BIN=%TOKEN_METER_JAVA%"
)
if "%TOKEN_METER_DAYS%"=="" set "TOKEN_METER_DAYS=30"

if not exist "%TOKEN_METER_JAR%" (
  echo collector jar not found: %TOKEN_METER_JAR% 1>&2
  exit /b 1
)

if not "%TOKEN_METER_SERVER_URL%"=="" (
  set "HEALTH_URL=%TOKEN_METER_SERVER_URL%"
  if "%HEALTH_URL:~-1%"=="/" set "HEALTH_URL=%HEALTH_URL:~0,-1%"
  echo Collector target: %TOKEN_METER_SERVER_URL% 1>&2
  curl --noproxy "*" -fsS "%HEALTH_URL%/health" >nul 2>nul
  if errorlevel 1 (
    echo Dashboard server is not reachable at %HEALTH_URL%/health 1>&2
    echo Start the dashboard server first, or set TOKEN_METER_SERVER_URL to the actual dashboard URL. 1>&2
    exit /b 1
  )
)

"%JAVA_BIN%" -jar "%TOKEN_METER_JAR%" ^
  --collect-team ^
  --collector-env-file="%CONFIG%"
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
set "LOG_DIR=%CONFIG_DIR%\logs"
set "CONFIG=%CONFIG_DIR%\collector.env.cmd"
set "PLAIN_CONFIG=%CONFIG_DIR%\collector.env"
set "RUNNER=%CONFIG_DIR%\run-collector-task.cmd"
set "INSTALL_LOG=%LOG_DIR%\install.log"
set "PACKAGE_RUNNER=%SCRIPT_DIR%run-collector.cmd"
set "PACKAGE_JAR=%SCRIPT_DIR%token-meter-collector.jar"

if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
if errorlevel 1 exit /b 1
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if errorlevel 1 exit /b 1
echo %DATE% %TIME% install started > "%INSTALL_LOG%"

set "HAS_ENV_VALUES=1"
if "%TOKEN_METER_SERVER_URL%"=="" set "HAS_ENV_VALUES=0"
if "%TOKEN_METER_DEVICE_TOKEN%"=="" set "HAS_ENV_VALUES=0"
if "%TOKEN_METER_USER_ID%"=="" set "HAS_ENV_VALUES=0"
if "%TOKEN_METER_DEVICE_ID%"=="" set "HAS_ENV_VALUES=0"
if "%HAS_ENV_VALUES%"=="0" (
  if not exist "%PLAIN_CONFIG%" if not exist "%CONFIG%" (
    echo collector env file not found: %PLAIN_CONFIG% >> "%INSTALL_LOG%"
    echo Save the teammate .env from admin.html to %PLAIN_CONFIG%, or set TOKEN_METER_SERVER_URL, TOKEN_METER_DEVICE_TOKEN, TOKEN_METER_USER_ID, and TOKEN_METER_DEVICE_ID. 1>&2
    exit /b 1
  )
)
if not exist "%PACKAGE_RUNNER%" (
  echo collector runner not found: %PACKAGE_RUNNER% >> "%INSTALL_LOG%"
  echo collector runner not found: %PACKAGE_RUNNER% 1>&2
  exit /b 1
)
if not exist "%PACKAGE_JAR%" (
  echo collector jar not found: %PACKAGE_JAR% >> "%INSTALL_LOG%"
  echo collector jar not found: %PACKAGE_JAR% 1>&2
  exit /b 1
)

if "%HAS_ENV_VALUES%"=="1" (
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
)

(
  echo @echo off
  echo echo %%DATE%% %%TIME%% collector task started ^>^> "%LOG_DIR%\collector.out.log"
  echo if exist "%CONFIG%" call "%CONFIG%"
  echo call "%PACKAGE_RUNNER%" ^>^> "%LOG_DIR%\collector.out.log" 2^>^> "%LOG_DIR%\collector.err.log"
  echo set "EXIT_CODE=%%ERRORLEVEL%%"
  echo echo %%DATE%% %%TIME%% collector task exited with %%EXIT_CODE%% ^>^> "%LOG_DIR%\collector.out.log"
  echo exit /b %%EXIT_CODE%%
) > "%RUNNER%"

schtasks /Create /TN "%TASK_NAME%" /SC MINUTE /MO "%INTERVAL_MINUTES%" /TR "%RUNNER%" /F
if errorlevel 1 (
  echo schtasks create failed >> "%INSTALL_LOG%"
  exit /b 1
)

echo %DATE% %TIME% running collector once >> "%INSTALL_LOG%"
call "%RUNNER%"
set "FIRST_RUN_EXIT_CODE=%ERRORLEVEL%"
echo %DATE% %TIME% collector first run exited with %FIRST_RUN_EXIT_CODE% >> "%INSTALL_LOG%"

schtasks /Run /TN "%TASK_NAME%"
if errorlevel 1 (
  echo schtasks run failed >> "%INSTALL_LOG%"
  echo warning: scheduled task was created but immediate task trigger failed 1>&2
)

echo collector task installed: %TASK_NAME%
echo config: %CONFIG%
echo runner: %RUNNER%
echo logs: %LOG_DIR%
echo first run triggered
if not "%FIRST_RUN_EXIT_CODE%"=="0" echo warning: first collector run failed, check %LOG_DIR%\collector.err.log
CMD
cat > "$PACKAGE/uninstall-collector-task.cmd" <<'CMD'
@echo off
setlocal EnableExtensions

set "TASK_NAME=%TOKEN_METER_COLLECTOR_TASK_NAME%"
if "%TASK_NAME%"=="" set "TASK_NAME=TokenMeterCollector"
set "CONFIG=%USERPROFILE%\.token-meter\collector.env.cmd"
set "PLAIN_CONFIG=%USERPROFILE%\.token-meter\collector.env"

schtasks /Delete /TN "%TASK_NAME%" /F >nul 2>nul

echo collector task uninstalled: %TASK_NAME%
echo collector config remains at: %CONFIG%
echo plain collector env may also remain at: %PLAIN_CONFIG%
CMD
cat > "$PACKAGE/README.md" <<'README'
# Token Meter Collector Teammate Guide

This Windows package uploads local Codex and Claude Code usage summaries to the team token-meter dashboard.

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

The collector reads configuration in this order:

```text
CLI args > %USERPROFILE%\.token-meter\collector.env > system environment variables
```

## Run Once

In Command Prompt:

```bat
mkdir "%USERPROFILE%\.token-meter"
rem Save the teammate .env from admin.html to %USERPROFILE%\.token-meter\collector.env
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
mkdir "%USERPROFILE%\.token-meter"
rem Save the teammate .env from admin.html to %USERPROFILE%\.token-meter\collector.env
install-collector-task.cmd
```

The installer triggers the first upload immediately after the scheduled task is created.

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
%USERPROFILE%\.token-meter\collector.env
%USERPROFILE%\.token-meter\run-collector-task.cmd
%USERPROFILE%\.token-meter\logs\install.log
%USERPROFILE%\.token-meter\logs\collector.out.log
%USERPROFILE%\.token-meter\logs\collector.err.log
```

`collector.env` or the legacy `collector.env.cmd` contains the device token. Do not share this file.

## Check or Trigger the Task

```bat
schtasks /Query /TN TokenMeterCollector
schtasks /Run /TN TokenMeterCollector
```

Task output is written to:

```text
%USERPROFILE%\.token-meter\logs\collector.out.log
%USERPROFILE%\.token-meter\logs\collector.err.log
```

## Uninstall

```bat
uninstall-collector-task.cmd
```

The uninstall script keeps `%USERPROFILE%\.token-meter\collector.env` and `%USERPROFILE%\.token-meter\collector.env.cmd`. Delete local token config files manually if they should be removed.
README

printf '%s\n' "collector package: $PACKAGE"
printf '%s\n' "Windows package files:"
printf '%s\n' "  README.md"
printf '%s\n' "  token-meter-collector.jar"
printf '%s\n' "  run-collector.cmd"
printf '%s\n' "  install-collector-task.cmd"
printf '%s\n' "  uninstall-collector-task.cmd"
