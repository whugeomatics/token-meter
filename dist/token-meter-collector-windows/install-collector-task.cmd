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
set "RUNNER=%CONFIG_DIR%\run-collector-task.cmd"
set "INSTALL_LOG=%LOG_DIR%\install.log"
set "PACKAGE_RUNNER=%SCRIPT_DIR%run-collector.cmd"
set "PACKAGE_JAR=%SCRIPT_DIR%token-meter-collector.jar"

if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
if errorlevel 1 exit /b 1
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if errorlevel 1 exit /b 1
echo %DATE% %TIME% install started > "%INSTALL_LOG%"

if "%TOKEN_METER_SERVER_URL%"=="" (
  echo TOKEN_METER_SERVER_URL is required >> "%INSTALL_LOG%"
  echo TOKEN_METER_SERVER_URL is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_DEVICE_TOKEN%"=="" (
  echo TOKEN_METER_DEVICE_TOKEN is required >> "%INSTALL_LOG%"
  echo TOKEN_METER_DEVICE_TOKEN is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_USER_ID%"=="" (
  echo TOKEN_METER_USER_ID is required >> "%INSTALL_LOG%"
  echo TOKEN_METER_USER_ID is required 1>&2
  exit /b 1
)
if "%TOKEN_METER_DEVICE_ID%"=="" (
  echo TOKEN_METER_DEVICE_ID is required >> "%INSTALL_LOG%"
  echo TOKEN_METER_DEVICE_ID is required 1>&2
  exit /b 1
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
  echo echo %%DATE%% %%TIME%% collector task started ^>^> "%LOG_DIR%\collector.out.log"
  echo call "%CONFIG%"
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
