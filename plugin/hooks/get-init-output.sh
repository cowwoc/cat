#!/bin/bash
# get-init-output.sh - Pre-compute init boxes before skill runs
#
# TRIGGER: UserPromptSubmit
#
# When user invokes /cat:init, this hook:
# 1. Detects the skill invocation in USER_PROMPT
# 2. Runs build-init-boxes.py to generate all 8 boxes
# 3. Returns pre-computed boxes via additionalContext
#
# The skill then uses these pre-computed templates - no agent computation needed.
# Variables like {trust}, {N}, {task-name} remain as placeholders for the agent
# to substitute at runtime.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize hook
if ! init_hook; then
    echo '{}'
    exit 0
fi

# Check if this is a /cat:init command
# Match patterns: /cat:init, cat:init (with optional leading/trailing whitespace)
if [[ ! "$USER_PROMPT" =~ ^[[:space:]]*/cat:init[[:space:]]*$ ]] && \
   [[ ! "$USER_PROMPT" =~ ^[[:space:]]*cat:init[[:space:]]*$ ]]; then
    echo '{}'
    exit 0
fi

# Find plugin root for scripts
PLUGIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_SCRIPT="$PLUGIN_ROOT/scripts/build-init-boxes.py"

if [[ ! -f "$BUILD_SCRIPT" ]]; then
    echo '{}'
    exit 0
fi

# Run the build script to generate all boxes
BOXES_JSON=$(python3 "$BUILD_SCRIPT" --format json 2>/dev/null) || {
    echo '{}'
    exit 0
}

# Check for errors
if echo "$BOXES_JSON" | jq -e '.error' >/dev/null 2>&1; then
    echo '{}'
    exit 0
fi

# Extract each box from the JSON
DEFAULT_GATES=$(echo "$BOXES_JSON" | jq -r '.default_gates_configured // ""')
RESEARCH_SKIPPED=$(echo "$BOXES_JSON" | jq -r '.research_skipped // ""')
CHOOSE_PARTNER=$(echo "$BOXES_JSON" | jq -r '.choose_your_partner // ""')
CAT_INITIALIZED=$(echo "$BOXES_JSON" | jq -r '.cat_initialized // ""')
FIRST_TASK_WALKTHROUGH=$(echo "$BOXES_JSON" | jq -r '.first_task_walkthrough // ""')
FIRST_TASK_CREATED=$(echo "$BOXES_JSON" | jq -r '.first_task_created // ""')
ALL_SET=$(echo "$BOXES_JSON" | jq -r '.all_set // ""')
EXPLORE=$(echo "$BOXES_JSON" | jq -r '.explore_at_your_own_pace // ""')

# Build the complete output message with all boxes as templates
MESSAGE=$(cat << 'ENDOFMESSAGE'
PRE-COMPUTED INIT BOXES

Use these box templates EXACTLY as shown. Replace {variables} with actual values at runtime.

=== BOX: default_gates_configured ===
Variables: {N} = version count
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$DEFAULT_GATES"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: research_skipped ===
Variables: {version} = example version number (shown in help text)
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$RESEARCH_SKIPPED"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: choose_your_partner ===
Variables: none (static)
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$CHOOSE_PARTNER"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: cat_initialized ===
Variables: {trust}, {curiosity}, {patience} = user preference values
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$CAT_INITIALIZED"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: first_task_walkthrough ===
Variables: none (static)
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$FIRST_TASK_WALKTHROUGH"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: first_task_created ===
Variables: {task-name} = sanitized task name from user input
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$FIRST_TASK_CREATED"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: all_set ===
Variables: none (static)
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$ALL_SET"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

=== BOX: explore_at_your_own_pace ===
Variables: none (static)
ENDOFMESSAGE
)

MESSAGE+=$'\n'"$EXPLORE"$'\n'

MESSAGE+=$(cat << 'ENDOFMESSAGE'

INSTRUCTION: When displaying a box, copy the template EXACTLY and only replace the {variable} placeholders.
Do NOT recalculate padding or alignment - the boxes are pre-computed with correct widths.
ENDOFMESSAGE
)

output_hook_message "UserPromptSubmit" "$MESSAGE"
exit 0
