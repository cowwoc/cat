#!/bin/bash
# Hook: Warn when editing skill SKILL.md without skill-builder
# Triggered by: PreToolUse on Edit tool
#
# A019/M213: Skills should be updated using /cat:skill-builder for proper
# backward reasoning and step extraction. This hook warns when editing
# SKILL.md files directly.

set -euo pipefail

# Source standard hook libraries
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize hook (reads JSON from stdin)
if ! init_hook; then
    echo '{}'
    exit 0
fi

# Only check Edit tool calls
if [[ "$TOOL_NAME" != "Edit" ]]; then
    echo '{}'
    exit 0
fi

# Extract file_path from tool_input
FILE_PATH=""
if $_JQ_AVAILABLE; then
    FILE_PATH=$(echo "$HOOK_JSON" | jq -r '.tool_input.file_path // empty' 2>/dev/null) || FILE_PATH=""
fi

# If no file path, allow
if [[ -z "$FILE_PATH" ]]; then
    echo '{}'
    exit 0
fi

# Only check SKILL.md files in skills directories
if [[ ! "$FILE_PATH" =~ /skills/[^/]+/SKILL\.md$ ]]; then
    echo '{}'
    exit 0
fi

# Extract skill name
SKILL_NAME=$(echo "$FILE_PATH" | sed -E 's|.*/skills/([^/]+)/SKILL\.md$|\1|')

# Warn about skill edit
output_hook_warning "PreToolUse" "📝 SKILL EDIT DETECTED (A019/M213)

Editing skill: $SKILL_NAME

Before modifying skill documentation, consider using /cat:skill-builder:
- Decomposes goal into forward steps via backward reasoning
- Identifies computation candidates for hook extraction
- Ensures consistent skill structure

If you've already used skill-builder, or this is a minor fix (typo, formatting), proceed.

Proceeding with edit (warning only, not blocked)."

exit 0
