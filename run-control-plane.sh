#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
# echo "[run-control-plane] building shared modules..."
# mvn -pl experiment-core -am install -DskipTests >/dev/null
cd control-plane-app
echo "[run-control-plane] starting control-plane-app"
mvn spring-boot:run "$@"
