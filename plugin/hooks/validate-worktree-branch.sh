#!/bin/bash
# Hook: validate-worktree-branch.sh
# Trigger: PreToolUse for Bash
# Purpose: Ensure operations stay in correct worktree/branch
# Return codes: 0=allow, 1=soft error, 2=block operation

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Read the command from tool input
COMMAND="${1:-}"

# Skip if not a git command that might switch branches
if ! echo "$COMMAND" | grep -qE "^git (checkout|switch|worktree)"; then
    exit 0
fi

# Get current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

if [[ -z "$CURRENT_BRANCH" ]]; then
    # Not in a git repository
    exit 0
fi

# Check if we're in a worktree
WORKTREE_PATH=$(git rev-parse --show-toplevel 2>/dev/null || echo "")
GIT_COMMON_DIR=$(git rev-parse --git-common-dir 2>/dev/null || echo "")

# Determine if this is a worktree (git-common-dir differs from git-dir)
GIT_DIR=$(git rev-parse --git-dir 2>/dev/null || echo "")
IS_WORKTREE=false
if [[ "$GIT_COMMON_DIR" != "$GIT_DIR" && "$GIT_COMMON_DIR" != ".git" ]]; then
    IS_WORKTREE=true
fi

# If in a worktree, warn about branch switching
if $IS_WORKTREE; then
    # Check if command is trying to checkout a different branch
    if echo "$COMMAND" | grep -qE "checkout|switch"; then
        TARGET_BRANCH=$(echo "$COMMAND" | grep -oE "(checkout|switch)[[:space:]]+([^[:space:]-][^[:space:]]*)" | awk '{print $2}' || true)

        if [[ -n "$TARGET_BRANCH" && "$TARGET_BRANCH" != "$CURRENT_BRANCH" && "$TARGET_BRANCH" != "-" ]]; then
            echo "WARNING: Switching branches in a worktree"
            echo ""
            echo "Current worktree: $WORKTREE_PATH"
            echo "Current branch: $CURRENT_BRANCH"
            echo "Target branch: $TARGET_BRANCH"
            echo ""
            echo "Worktrees are typically tied to specific branches."
            echo "Consider using the appropriate worktree instead."
            # Warn but don't block
            exit 0
        fi
    fi
fi

# Check expected branch pattern for CAT issues: {version}-{issue-name}
# Supports: MAJOR (1), MAJOR.MINOR (1.0), MAJOR.MINOR.PATCH (1.0.0)
if [[ "$CURRENT_BRANCH" != "main" && "$CURRENT_BRANCH" != "master" ]]; then
    # Source shared version utilities
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    VERSION_UTILS="${SCRIPT_DIR}/../scripts/lib/version-utils.sh"

    if [[ -f "$VERSION_UTILS" ]]; then
        source "$VERSION_UTILS"

        # Extract version part (everything before first dash followed by letter)
        VERSION_PART="${CURRENT_BRANCH%%-[a-zA-Z]*}"

        # Validate using shared function
        if ! validate_version "$VERSION_PART"; then
            # Only warn if this looks like it should be an issue branch
            if echo "$CURRENT_BRANCH" | grep -qE "^(issue|task|feature|bugfix|hotfix)"; then
                echo "NOTE: Branch naming convention"
                echo ""
                echo "Current branch: $CURRENT_BRANCH"
                echo "Expected pattern: {version}-{issue-name}"
                echo "Examples: 1-issue, 1.0-issue, 1.0.0-issue"
            fi
        fi
    fi
fi

exit 0
