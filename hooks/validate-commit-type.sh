#!/bin/bash
# Hook: validate-commit-type.sh
# Trigger: PreToolUse for Bash
# Purpose: Enforce CAT commit type standards
# Return codes: 0=allow, 1=soft error, 2=block operation
#
# VALID TYPES (from commit-types.md):
#   feature, bugfix, test, refactor, performance, docs, style, config, planning
#
# BLOCKED (common mistakes):
#   feat, fix, chore, build, ci, perf (abbreviated/wrong types)
#
# See: Learning M059, M068 - commit type confusion

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"

# Read tool input from stdin
INPUT=""
if [ -t 0 ]; then
    echo '{}'
    exit 0
else
    INPUT="$(timeout 5s cat 2>/dev/null)" || INPUT=""
fi

# Extract command and tool name
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null || echo "")

# Only check Bash commands
if [[ "$TOOL_NAME" != "Bash" ]] || [[ -z "$COMMAND" ]]; then
    echo '{}'
    exit 0
fi

# Skip if not a git commit command
if ! echo "$COMMAND" | grep -qE "(^|;|&&|\|)\s*git\s+commit"; then
    echo '{}'
    exit 0
fi

# Valid CAT commit types
VALID_TYPES="feature|bugfix|test|refactor|performance|docs|style|config|planning"

# Invalid types (common mistakes)
INVALID_TYPES="feat|fix|chore|build|ci|perf"

# Extract commit message from -m flag (handles both -m "msg" and -m 'msg')
# This regex handles: git commit -m "msg", git commit -m 'msg', git commit -m msg
COMMIT_MSG=""
if echo "$COMMAND" | grep -qE '\-m\s+["\x27]'; then
    # Message in quotes
    COMMIT_MSG=$(echo "$COMMAND" | grep -oE '\-m\s+["\x27][^"\x27]*' | head -1 | sed -E 's/-m\s+["\x27]//')
elif echo "$COMMAND" | grep -qE '\-m\s+\S'; then
    # Message without quotes (single word)
    COMMIT_MSG=$(echo "$COMMAND" | grep -oE '\-m\s+\S+' | head -1 | sed -E 's/-m\s+//')
fi

# Also handle heredoc format: git commit -m "$(cat <<'EOF'
if echo "$COMMAND" | grep -qE '\-m\s+"\$\(cat\s+<<'; then
    # Extract from heredoc - get content between EOF markers
    COMMIT_MSG=$(echo "$COMMAND" | grep -oP '(?<=EOF\n).*?(?=\nEOF)' | head -1 || echo "")
    # If that doesn't work, try simpler extraction
    if [[ -z "$COMMIT_MSG" ]]; then
        COMMIT_MSG=$(echo "$COMMAND" | grep -oE 'EOF[^E]+' | head -1 | sed 's/EOF//' | tr -d '\n' | xargs)
    fi
fi

# If no message found, allow (might be interactive or other format)
if [[ -z "$COMMIT_MSG" ]]; then
    echo '{}'
    exit 0
fi

# Extract the type prefix (everything before the first colon)
TYPE_PREFIX=$(echo "$COMMIT_MSG" | grep -oE '^[a-zA-Z]+:' | sed 's/://' || echo "")

if [[ -z "$TYPE_PREFIX" ]]; then
    # No type prefix found - warn but don't block
    output_hook_warning "
⚠️  Commit message missing type prefix

Expected format: {type}: {description}
Valid types: feature, bugfix, test, refactor, performance, docs, style, config, planning

Example: feature: add user registration
"
    exit 0
fi

# Check for invalid types
if echo "$TYPE_PREFIX" | grep -qE "^($INVALID_TYPES)$"; then
    output_hook_block "
**BLOCKED: Invalid commit type '$TYPE_PREFIX:'**

This type is NOT in CAT's standard commit types.

**Common mappings:**
- \`feat:\` → use \`feature:\`
- \`fix:\` → use \`bugfix:\`
- \`chore:\` → use \`config:\` (for tooling) or \`refactor:\` (for cleanup)
- \`perf:\` → use \`performance:\`

**Valid CAT types:** feature, bugfix, test, refactor, performance, docs, style, config, planning

**See:** commit-types.md and learnings M059, M068
"
    exit 0
fi

# Check if type is valid
if ! echo "$TYPE_PREFIX" | grep -qE "^($VALID_TYPES)$"; then
    output_hook_block "
**BLOCKED: Unknown commit type '$TYPE_PREFIX:'**

**Valid CAT types:** feature, bugfix, test, refactor, performance, docs, style, config, planning

**Guidance:**
- \`docs:\` = User-facing (README, API docs)
- \`config:\` = Claude-facing (CLAUDE.md, hooks, skills)

**See:** commit-types.md
"
    exit 0
fi

# Valid commit type
echo '{}'
exit 0
