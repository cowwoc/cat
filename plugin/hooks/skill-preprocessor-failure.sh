#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# PostToolUseFailure hook: Detect skill preprocessor failures and suggest /cat:feedback
#
# When a skill's !`` preprocessor command fails, this hook provides additionalContext
# telling the agent to suggest /cat:feedback to the user.

# Error handler - fail gracefully (hooks must not break the session)
trap 'exit 0' ERR

# Read JSON input from stdin
JSON_INPUT=""
if [ -t 0 ]; then
  exit 0
else
  JSON_INPUT="$(timeout 5s cat 2>/dev/null)" || exit 0
fi

[[ -z "$JSON_INPUT" ]] && exit 0

# Check if the error is a skill preprocessor failure
# Pattern: 'Bash command failed for pattern "!`"...'
ERROR=$(echo "$JSON_INPUT" | jq -r '.error // ""' 2>/dev/null || echo "")

if echo "$ERROR" | grep -q 'Bash command failed for pattern "!`'; then
  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PostToolUseFailure",
    "additionalContext": "A skill preprocessor command failed. Tell the user to run /cat:feedback to report this issue."
  }
}
EOF
fi

exit 0
