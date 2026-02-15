#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# get-work-boxes.sh - Generate work skill box templates
#
# USAGE: get-work-boxes.sh
#
# OUTPUTS: Work box templates (skill output)
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

if [[ $# -gt 0 ]]; then
  echo "ERROR: $(basename "$0") accepts no arguments, got $#" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call Python to generate boxes with correct alignment
python3 "$SCRIPT_DIR/get-work-boxes.py"
