#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# get-help-display.sh - Generate CAT help reference
#
# USAGE: get-help-display.sh
#
# OUTPUTS: Help content for /cat:help (skill output)
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

if [[ $# -gt 0 ]]; then
  echo "ERROR: $(basename "$0") accepts no arguments, got $#" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call Python to generate help display
python3 "${SCRIPT_DIR}/get-help-display.py"
