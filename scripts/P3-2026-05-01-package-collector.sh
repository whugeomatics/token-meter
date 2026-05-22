#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TARGET="${1:-all}"

usage() {
  printf '%s\n' "Usage: sh scripts/P3-2026-05-01-package-collector.sh [unix|windows|all]" >&2
}

case "$TARGET" in
  unix)
    sh "$ROOT/scripts/P3-2026-05-01-package-collector-unix.sh"
    ;;
  windows)
    sh "$ROOT/scripts/P3-2026-05-01-package-collector-windows.sh"
    ;;
  all)
    sh "$ROOT/scripts/P3-2026-05-01-package-collector-unix.sh"
    sh "$ROOT/scripts/P3-2026-05-01-package-collector-windows.sh"
    ;;
  *)
    usage
    exit 2
    ;;
esac
