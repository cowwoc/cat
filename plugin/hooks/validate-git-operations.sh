#!/bin/bash
# Hook: validate-git-operations.sh
# Trigger: PreToolUse for Bash
# Purpose: Warn or block dangerous git commands
# Return codes: 0=allow, 1=soft error, 2=block operation

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Configuration - customize for your project
PROTECTED_BRANCHES="v[0-9]+ main master release-"
BLOCK_FORCE_PUSH=true
BLOCK_FILTER_BRANCH_ALL=true

# Read the command from stdin (Claude Code passes tool input)
COMMAND="${1:-}"

# Skip if not a git command
if ! echo "$COMMAND" | grep -q "^git "; then
    exit 0
fi

# Check for dangerous --all flag with history rewriting
if echo "$COMMAND" | grep -qE "filter-branch.*--all|rebase.*--all"; then
    echo "BLOCKED: History rewriting with --all affects ALL branches"
    echo ""
    echo "This would affect version branches and shared history."
    echo ""
    echo "Safe alternative: Target a specific branch"
    echo "  git filter-branch <branch-name>"
    echo "  git rebase main"
    exit 2
fi

# Check for force push
if $BLOCK_FORCE_PUSH && echo "$COMMAND" | grep -qE "push.*(-f|--force)[^-]|push.*--force$"; then
    echo "WARNING: Force push detected"
    echo ""
    echo "Force push rewrites remote history and can cause data loss."
    echo ""
    echo "Safer alternative: git push --force-with-lease"
    echo ""
    echo "Proceed with caution."
    # Exit 0 to warn but not block (change to exit 2 to block)
    exit 0
fi

# Check for operations on protected branches
for pattern in $PROTECTED_BRANCHES; do
    if echo "$COMMAND" | grep -qE "(checkout|branch -[dD]|rebase|reset).*$pattern"; then
        echo "WARNING: Operation on protected branch pattern: $pattern"
        echo ""
        echo "Protected branches should not be modified directly."
        exit 0
    fi
done

# Command appears safe
exit 0
