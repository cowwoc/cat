#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Locates or downloads git-filter-repo and outputs its invocation path.
#
# Resolution order:
#   1. If python3 is installed and the git_filter_repo module is importable, outputs
#      "python3 -m git_filter_repo" (uses the module directly).
#   2. If the git-filter-repo script is on PATH, outputs its path.
#   3. Downloads the pre-built platform binary from a CAT GitHub release, caches it
#      under ${CLAUDE_PLUGIN_ROOT}/lib/, verifies SHA256, and outputs its path.
#
# Requires:
#   CLAUDE_PLUGIN_ROOT - path to the CAT plugin root directory

CONF="${CLAUDE_PLUGIN_ROOT}/.git-filter-repo-config/release.conf"

if [[ ! -f "${CONF}" ]]; then
  echo "ERROR: Release config not found: ${CONF}" >&2
  echo "Expected file: ${CONF}" >&2
  exit 1
fi

# --- Check 1: python3 with git_filter_repo module ---
if command -v python3 &>/dev/null; then
  if python3 -c "import git_filter_repo" 2>/dev/null; then
    echo "python3 -m git_filter_repo"
    exit 0
  fi
fi

# --- Check 2: git-filter-repo on PATH ---
if command -v git-filter-repo &>/dev/null; then
  echo "$(command -v git-filter-repo)"
  exit 0
fi

# Parse release.conf safely without executing arbitrary shell code
RELEASE_TAG=$(grep -E '^RELEASE_TAG=' "${CONF}" | sed 's/^RELEASE_TAG="\(.*\)"$/\1/' | head -1 || true)
if [[ -z "${RELEASE_TAG}" ]]; then
  echo "ERROR: RELEASE_TAG not found in ${CONF}" >&2
  exit 1
fi
if ! echo "${RELEASE_TAG}" | grep -qE '^git-filter-repo-v[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "ERROR: RELEASE_TAG in ${CONF} has unexpected format: ${RELEASE_TAG}" >&2
  echo "Expected format: git-filter-repo-vX.Y.Z" >&2
  exit 1
fi

# Parse platform SHA256 values and validate format
declare -A PLATFORM_HASHES
VALIDATION_ERRORS=""
for PLATFORM_NAME in linux_x64 linux_aarch64 macos_x64 macos_aarch64; do
  VAR_NAME="PLATFORM_SHA256_${PLATFORM_NAME}"
  # Note: VAR_NAME is always one of {PLATFORM_SHA256_linux_x64, linux_aarch64, macos_x64, macos_aarch64},
  # containing no regex metacharacters, so direct interpolation is safe.
  HASH=$(grep -E "^${VAR_NAME}=" "${CONF}" | sed "s/^$(printf '%s\n' "${VAR_NAME}" | sed -e 's/[\/&]/\\&/g')=\"\(.*\)\"$/\1/" | head -1 || true)
  if [[ -z "${HASH}" ]]; then
    VALIDATION_ERRORS="${VALIDATION_ERRORS}\n  Missing: ${VAR_NAME}"
  elif ! echo "${HASH}" | grep -qE '^[a-f0-9]{64}$'; then
    VALIDATION_ERRORS="${VALIDATION_ERRORS}\n  Invalid SHA256 format for ${VAR_NAME}: ${HASH}"
  else
    PLATFORM_HASHES[${PLATFORM_NAME}]="${HASH}"
  fi
done
if [[ -n "${VALIDATION_ERRORS}" ]]; then
  echo "ERROR: Invalid or missing SHA256 fields in ${CONF}:${VALIDATION_ERRORS}" >&2
  exit 1
fi

# --- Check 3: Download pre-built binary ---

# shellcheck source=plugin/scripts/sha256sum-portable.sh
source "${CLAUDE_PLUGIN_ROOT}/scripts/sha256sum-portable.sh"

# --- Detect platform ---
# Maps uname output to platform identifiers:
#   Linux x86_64  -> linux-x64
#   Linux aarch64 -> linux-aarch64
#   macOS x86_64  -> macos-x64
#   macOS arm64   -> macos-aarch64
OS=$(uname -s)
ARCH=$(uname -m)
PLATFORM=""

case "${OS}" in
  Linux)
    case "${ARCH}" in
      x86_64)  PLATFORM="linux-x64" ;;
      aarch64) PLATFORM="linux-aarch64" ;;
      arm64)   PLATFORM="linux-aarch64" ;;
      *)
        echo "ERROR: Unsupported Linux architecture: ${ARCH}" >&2
        echo "Supported: x86_64, aarch64" >&2
        exit 1
        ;;
    esac
    ;;
  Darwin)
    case "${ARCH}" in
      x86_64)  PLATFORM="macos-x64" ;;
      arm64)   PLATFORM="macos-aarch64" ;;
      *)
        echo "ERROR: Unsupported macOS architecture: ${ARCH}" >&2
        echo "Supported: x86_64, arm64" >&2
        exit 1
        ;;
    esac
    ;;
  *)
    echo "ERROR: Unsupported operating system: ${OS}" >&2
    exit 1
    ;;
esac

if [[ -z "${PLATFORM}" ]]; then
  echo "ERROR: Failed to detect platform (OS=${OS}, ARCH=${ARCH})" >&2
  exit 1
fi

BINARY_NAME="git-filter-repo-${PLATFORM}"
CACHE_DIR="${CLAUDE_PLUGIN_ROOT}/lib"
CACHED_BINARY="${CACHE_DIR}/${BINARY_NAME}"

