@echo off
setlocal EnableExtensions

set "TASK_NAME=%TOKEN_METER_COLLECTOR_TASK_NAME%"
if "%TASK_NAME%"=="" set "TASK_NAME=TokenMeterCollector"
set "CONFIG=%USERPROFILE%\.token-meter\collector.env.cmd"

schtasks /Delete /TN "%TASK_NAME%" /F >nul 2>nul

echo collector task uninstalled: %TASK_NAME%
echo collector config remains at: %CONFIG%
