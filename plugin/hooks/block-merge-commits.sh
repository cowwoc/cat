#!/bin/bash
# Hook: block-merge-commits.sh
# Trigger: PreToolUse for Bash
# Purpose: Enforce linear git history by blocking merge commits
# Return codes: 0=allow, 1=soft error, 2=block operation
#
# BLOCKED:
#   - git merge --no-ff (explicitly creates merge commit)
#   - git merge without --ff-only or --squash (might create merge commit)
#
# WARNED (not blocked):
#   - MERGE_HEAD exists (merge in progress - informational)
#
# ALLOWED:
#   - git merge --ff-only (only fast-forward, fails if not possible)
#   - git merge --squash (creates single commit, no merge commit)
#
# See: Learning M047 - use ff-merge to maintain linear history

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Source the CAT hook library for consistent messaging
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"

# Check if MERGE_HEAD exists (merge in progress)
if git rev-parse -q --verify MERGE_HEAD > /dev/null 2>&1; then
    cat >&2 <<EOF
⚠️  WARNING: Merge in progress (MERGE_HEAD exists)

This project uses linear history (--ff-only merges).
If main has diverged from your branch:
  1. Abort: git merge --abort
  2. Rebase: /cat:git-rebase
  3. Then merge: git merge --ff-only <branch>
EOF
fi

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

# BLOCK: git merge without --ff-only or --squash (might create merge commit)
if ! echo "$COMMAND" | grep -qE '\-\-ff-only|\-\-squash'; then
    output_hook_block "
**BLOCKED: git merge without --ff-only may create merge commits**

Linear history is required. Use one of:
- \`git merge --ff-only <branch>\` - Fast-forward only, fails if not possible
- \`git merge --squash <branch>\` - Squash commits into one
- \`git rebase <branch>\` - Rebase for linear history

Or use the \`/cat:git-merge-linear\` skill which handles this correctly.

**See**: Learning M047 - merge commits break linear history
"
    exit 0
fi

# Allow command (has --ff-only or --squash)
echo '{}'
exit 0
