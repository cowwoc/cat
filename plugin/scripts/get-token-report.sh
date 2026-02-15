#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# get-token-report.sh - Generate token usage report
#
# USAGE: get-token-report.sh --session-id <id>
#
# OUTPUTS: Token usage table (skill output)
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
SESSION_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --session-id)
            SESSION_ID="$2"
            shift 2
            ;;
        *)
            echo "ERROR: $(basename "$0"): unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

# Default to CLAUDE_SESSION_ID
if [[ -z "$SESSION_ID" ]]; then
    SESSION_ID="${CLAUDE_SESSION_ID:-}"
fi

if [[ -z "$SESSION_ID" ]]; then
    echo "## Token Report"
    echo ""
    echo "**Unable to generate report** - No session ID available"
    echo ""
    echo "Session ID is required to compute token usage."
    exit 0
fi

# Call the token computation script
python3 "$SCRIPT_DIR/compute-token-table.py" --session-id "$SESSION_ID" 2>/dev/null || {
    echo "## Token Report"
    echo ""
    echo "**Unable to generate report** - Token computation failed"
    echo ""
    echo "This may occur if session data is not yet available."
}
