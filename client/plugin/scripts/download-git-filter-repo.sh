#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Locates git-filter-repo and outputs its invocation path.
#
# Resolves the platform-appropriate bundled binary under ${CLAUDE_PLUGIN_ROOT}/lib
# and verifies its SHA256 before outputting its invocation path.
#
# Requires:
#   CLAUDE_PLUGIN_ROOT - path to the CAT plugin root directory

CONF="${CLAUDE_PLUGIN_ROOT}/.git-filter-repo-config/release.conf"

# shellcheck source=plugin/scripts/sha256sum-portable.sh
source "${CLAUDE_PLUGIN_ROOT}/scripts/sha256sum-portable.sh"
# shellcheck source=plugin/scripts/git-filter-repo-release.sh
source "${CLAUDE_PLUGIN_ROOT}/scripts/git-filter-repo-release.sh"

git_filter_repo_require_conf "${CONF}"
PLATFORM="$(git_filter_repo_detect_platform "")"

BINARY_NAME="git-filter-repo-${PLATFORM}"
BUNDLED_BINARY="${CLAUDE_PLUGIN_ROOT}/lib/${BINARY_NAME}"
EXPECTED_SHA256="$(git_filter_repo_expected_sha256 "${CONF}" "${PLATFORM}")"

if [[ ! -f "${BUNDLED_BINARY}" ]]; then
  echo "ERROR: Bundled git-filter-repo executable not found" >&2
  echo "Expected bundled path: ${BUNDLED_BINARY}" >&2
  echo "Reinstall CAT engine artifact to restore bundled tools." >&2
  exit 1
fi

if [[ ! -x "${BUNDLED_BINARY}" ]]; then
  echo "ERROR: Bundled git-filter-repo is not executable" >&2
  echo "Expected bundled path: ${BUNDLED_BINARY}" >&2
  echo "Reinstall CAT engine artifact to restore executable permissions." >&2
  exit 1
fi

ACTUAL_SHA256=$(sha256sum_portable "${BUNDLED_BINARY}")
if [[ -z "${ACTUAL_SHA256}" ]]; then
  echo "ERROR: Failed to compute SHA256 checksum for bundled git-filter-repo" >&2
  echo "Expected bundled path: ${BUNDLED_BINARY}" >&2
  exit 1
fi
if [[ "${ACTUAL_SHA256}" != "${EXPECTED_SHA256}" ]]; then
  echo "ERROR: Bundled git-filter-repo checksum mismatch" >&2
  echo "Expected: ${EXPECTED_SHA256}" >&2
  echo "Actual:   ${ACTUAL_SHA256}" >&2
  echo "Expected bundled path: ${BUNDLED_BINARY}" >&2
  echo "Reinstall CAT engine artifact to restore bundled tools." >&2
  exit 1
fi

echo "${BUNDLED_BINARY}"
