#!/bin/bash
# precompute-config.sh - Pre-compute config display before skill runs
#
# TRIGGER: UserPromptSubmit
#
# When user invokes /cat:config, this hook:
# 1. Runs the precompute-config-display.sh script
# 2. Returns the pre-rendered display via additionalContext
#
# The skill then just outputs the pre-computed result - no agent computation needed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize hook
if ! init_hook; then
    echo '{}'
    exit 0
fi

# Check if this is a /cat:config command
if [[ ! "$USER_PROMPT" =~ ^[[:space:]]*/cat:config[[:space:]]*$ ]] && \
   [[ ! "$USER_PROMPT" =~ ^[[:space:]]*cat:config[[:space:]]*$ ]]; then
    echo '{}'
    exit 0
fi

# Find project root (look for .claude/cat directory)
PROJECT_ROOT=""
if [[ -n "${CLAUDE_PROJECT_DIR:-}" ]] && [[ -d "${CLAUDE_PROJECT_DIR}/.claude/cat" ]]; then
    PROJECT_ROOT="$CLAUDE_PROJECT_DIR"
elif [[ -d ".claude/cat" ]]; then
    PROJECT_ROOT="$(pwd)"
else
    # No CAT project found, let the skill handle the error
    echo '{}'
    exit 0
fi

# Find plugin root for scripts
PLUGIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DISPLAY_SCRIPT="$PLUGIN_ROOT/scripts/precompute-config-display.sh"

if [[ ! -f "$DISPLAY_SCRIPT" ]]; then
    echo '{}'
    exit 0
fi

# Run the display script
CONFIG_FILE="$PROJECT_ROOT/.claude/cat/cat-config.json"
OUTPUT=$("$DISPLAY_SCRIPT" "$CONFIG_FILE" 2>/dev/null) || {
    echo '{}'
    exit 0
}

if [[ -z "$OUTPUT" ]]; then
    echo '{}'
    exit 0
fi

# Build the complete output message
MESSAGE=$(cat << EOF
PRE-COMPUTED CONFIG DISPLAY (copy exactly):

$OUTPUT

INSTRUCTION: Output the above box EXACTLY as shown. Do NOT recompute or modify alignment.
EOF
)

output_hook_message "UserPromptSubmit" "$MESSAGE"
exit 0
