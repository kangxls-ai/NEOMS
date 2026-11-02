#!/usr/bin/env bash
set -euo pipefail
cd -- "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVA" >/dev/null || { echo 'Java 21+ required'; exit 1; }
version="$("$JAVA" -version 2>&1 | head -n 1)"
[[ "$version" =~ \"([0-9]+) ]] && (( BASH_REMATCH[1] >= 21 )) || { echo 'Java 21+ required'; exit 1; }
[[ -f neoms.jar && -d lib ]] || { echo 'neoms.jar / external lib missing'; exit 1; }
mkdir -p logs data
args=('-Dloader.config.location=file:./config/loader.properties' -jar neoms.jar)
if [[ "${1:-}" == '--background' ]]; then
  [[ ! -e neoms.pid ]] || { echo 'PID file exists. Check running instance first.'; exit 1; }
  nohup "$JAVA" "${args[@]}" >logs/console.log 2>&1 &
  echo "$!" >neoms.pid
  echo 'NEOMS starting. Open http://localhost:8080 ; see logs/neoms.log.'
else
  echo 'NEOMS: http://localhost:8080 ; Ctrl+C to stop.'
  exec "$JAVA" "${args[@]}"
fi
