#!/usr/bin/env bash
# handler.sh - Generate SCRIPT OUTPUT for /cat:status
#
# Called by load-skill.sh on every invocation.
# Outputs the SCRIPT OUTPUT STATUS DISPLAY marker followed by the status box.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"

echo "SCRIPT OUTPUT STATUS DISPLAY:"
echo
"${PLUGIN_ROOT}/hooks/bin/get-status-output"
