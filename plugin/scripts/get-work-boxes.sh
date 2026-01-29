#!/usr/bin/env bash
# get-work-boxes.sh - Generate work skill box templates
#
# USAGE: get-work-boxes.sh
#
# OUTPUTS: Pre-rendered work box templates
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call Python to generate boxes with correct alignment
python3 "$SCRIPT_DIR/get-work-boxes.py"
