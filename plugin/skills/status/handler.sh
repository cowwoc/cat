#\!/usr/bin/env bash
# handler.sh - Generate SCRIPT OUTPUT for /cat:status
#
# Called by load-skill.sh on every invocation.
# Outputs the SCRIPT OUTPUT STATUS DISPLAY marker followed by the status box.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "SCRIPT OUTPUT STATUS DISPLAY:"
echo
"${SCRIPT_DIR}/../../scripts/get-status-display.sh" --project-dir "${CLAUDE_PROJECT_DIR}"
