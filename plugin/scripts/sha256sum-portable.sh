#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Portable SHA256 computation across Linux (sha256sum) and macOS (shasum -a 256).
# Usage: sha256sum_portable <file>
# Outputs: 64-character lowercase hex SHA256 hash on stdout
# Exits non-zero if neither sha256sum nor shasum is found.
sha256sum_portable() {
  local file="$1"
  if command -v sha256sum &>/dev/null; then
    sha256sum "${file}" 2>/dev/null | awk '{print $1}'
  elif command -v shasum &>/dev/null; then
    shasum -a 256 "${file}" 2>/dev/null | awk '{print $1}'
  else
    echo "ERROR: Neither sha256sum nor shasum found in PATH" >&2
    return 1
  fi
}
