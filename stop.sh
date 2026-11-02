#!/usr/bin/env bash
set -euo pipefail
cd -- "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
[[ -f neoms.pid ]] || { echo 'No background PID; use Ctrl+C for foreground mode.'; exit 0; }
neoms_pid="$(cat neoms.pid)"
[[ "$neoms_pid" =~ ^[0-9]+$ ]] || { echo 'Invalid PID'; exit 1; }
if kill -0 "$neoms_pid" 2>/dev/null; then
  ps -p "$neoms_pid" -o args= | grep -q 'neoms.jar' || { echo 'PID is not NEOMS; refusing to stop'; exit 1; }
  kill "$neoms_pid"
fi
rm -- neoms.pid
echo 'NEOMS stopping.'
