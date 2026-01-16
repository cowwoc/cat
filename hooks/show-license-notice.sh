#!/bin/bash
set -euo pipefail

# Show license notice on session start
#
# TRIGGER: SessionStart
#
# BEHAVIOR:
# - Displays a brief license notice to the user
# - Only shows once per session (first hook execution)

trap 'echo "ERROR in show-license-notice.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Output license notice directly to user
echo ""
echo "CAT is free for personal use and small organizations."
echo "Commercial use requires a license: https://github.com/cowwoc/cat/blob/main/LICENSE.md"
echo ""

# Return empty JSON (no additionalContext needed)
echo '{}'
