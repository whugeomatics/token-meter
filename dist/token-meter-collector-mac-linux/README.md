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
export TOKEN_METER_SERVER_URL="http://admin-machine:18080"
export TOKEN_METER_DEVICE_TOKEN="your-device-token"
export TOKEN_METER_USER_ID="your-user-id"
export TOKEN_METER_DEVICE_ID="your-device-id"

sh run-collector.sh
```

If the dashboard runs on a different machine, do not use `127.0.0.1` unless the dashboard also runs on this teammate machine.

## Install Periodic Upload on macOS

```sh
export TOKEN_METER_SERVER_URL="http://admin-machine:18080"
export TOKEN_METER_DEVICE_TOKEN="your-device-token"
export TOKEN_METER_USER_ID="your-user-id"
export TOKEN_METER_DEVICE_ID="your-device-id"

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