# Determine expected SHA256 for the platform
# This allowlist must be kept in sync with the platform matrix in .github/workflows/build-git-filter-repo.yml
PLATFORM_NORMALIZED="${PLATFORM//-/_}"
EXPECTED_SHA256="${PLATFORM_HASHES[${PLATFORM_NORMALIZED}]:-}"

if [[ -z "${EXPECTED_SHA256}" ]]; then
  echo "ERROR: No SHA256 configured for platform '${PLATFORM}' in ${CONF}" >&2
  echo "Supported platforms: linux-x64, linux-aarch64, macos-x64, macos-aarch64" >&2
  exit 1
fi

# Use cached binary if it exists, passes version check, and passes SHA256 verification
if [[ -f "${CACHED_BINARY}" ]] && [[ -z "${GFR_FORCE_DOWNLOAD:-}" ]]; then
  CACHED_VERSION_FILE="${CACHED_BINARY}.version"
  VERSION_CHECK_PASSED=false

  # Check version file first (faster than SHA256 computation)
  if [[ -f "${CACHED_VERSION_FILE}" ]]; then
    CACHED_VERSION=$(cat "${CACHED_VERSION_FILE}" 2>/dev/null || true)
    if [[ "${CACHED_VERSION}" != "${RELEASE_TAG}" ]]; then
      echo "Cached binary version mismatch (expected: ${RELEASE_TAG}, got: ${CACHED_VERSION}); re-downloading..." >&2
      rm -f "${CACHED_BINARY}" "${CACHED_VERSION_FILE}"
    else
      VERSION_CHECK_PASSED=true
    fi
  else
    # No version file present; flag as passed for backward compatibility with older cached binaries
    VERSION_CHECK_PASSED=true
  fi

  # If version check passed, verify SHA256
  if [[ "${VERSION_CHECK_PASSED}" == "true" ]]; then
    ACTUAL_SHA256=$(sha256sum_portable "${CACHED_BINARY}")
    if [[ "${ACTUAL_SHA256}" == "${EXPECTED_SHA256}" ]]; then
      # SHA256 matches; create/update version file for future checks
      echo "${RELEASE_TAG}" > "${CACHED_VERSION_FILE}"
      echo "${CACHED_BINARY}"
      exit 0
    else
      echo "Cached binary failed SHA256 verification; re-downloading..." >&2
    fi
  fi
fi

# Construct GitHub release download URL
REPO_OWNER="cowwoc"
REPO_NAME="cat"
BINARY_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/${RELEASE_TAG}/${BINARY_NAME}"

if ! mkdir -p "${CACHE_DIR}"; then
  echo "ERROR: Cannot create cache directory: ${CACHE_DIR}" >&2
  exit 1
fi
if [[ ! -w "${CACHE_DIR}" ]]; then
  echo "ERROR: Cache directory is not writable: ${CACHE_DIR}" >&2
  exit 1
fi

TMP_BINARY="${CACHED_BINARY}.tmp.${CLAUDE_SESSION_ID:-$$}.$$.$RANDOM"
trap "rm -f '${TMP_BINARY}'" EXIT

echo "Downloading git-filter-repo standalone binary for ${PLATFORM}..." >&2
echo "  URL: ${BINARY_URL}" >&2
if ! curl -fsSL --max-time 120 -o "${TMP_BINARY}" "${BINARY_URL}"; then
  CURL_EXIT=$?
  echo "ERROR: Failed to download git-filter-repo" >&2
  echo "  URL: ${BINARY_URL}" >&2
  echo "  Curl exit code: ${CURL_EXIT}" >&2
  case ${CURL_EXIT} in
    7)  echo "  Connection refused. Check network connectivity." >&2 ;;
    28) echo "  Download timeout (120s). Server may be slow or unreachable." >&2 ;;
    22) echo "  HTTP error. The URL may not exist or server is down." >&2 ;;
    *)  echo "  See 'man curl' for exit code ${CURL_EXIT} details." >&2 ;;
  esac
  exit 1
fi

if [[ ! -s "${TMP_BINARY}" ]]; then
  echo "ERROR: Downloaded file is empty or missing: ${TMP_BINARY}" >&2
  exit 1
fi

echo "Verifying SHA256 checksum..." >&2
ACTUAL_SHA256=$(sha256sum_portable "${TMP_BINARY}")
if [[ -z "${ACTUAL_SHA256}" ]]; then
  echo "ERROR: Failed to compute SHA256 checksum for ${TMP_BINARY}" >&2
  exit 1
fi
if [[ "${ACTUAL_SHA256}" != "${EXPECTED_SHA256}" ]]; then
  echo "ERROR: SHA256 checksum mismatch for git-filter-repo binary." >&2
  echo "  Expected: ${EXPECTED_SHA256}" >&2
  echo "  Actual:   ${ACTUAL_SHA256}" >&2
  exit 1
fi

mv "${TMP_BINARY}" "${CACHED_BINARY}"
chmod +x "${CACHED_BINARY}"

# Write version file for future cache coherency checks
CACHED_VERSION_FILE="${CACHED_BINARY}.version"
echo "${RELEASE_TAG}" > "${CACHED_VERSION_FILE}"

echo "git-filter-repo downloaded and verified at ${CACHED_BINARY}" >&2

echo "${CACHED_BINARY}"
