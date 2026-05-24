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
