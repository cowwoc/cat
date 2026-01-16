#!/bin/bash
set -euo pipefail

# Error handler - output helpful message to stderr on failure
trap 'echo "ERROR in block-main-rebase.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Block Rebase on Main Branch Hook
# Prevents git rebase commands when on main branch
#
# TRIGGER: PreToolUse (Bash)
#
# BLOCKED OPERATIONS:
# 1. git rebase (any form) when on main branch
#
# RATIONALE:
# Rebasing main rewrites history, making merged commits appear as direct
# commits on main. This breaks the audit trail and disrupts collaboration.
#
# ALLOWED:
# - Rebasing on feature branches (for squashing before merge)
# - Any non-rebase git operations on main

# Source JSON parsing library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"

# Initialize as Bash hook (reads stdin, parses JSON, extracts command)
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

# Use HOOK_COMMAND from init_bash_hook
COMMAND="$HOOK_COMMAND"
if [[ -z "$COMMAND" ]]; then
    echo '{}'
    exit 0
fi

# Check if command contains git rebase
COMMAND_LOWER=$(echo "$COMMAND" | tr '[:upper:]' '[:lower:]')
if ! echo "$COMMAND_LOWER" | grep -qE "(^|[;&|])[[:space:]]*git[[:space:]]+rebase"; then
	echo '{}'
	exit 0
fi

# Get current branch (try multiple methods)
CURRENT_BRANCH=""

# Method 1: Check if command changes to /workspace (main worktree) exactly
# This protects the main worktree's checkout state
# Must match /workspace exactly, not /workspace/.worktrees/* or other subdirs
if echo "$COMMAND" | grep -qE "cd[[:space:]]+(/workspace|['\"]*/workspace['\"]*)([[:space:]]|&&|;|$)"; then
	# Check if command also includes git checkout (changing what's checked out in main worktree)
	if echo "$COMMAND" | grep -qE "git[[:space:]]+checkout[[:space:]]"; then
		# Extract the branch being checked out
		CHECKOUT_BRANCH=$(echo "$COMMAND" | sed -n 's/.*git[[:space:]]\+checkout[[:space:]]\+\([^[:space:];&|]*\).*/\1/p' | head -1)
		# Block: trying to checkout a different branch in main worktree then rebase
		cat << EOF >&2

🚨 MAIN WORKTREE CHECKOUT CHANGE BLOCKED
═══════════════════════════════════════════════════════════════

❌ Attempted: cd /workspace && git checkout $CHECKOUT_BRANCH && git rebase
✅ Correct:   Rebase from your task worktree, not from /workspace

WHY THIS IS BLOCKED:
• This would change what's checked out in the main worktree
• The main worktree should always have 'main' checked out
• Changing it disrupts other operations and breaks assumptions

WHAT TO DO INSTEAD:
1. Run rebase from your task worktree:
   cd /workspace/.worktrees/$CHECKOUT_BRANCH
   git rebase main

2. Then return to main worktree for fast-forward merge:
   cd /workspace
   git merge --ff-only $CHECKOUT_BRANCH

═══════════════════════════════════════════════════════════════
EOF
		output_hook_block "Blocked: Cannot checkout '$CHECKOUT_BRANCH' in main worktree. Run 'git rebase main' from task worktree instead."
		exit 0
	fi
	CURRENT_BRANCH="main"
fi

# Method 2: Check the branch of the directory where command will execute
# BUG FIX: 2025-12-15 - Was checking /workspace's branch instead of $PWD's branch
# BUG FIX: 2025-12-25 - If command starts with "cd /path && ...", check that path's branch
#                       instead of the hook process's current directory
if [ -z "$CURRENT_BRANCH" ]; then
	# Check if command starts with cd to a different directory
	CD_TARGET=""
	if echo "$COMMAND" | grep -qE "^cd[[:space:]]+" ; then
		# Extract cd target (handles: cd /path, cd '/path', cd "/path")
		# Use xargs to trim trailing whitespace before && or other operators
		CD_TARGET=$(echo "$COMMAND" | sed -n "s/^cd[[:space:]]*['\"]\\{0,1\\}\\([^'\";&|]*\\)['\"]\\{0,1\\}.*/\\1/p" | head -1 | xargs)
	fi

	if [ -n "$CD_TARGET" ] && [ -d "$CD_TARGET" ]; then
		# Check branch in the target directory
		CURRENT_BRANCH=$(git -C "$CD_TARGET" branch --show-current 2>/dev/null || echo "")
	else
		# Fallback to current directory
		CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "")
	fi
fi

# If on main branch, block the rebase
if [ "$CURRENT_BRANCH" = "main" ]; then
	cat << EOF >&2

🚨 REBASE ON MAIN BLOCKED
═══════════════════════════════════════════════════════════════

❌ Attempted: git rebase on main branch
✅ Correct:   Main branch should never be rebased

WHY THIS IS BLOCKED:
• Rebasing main rewrites commit history
• Merged commits get recreated as direct commits
• This breaks the audit trail and disrupts collaboration

TO REBASE A TASK BRANCH ONTO MAIN:
Run the rebase from within your task's worktree, not from the main worktree:

  cd /workspace/.worktrees/<task-branch>
  git rebase main

The main worktree must always have 'main' checked out. Rebase operations
from here could change that, which breaks assumptions across the system.

═══════════════════════════════════════════════════════════════
❌ COMMAND BLOCKED - Rebase on main is prohibited
EOF
	output_hook_block "Blocked: git rebase on main branch is prohibited. Rebase rewrites history and breaks audit trail."
	exit 0
fi

# Allow rebase on non-main branches
echo '{}'
exit 0
