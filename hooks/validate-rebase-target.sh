#!/bin/bash
set -euo pipefail

# Error handler - output helpful message to stderr on failure
trap 'echo "ERROR in validate-rebase-target.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Validate Rebase Target Hook
# Warns when using origin/X instead of local X when local branch exists
#
# TRIGGER: PreToolUse (Bash)
#
# DETECTED ISSUE:
# Using `git rebase origin/main` when local `main` branch exists and differs
#
# RATIONALE:
# Per git-workflow.md Branch Reference Resolution:
# - When user says "rebase on X", prefer local branch X
# - Remote references may be stale or different from local
# - User intent is typically to work with local state
#
# ADDED: 2026-01-07 after agent used origin/main instead of local main
# when user said "rebase on main"

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

# Check if command contains git rebase with origin/ prefix
COMMAND_LOWER=$(echo "$COMMAND" | tr '[:upper:]' '[:lower:]')
# Match: git rebase origin/, git rebase -i origin/, git rebase --interactive origin/
if ! echo "$COMMAND_LOWER" | grep -qE "(^|[;&|])[[:space:]]*git[[:space:]]+rebase[[:space:]]+(-[a-z]+[[:space:]]+|--[a-z-]+[[:space:]]+)*origin/"; then
	echo '{}'
	exit 0
fi

# Extract the branch name after origin/
# Handles: git rebase origin/main, git rebase -i origin/main, etc.
REMOTE_BRANCH=$(echo "$COMMAND" | grep -oE "origin/[a-zA-Z0-9_-]+" | head -1 | sed 's/origin\///')

if [ -z "$REMOTE_BRANCH" ]; then
	echo '{}'
	exit 0
fi

# Check if local branch with same name exists
if git rev-parse --verify "$REMOTE_BRANCH" >/dev/null 2>&1; then
	# Local branch exists - check if it differs from origin
	LOCAL_COMMIT=$(git rev-parse "$REMOTE_BRANCH" 2>/dev/null || echo "")
	REMOTE_COMMIT=$(git rev-parse "origin/$REMOTE_BRANCH" 2>/dev/null || echo "")

	if [ -n "$LOCAL_COMMIT" ] && [ -n "$REMOTE_COMMIT" ] && [ "$LOCAL_COMMIT" != "$REMOTE_COMMIT" ]; then
		# Branches differ - warn about using origin/ when local exists
		cat << EOF >&2

⚠️  REBASE TARGET WARNING
═══════════════════════════════════════════════════════════════

You're using: git rebase origin/$REMOTE_BRANCH
Local branch exists: $REMOTE_BRANCH

LOCAL vs REMOTE DIFFER:
  Local  $REMOTE_BRANCH: ${LOCAL_COMMIT:0:7}
  Remote origin/$REMOTE_BRANCH: ${REMOTE_COMMIT:0:7}

Per git-workflow.md § Branch Reference Resolution:
  ✅ PREFER: git rebase $REMOTE_BRANCH  (uses local branch)
  ⚠️  CURRENT: git rebase origin/$REMOTE_BRANCH  (uses remote)

WHEN TO USE WHICH:
  • Use local $REMOTE_BRANCH: Default when user says "rebase on $REMOTE_BRANCH"
  • Use origin/$REMOTE_BRANCH: Only if user explicitly requests remote

If user said "rebase on $REMOTE_BRANCH", you should use:
  git rebase $REMOTE_BRANCH

═══════════════════════════════════════════════════════════════
⚠️  Proceeding with command, but verify this is the intended target
EOF
	fi
fi

# Allow command to proceed (this is a warning, not a block)
echo '{}'
exit 0
