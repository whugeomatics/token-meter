#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAR="$ROOT/agent-dashboard-collector/target/agent-dashboard-collector-0.1.0-SNAPSHOT.jar"
DIST_ROOT="$ROOT/dist"
PACKAGE="$DIST_ROOT/agent-dashboard-collector-P2.5-2026-05-01"

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  printf '%s\n' "Run: mvn -DskipTests package" >&2
  exit 1
fi
if jar tf "$JAR" | grep -E '(^static/|local/agent/dashboard/http/|local/agent/dashboard/report/|local/agent/dashboard/store/|local/agent/dashboard/app/AgentTokenDashboardApp|DashboardServer|AdminService|AdminAuth|DashboardPage|org/sqlite/|sqlite-jdbc)' >/dev/null; then
  printf '%s\n' "collector jar contains dashboard/database classes or static assets: $JAR" >&2
  exit 1
fi

mkdir -p "$PACKAGE"
rm -f "$PACKAGE/agent-dashboard-0.1.0-SNAPSHOT.jar"
cp "$JAR" "$PACKAGE/agent-dashboard-collector-0.1.0-SNAPSHOT.jar"
cp "$ROOT/scripts/P2.5-2026-05-01-run-collector.sh" "$PACKAGE/"
cp "$ROOT/scripts/P2.5-2026-05-01-run-collector-service.sh" "$PACKAGE/"
cp "$ROOT/scripts/P2.5-2026-05-01-install-collector-service.sh" "$PACKAGE/"
cp "$ROOT/scripts/P2.5-2026-05-01-uninstall-collector-service.sh" "$PACKAGE/"
chmod +x "$PACKAGE"/*.sh

printf '%s\n' "collector package: $PACKAGE"
printf '%s\n' "send this directory to teammate:"
printf '%s\n' "  agent-dashboard-collector-0.1.0-SNAPSHOT.jar"
printf '%s\n' "  P2.5-2026-05-01-run-collector.sh"
printf '%s\n' "  P2.5-2026-05-01-run-collector-service.sh"
printf '%s\n' "  P2.5-2026-05-01-install-collector-service.sh"
printf '%s\n' "  P2.5-2026-05-01-uninstall-collector-service.sh"
