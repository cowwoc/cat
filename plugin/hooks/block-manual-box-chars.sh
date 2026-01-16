#!/bin/bash
# PreToolUse hook: Block manual box-drawing characters in Bash output
# Action item A009 - Escalation of A007 (box rendering still ignored)
#
# BLOCKS: Bash commands using echo/printf with box-drawing characters
# REASON: LLMs cannot reliably align Unicode box characters (M142, M149, M155)
# SOLUTION: Use render scripts in ${CLAUDE_PLUGIN_ROOT}/scripts/

set -euo pipefail
trap 'echo "ERROR in block-manual-box-chars.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Source JSON libraries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"
source "${SCRIPT_DIR}/lib/json-output.sh"

# Initialize as Bash hook
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

COMMAND="$HOOK_COMMAND"

# Only check echo/printf commands
if [[ "$COMMAND" != *"echo"* && "$COMMAND" != *"printf"* ]]; then
    echo '{}'
    exit 0
fi

# Box-drawing characters that indicate manual box construction
# Unicode: │ ╭ ╮ ╰ ╯ ─ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼ ═ ║ ╔ ╗ ╚ ╝
# Also check for common box patterns
BOX_CHARS='[│╭╮╰╯─┌┐└┘├┤┬┴┼═║╔╗╚╝]'

# Check if command contains box-drawing characters
if echo "$COMMAND" | grep -qE "$BOX_CHARS"; then
    # Allow if it's calling a render script (scripts/ directory)
    if [[ "$COMMAND" == *"/scripts/"* ]]; then
        echo '{}'
        exit 0
    fi

    # Allow if it's reading from a file (cat, Read output)
    if [[ "$COMMAND" == *"cat "* && "$COMMAND" != *"<<EOF"* ]]; then
        echo '{}'
        exit 0
    fi

    output_hook_block "Manual box characters detected in Bash output" \
        "LLMs cannot reliably align Unicode box characters (M142)." \
        "Use render scripts: \${CLAUDE_PLUGIN_ROOT}/scripts/work-progress.sh or status.sh"
    exit 0
fi

# Also check for heredoc patterns with box characters
# Pattern: echo "$(cat <<'EOF'  or  cat <<EOF
if [[ "$COMMAND" =~ (cat[[:space:]]+\<\<|printf.*\<\<) ]]; then
    # Extract heredoc content and check for box chars
    HEREDOC_CONTENT=$(echo "$COMMAND" | sed -n '/<<.*EOF/,/^EOF/p' || echo "")
    if echo "$HEREDOC_CONTENT" | grep -qE "$BOX_CHARS"; then
        output_hook_block "Manual box characters in heredoc" \
            "Box-drawing characters detected in heredoc content." \
            "Use render scripts instead of manual box construction."
        exit 0
    fi
fi

echo '{}'
exit 0
