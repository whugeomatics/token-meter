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

schtasks /Create /TN "%TASK_NAME%" /SC MINUTE /MO "%INTERVAL_MINUTES%" /TR "cmd /c \"%RUNNER%\"" /F
if errorlevel 1 exit /b 1

echo collector task installed: %TASK_NAME%
echo config: %CONFIG%
echo runner: %RUNNER%
