#!/bin/bash
#
# Remind Restart After Skill Modification (PostToolUse Hook)
#
# Detects when skills or settings are modified and reminds
# the user to restart Claude Code for changes to take effect.
#
# Runs AFTER Edit/Write completes successfully.

set -euo pipefail
trap 'echo "ERROR in remind-restart-after-skill-modification.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Read JSON from stdin
INPUT=$(cat)

# Extract file_path using jq
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Check if the modified file requires a restart
if [[ -z "$FILE_PATH" ]]; then
    exit 0
fi

NEEDS_RESTART=false
FILE_TYPE=""

# Check if it's a skill file
if [[ "$FILE_PATH" =~ \.claude/skills/[^/]+/SKILL\.md$ ]]; then
    NEEDS_RESTART=true
    FILE_TYPE="skill definition"
fi

# Check if it's settings.json
if [[ "$FILE_PATH" =~ \.claude/settings\.json$ ]]; then
    NEEDS_RESTART=true
    FILE_TYPE="settings"
fi

# Check if it's a hook script
if [[ "$FILE_PATH" =~ \.claude/hooks/[^/]+\.sh$ ]]; then
    NEEDS_RESTART=true
    FILE_TYPE="hook script"
fi

# If restart needed, output reminder to stderr for visibility
if [[ "$NEEDS_RESTART" == "true" ]]; then
    cat >&2 << EOF

🔴 RESTART REQUIRED - ASK USER NOW 🔴
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
You modified: $(basename "$FILE_PATH") ($FILE_TYPE)

⚠️  ACTION REQUIRED: Ask the user to restart Claude Code NOW.

Do NOT continue with other tasks until you have:
  1. TOLD the user: "Please restart Claude Code for changes to take effect"
  2. WAITED for confirmation they will restart

Changes to $FILE_TYPE files require a restart to take effect.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

EOF
fi

# Always allow (PostToolUse hooks can't block)
exit 0
