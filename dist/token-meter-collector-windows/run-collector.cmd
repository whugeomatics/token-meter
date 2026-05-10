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
