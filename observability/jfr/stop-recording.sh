#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <pid> [output-file]" >&2
  exit 1
fi

PID="$1"
shift || true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${JFR_OUTPUT_DIR:-${SCRIPT_DIR}/recordings}"
mkdir -p "${OUT_DIR}"

LAST_FILE="${SCRIPT_DIR}/.last-recording"
if [[ -s "${LAST_FILE}" ]]; then
  NAME="$(cat "${LAST_FILE}")"
else
  NAME="${JFR_NAME:-latest}"
fi

TS="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${1:-${OUT_DIR}/${NAME}-stop-${TS}.jfr}"

echo "[JFR] stopping recording name=${NAME}"
jcmd "${PID}" JFR.stop name="${NAME}" filename="${OUT_FILE}"
echo "[JFR] dump written to ${OUT_FILE}"
