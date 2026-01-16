#!/bin/bash
# Hook: Warn when main agent edits source files directly
# Triggered by: PreToolUse on Edit tool
#
# A003/M097: Main agent should delegate code edits to subagents.
# This hook warns (does not block) when editing source files.
#
# Allowed without warning:
# - .claude/ directory (orchestration files)
# - STATE.md, PLAN.md, CHANGELOG.md (task metadata)
# - CLAUDE.md (project instructions)

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

# Get just the filename for checking
FILENAME=$(basename "$FILE_PATH")

# Allowed patterns (no warning needed)
ALLOWED_PATTERNS=(
    ".claude/"
    "STATE.md"
    "PLAN.md"
    "CHANGELOG.md"
    "CLAUDE.md"
    "ROADMAP.md"
    "PROJECT.md"
    "mistakes.json"
    "retrospectives.json"
    "hooks/"
    "skills/"
)

for pattern in "${ALLOWED_PATTERNS[@]}"; do
    if [[ "$FILE_PATH" == *"$pattern"* ]]; then
        echo '{}'
        exit 0
    fi
done

# This appears to be a source file edit - warn but don't block
output_hook_warning "⚠️ MAIN AGENT SOURCE EDIT DETECTED (A003/M097)

File: $FILE_PATH

Main agent should delegate source code edits to subagents.
If you are the main CAT orchestrator, consider:
1. Spawning a subagent for this edit
2. Or confirming this is intentional (trivial fix, not during task execution)

Proceeding with edit (warning only, not blocked)."

exit 0
