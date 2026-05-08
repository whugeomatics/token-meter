#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAR="$ROOT/token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar"
DIST_ROOT="$ROOT/dist"
PACKAGE="$DIST_ROOT/token-meter-collector-P2.5-2026-05-01"

if [ ! -f "$JAR" ]; then
  printf '%s\n' "collector jar not found: $JAR" >&2
  printf '%s\n' "Run: mvn -DskipTests package" >&2
  exit 1
fi
if jar tf "$JAR" | grep -E '(^static/|local/token/meter/http/|local/token/meter/report/|local/token/meter/store/|local/token/meter/app/TokenMeterApp|DashboardServer|AdminService|AdminAuth|DashboardPage|org/sqlite/|sqlite-jdbc)' >/dev/null; then
  printf '%s\n' "collector jar contains dashboard/database classes or static assets: $JAR" >&2
  exit 1
fi

mkdir -p "$PACKAGE"
rm -f "$PACKAGE/token-meter-0.1.0-SNAPSHOT.jar"
rm -f "$PACKAGE/token-meter-app-0.1.0-SNAPSHOT.jar"
cp "$JAR" "$PACKAGE/token-meter-collector-0.1.0-SNAPSHOT.jar"
cp "$ROOT/scripts/P2.5-2026-05-01-run-collector.sh" "$PACKAGE/"
cp "$ROOT/scripts/P2.5-2026-05-01-run-collector-service.sh" "$PACKAGE/"
cp "$ROOT/scripts/P2.5-2026-05-01-install-collector-service.sh" "$PACKAGE/"
cp "$ROOT/scripts/P2.5-2026-05-01-uninstall-collector-service.sh" "$PACKAGE/"
chmod +x "$PACKAGE"/*.sh

printf '%s\n' "collector package: $PACKAGE"
printf '%s\n' "send this directory to teammate:"
printf '%s\n' "  token-meter-collector-0.1.0-SNAPSHOT.jar"
printf '%s\n' "  P2.5-2026-05-01-run-collector.sh"
printf '%s\n' "  P2.5-2026-05-01-run-collector-service.sh"
printf '%s\n' "  P2.5-2026-05-01-install-collector-service.sh"
printf '%s\n' "  P2.5-2026-05-01-uninstall-collector-service.sh"
