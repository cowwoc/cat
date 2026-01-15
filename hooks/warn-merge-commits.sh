#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Hook: warn-merge-commits.sh
# Trigger: PreToolUse for Bash
# Purpose: Warn when a merge commit is being created (MERGE_HEAD exists)

# Check if this is a merge commit being created
if git rev-parse -q --verify MERGE_HEAD > /dev/null 2>&1; then
    echo "⚠️  WARNING: Creating a merge commit" >&2
    echo "" >&2
    echo "This project uses linear history (--ff-only merges)." >&2
    echo "If main has diverged from your branch, use:" >&2
    echo "  /cat:git-rebase to rebase your branch onto main first" >&2
    echo "" >&2
    echo "Then merge with: git merge --ff-only <branch>" >&2
    echo "" >&2
    # Allow to continue but warn (don't block)
fi

echo '{}'
exit 0
