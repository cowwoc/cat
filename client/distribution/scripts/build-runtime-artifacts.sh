#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DISTRIBUTION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CLIENT_DIR="$(cd "${DISTRIBUTION_DIR}/.." && pwd)"
PLUGIN_DIR="${CLIENT_DIR}/plugin"
TARGET_DIR="${DISTRIBUTION_DIR}/target/runtime"
BUILDER=""
for runtime in codex claude; do
  candidate="${CLIENT_DIR}/cli/target/jlink/${runtime}/bin/build-runtime-artifacts"
  if [[ -x "$candidate" ]]; then
    BUILDER="$candidate"
    break
  fi
done

if [[ -z "$BUILDER" ]]; then
  echo "ERROR: build-runtime-artifacts launcher not found in any runtime image under ${CLIENT_DIR}/cli/target/jlink" >&2
  echo "Run: ${CLIENT_DIR}/mvnw -f ${CLIENT_DIR}/pom.xml -pl cli verify -e" >&2
  exit 1
fi

exec "$BUILDER" "$PLUGIN_DIR" "$CLIENT_DIR" "$TARGET_DIR"
