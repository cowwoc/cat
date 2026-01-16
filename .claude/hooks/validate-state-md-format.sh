#!/bin/bash
# Hook: validate-state-md-format
# Type: PreToolUse (Write tool)
# Purpose: Enforce correct STATE.md format by blocking writes that don't match the template
#
# This prevents manual task creation with invalid STATE.md format.
# Agents should use /cat:add which uses templates with the correct format.

set -euo pipefail

# Read hook input from stdin
INPUT=$(cat)

# Extract tool name and file path
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // ""')
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // ""')
CONTENT=$(echo "$INPUT" | jq -r '.tool_input.content // ""')

# Only check Write tool operations
if [[ "$TOOL_NAME" != "Write" ]]; then
    echo '{"decision": "allow"}'
    exit 0
fi

# Only check STATE.md files in .claude/cat/issues/v*/v*.*/ (task STATE.md files)
if [[ ! "$FILE_PATH" =~ \.claude/cat/issues/v[0-9]+/v[0-9]+\.[0-9]+/[^/]+/STATE\.md$ ]]; then
    echo '{"decision": "allow"}'
    exit 0
fi

# Validate the content has the required bullet-point format
# Must have: - **Status:** on a line
if ! echo "$CONTENT" | grep -qE '^\- \*\*Status:\*\*'; then
    cat <<EOF
{
    "decision": "block",
    "reason": "STATE.md format violation: Missing '- **Status:** value' line.\n\nSTATE.md files must use bullet-point format:\n  - **Status:** pending\n  - **Progress:** 0%\n  - **Dependencies:** []\n  - **Last Updated:** YYYY-MM-DD\n\nUse /cat:add to create tasks with correct format, or fix the content to match the template."
}
EOF
    exit 0
fi

# Also check for Progress field
if ! echo "$CONTENT" | grep -qE '^\- \*\*Progress:\*\*'; then
    cat <<EOF
{
    "decision": "block",
    "reason": "STATE.md format violation: Missing '- **Progress:** value' line.\n\nSTATE.md files must use bullet-point format. Use /cat:add to create tasks with correct format."
}
EOF
    exit 0
fi

# Also check for Dependencies field
if ! echo "$CONTENT" | grep -qE '^\- \*\*Dependencies:\*\*'; then
    cat <<EOF
{
    "decision": "block",
    "reason": "STATE.md format violation: Missing '- **Dependencies:** [...]' line.\n\nSTATE.md files must use bullet-point format. Use /cat:add to create tasks with correct format."
}
EOF
    exit 0
fi

# Content is valid
echo '{"decision": "allow"}'
