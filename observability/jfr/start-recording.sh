#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <pid> [extra-jcmd-args]" >&2
  exit 1
fi

PID="$1"
shift || true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${JFR_OUTPUT_DIR:-${SCRIPT_DIR}/recordings}"
mkdir -p "${OUT_DIR}"

DURATION="${JFR_DURATION_SEC:-120}"
DELAY="${JFR_DELAY_SEC:-0}"
SETTINGS="${JFR_SETTINGS:-profile}"
NAME="${JFR_NAME:-load-executor-$(date +%Y%m%d-%H%M%S)}"
OUT_FILE="${OUT_DIR}/${NAME}.jfr"
LAST_FILE="${SCRIPT_DIR}/.last-recording"

echo "${NAME}" > "${LAST_FILE}"

CMD=(jcmd "${PID}" JFR.start name="${NAME}" settings="${SETTINGS}" duration="${DURATION}s" filename="${OUT_FILE}" dumponexit=true)
if [[ "${DELAY}" != "0" ]]; then
  CMD+=(delay="${DELAY}s")
fi

if [[ $# -gt 0 ]]; then
  CMD+=("$@")
fi

echo "[JFR] starting recording name=${NAME} duration=${DURATION}s settings=${SETTINGS}"
"${CMD[@]}"
echo "[JFR] recording file ${OUT_FILE}"
