#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
# echo "[run-load-executor] building shared modules..."
# mvn -pl experiment-core -am install -DskipTests >/dev/null
cd load-executor
echo "[run-load-executor] starting load-executor"
mvn spring-boot:run "$@"
