#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 [mode|event] <pid> [extra-profiler-args]" >&2
  echo "Examples: $0 alloc 7035  |  $0 7035 -e wall" >&2
  echo "Env: EVENT=cpu|alloc|lock|wall|itimer, MODE=cpu|lock|alloc|wall (preset), DURATION=60, OUTPUT_DIR=..., ALLOCATION_INTERVAL=512k" >&2
  exit 1
fi

MODE_ARG=""
PID=""
ARGS=()

for arg in "$@"; do
  if [[ -z "${PID}" && "${arg}" =~ ^[0-9]+$ ]]; then
    PID="${arg}"
  elif [[ -z "${MODE_ARG}" && ( "${arg}" == "cpu" || "${arg}" == "alloc" || "${arg}" == "lock" || "${arg}" == "wall" || "${arg}" == "itimer" ) ]]; then
    MODE_ARG="${arg}"
  else
    ARGS+=("${arg}")
  fi
done

if [[ -z "${PID}" ]]; then
  echo "PID is required. Usage: $0 [mode|event] <pid> [extra-profiler-args]" >&2
  exit 1
fi

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

MODE="${MODE:-${MODE_ARG}}"
ALLOCATION_INTERVAL="${ALLOCATION_INTERVAL:-512k}"

# Provide friendly presets for common flame graphs
EXTRA_ARGS=("${ARGS[@]}")
case "${MODE}" in
  lock)
    EVENT="lock"
    EXTRA_ARGS+=("-t") # include thread states for contention attribution
    ;;
  alloc)
    EVENT="alloc"
    EXTRA_ARGS+=("-i" "${ALLOCATION_INTERVAL}")
    ;;
  wall)
    EVENT="wall"
    ;;
  cpu|"")
    # default CPU sampling
    ;;
  *)
    echo "Unknown MODE=${MODE}, falling back to EVENT=${EVENT}" >&2
    ;;
esac

echo "[async-profiler] running event=${EVENT} duration=${DURATION}s mode=${MODE:-manual} pid=${PID}"
"${PROFILER}" -d "${DURATION}" -e "${EVENT}" -f "${OUT_FILE}" "${EXTRA_ARGS[@]}" "${PID}"
echo "[async-profiler] flame graph written to ${OUT_FILE}"
