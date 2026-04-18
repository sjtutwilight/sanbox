#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
# echo "[run-load-executor] building shared modules..."
# mvn -pl experiment-core -am install -DskipTests >/dev/null
cd load-executor
echo "[run-load-executor] starting load-executor"
JVM_ARGS=${JVM_ARGS:--Xms256m -Xmx512m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200}
mvn spring-boot:run -Dspring-boot.run.jvmArguments="$JVM_ARGS" "$@"
