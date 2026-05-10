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
