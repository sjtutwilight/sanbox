#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="${SCRIPT_DIR}/bin"
mkdir -p "${BIN_DIR}"

VERSION="${ASYNC_PROFILER_VERSION:-4.2.1}"

# 自动检测操作系统和架构
OS="$(uname -s)"
ARCH="$(uname -m)"

if [[ "${OS}" == "Darwin" ]]; then
  PLATFORM="macos"
  EXT="zip"
elif [[ "${OS}" == "Linux" ]]; then
  PLATFORM="linux-x64"
  EXT="tar.gz"
  if [[ "${ARCH}" == "aarch64" ]]; then
    PLATFORM="linux-arm64"
  fi
else
  echo "Unsupported OS: ${OS}" >&2
  exit 1
fi

DEFAULT_URL="https://github.com/async-profiler/async-profiler/releases/download/v${VERSION}/async-profiler-${VERSION}-${PLATFORM}.${EXT}"
ARCHIVE_URL="${ASYNC_PROFILER_URL:-$DEFAULT_URL}"

TMP_DIR="$(mktemp -d)"
ARCHIVE="${TMP_DIR}/async-profiler.${EXT}"

echo "[async-profiler] downloading ${ARCHIVE_URL}"
curl -sSL "${ARCHIVE_URL}" -o "${ARCHIVE}"

echo "[async-profiler] extracting"
if [[ "${EXT}" == "zip" ]]; then
  unzip -q "${ARCHIVE}" -d "${TMP_DIR}"
else
  tar -xzf "${ARCHIVE}" -C "${TMP_DIR}"
fi

EXTRACTED_DIR="$(find "${TMP_DIR}" -maxdepth 1 -type d -name 'async-profiler-*' | head -n1)"
if [[ -z "${EXTRACTED_DIR}" ]]; then
  echo "Failed to locate extracted async-profiler directory" >&2
  exit 1
fi

TARGET_DIR="${BIN_DIR}/$(basename "${EXTRACTED_DIR}")"
rm -rf "${TARGET_DIR}"
mv "${EXTRACTED_DIR}" "${TARGET_DIR}"

echo "[async-profiler] ready at ${TARGET_DIR}"
echo "You can now run ${SCRIPT_DIR}/profile.sh <pid>"
