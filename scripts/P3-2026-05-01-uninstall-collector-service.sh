#!/usr/bin/env sh
set -eu

LABEL="${TOKEN_METER_COLLECTOR_LABEL:-local.token.meter.collector}"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

launchctl bootout "gui/$(id -u)" "$PLIST" >/dev/null 2>&1 || true
rm -f "$PLIST"

printf '%s\n' "collector service uninstalled: $LABEL"
printf '%s\n' "collector config remains at: $HOME/.token-meter/collector.env"
