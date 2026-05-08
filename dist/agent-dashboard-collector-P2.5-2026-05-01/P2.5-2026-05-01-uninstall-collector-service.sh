#!/usr/bin/env sh
set -eu

LABEL="${AGENT_DASHBOARD_COLLECTOR_LABEL:-local.agent.dashboard.collector}"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

launchctl bootout "gui/$(id -u)" "$PLIST" >/dev/null 2>&1 || true
rm -f "$PLIST"

printf '%s\n' "collector service uninstalled: $LABEL"
printf '%s\n' "collector config remains at: $HOME/.agent-dashboard/collector.env"
