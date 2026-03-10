#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Downloads BFG Repo-Cleaner JAR from Maven Central (if not already cached).
# Outputs the path to the JAR on success.
#
# Requires:
#   CLAUDE_PLUGIN_ROOT - path to the CAT plugin root directory

BFG_JAR="${CLAUDE_PLUGIN_ROOT}/lib/bfg.jar"
BFG_URL="https://repo1.maven.org/maven2/com/madgag/bfg/1.14.0/bfg-1.14.0.jar"
BFG_SHA256="1a75e9390541f4b55d9c01256b361b815c1e0a263e2fb3d072b55c2911ead0b7"

if [[ -f "${BFG_JAR}" ]]; then
  echo "${BFG_JAR}"
  exit 0
fi

mkdir -p "${CLAUDE_PLUGIN_ROOT}/lib"

TMP_JAR="${BFG_JAR}.tmp"

echo "Downloading BFG Repo-Cleaner from Maven Central..." >&2
if ! curl -fsSL -o "${TMP_JAR}" "${BFG_URL}"; then
  echo "ERROR: Failed to download BFG from ${BFG_URL}" >&2
  rm -f "${TMP_JAR}"
  exit 1
fi

echo "Verifying SHA256 checksum..." >&2
ACTUAL_SHA256=$(sha256sum "${TMP_JAR}" | awk '{print $1}')
if [[ "${ACTUAL_SHA256}" != "${BFG_SHA256}" ]]; then
  echo "ERROR: SHA256 checksum mismatch for BFG JAR." >&2
  echo "  Expected: ${BFG_SHA256}" >&2
  echo "  Actual:   ${ACTUAL_SHA256}" >&2
  rm -f "${TMP_JAR}"
  exit 1
fi

mv "${TMP_JAR}" "${BFG_JAR}"
echo "BFG downloaded and verified at ${BFG_JAR}" >&2

echo "${BFG_JAR}"
