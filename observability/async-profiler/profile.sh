#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <pid> [extra-profiler-args]" >&2
  exit 1
fi

PID="$1"
shift || true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_BIN="$(ls -d "${SCRIPT_DIR}"/bin/async-profiler-* 2>/dev/null | sort | tail -n1)"
PROFILER_DIR="${ASYNC_PROFILER_DIR:-$DEFAULT_BIN}"

# 新版本使用 asprof，旧版本使用 profiler.sh
if [[ -x "${PROFILER_DIR}/bin/asprof" ]]; then
  PROFILER="${PROFILER_DIR}/bin/asprof"
elif [[ -x "${PROFILER_DIR}/profiler.sh" ]]; then
  PROFILER="${PROFILER_DIR}/profiler.sh"
else
  echo "asprof or profiler.sh not found. Run ${SCRIPT_DIR}/download.sh or set ASYNC_PROFILER_DIR." >&2
  exit 1
fi

EVENT="${EVENT:-cpu}"
DURATION="${DURATION:-60}"
OUTPUT_DIR="${OUTPUT_DIR:-${SCRIPT_DIR}/profiles}"
mkdir -p "${OUTPUT_DIR}"

TS="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${OUTPUT_DIR}/ap-${EVENT}-${TS}.html"

echo "[async-profiler] running event=${EVENT} duration=${DURATION}s pid=${PID}"
"${PROFILER}" -d "${DURATION}" -e "${EVENT}" -f "${OUT_FILE}" "$@" "${PID}"
echo "[async-profiler] flame graph written to ${OUT_FILE}"
