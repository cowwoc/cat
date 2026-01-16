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

# Read the command from stdin (Claude Code passes tool input)
COMMAND="${1:-}"

# Skip if not a git merge command
if ! echo "$COMMAND" | grep -qE "^\s*git\s+merge"; then
    exit 0
fi

# BLOCK: git merge --no-ff (explicitly creates merge commit)
if echo "$COMMAND" | grep -qE 'git\s+merge\s+.*--no-ff|git\s+merge\s+--no-ff'; then
    echo "BLOCKED: git merge --no-ff creates merge commits"
    echo ""
    echo "Linear history is required. Use one of:"
    echo "  git merge --ff-only <branch>  # Fast-forward only, fails if not possible"
    echo "  git rebase <branch>           # Rebase for linear history"
    echo ""
    echo "Or use the /cat:git-merge-linear skill which handles this correctly."
    echo ""
    echo "See: Learning M047 - merge commits break linear history"
    exit 2
fi

# WARN: git merge without --ff-only (might create merge commit)
# Only warn if neither --ff-only nor --squash is present
if ! echo "$COMMAND" | grep -qE '\-\-ff-only|\-\-squash'; then
    echo "WARNING: git merge without --ff-only may create merge commits"
    echo ""
    echo "Consider using: git merge --ff-only <branch>"
    echo "This will fail if a fast-forward merge isn't possible,"
    echo "preventing accidental merge commits."
    echo ""
    # Don't block, just warn
fi

exit 0
