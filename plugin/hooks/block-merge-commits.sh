#!/bin/bash
# Hook: block-merge-commits.sh
# Trigger: PreToolUse for Bash
# Purpose: Enforce linear git history by blocking merge commits
# Return codes: 0=allow, 1=soft error, 2=block operation
#
# BLOCKED:
#   - git merge --no-ff (explicitly creates merge commit)
#
# WARNED (not blocked):
#   - git merge without --ff-only (might create merge commit if not fast-forwardable)
#
# ALLOWED:
#   - git merge --ff-only (only fast-forward, fails if not possible)
#   - git merge --ff (default, but creates merge commit if not fast-forwardable)
#
# See: Learning M047 - use ff-merge to maintain linear history

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Source the CAT hook library for consistent messaging
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"

# Read tool input from stdin (Claude Code passes JSON via stdin)
INPUT=""
if [ -t 0 ]; then
    echo '{}'
    exit 0
else
    INPUT="$(timeout 5s cat 2>/dev/null)" || INPUT=""
fi

# Extract the command from tool input
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

# Only check Bash commands
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null || echo "")
if [[ "$TOOL_NAME" != "Bash" ]] || [[ -z "$COMMAND" ]]; then
    echo '{}'
    exit 0
fi

# Skip if not a git merge command
if ! echo "$COMMAND" | grep -qE "(^|;|&&|\|)\s*git\s+merge"; then
    echo '{}'
    exit 0
fi

# BLOCK: git merge --no-ff (explicitly creates merge commit)
if echo "$COMMAND" | grep -qE 'git\s+merge\s+.*--no-ff|git\s+merge\s+--no-ff'; then
    output_hook_block "
**BLOCKED: git merge --no-ff creates merge commits**

Linear history is required. Use one of:
- \`git merge --ff-only <branch>\` - Fast-forward only, fails if not possible
- \`git rebase <branch>\` - Rebase for linear history

Or use the \`/cat:git-merge-linear\` skill which handles this correctly.

**See**: Learning M047 - merge commits break linear history
"
    exit 0
fi

# WARN: git merge without --ff-only (might create merge commit)
# Only warn if neither --ff-only nor --squash is present
if ! echo "$COMMAND" | grep -qE '\-\-ff-only|\-\-squash'; then
    # Output warning to stderr but allow command to proceed
    cat >&2 <<EOF
⚠️  WARNING: git merge without --ff-only may create merge commits

Consider using: git merge --ff-only <branch>
This will fail if a fast-forward merge isn't possible,
preventing accidental merge commits.

Or use /cat:git-merge-linear skill.
EOF
fi

# Allow command
echo '{}'
exit 0
