#!/bin/bash
# PreToolUse hook: Remind to use git skills for rebase/squash operations
#
# ADDED: 2025-12-22 after using ad-hoc git rebase -i instead of git-squash skill
# which produced concatenated commit messages requiring amendment.
#
# UPDATED: 2026-01-08 to also detect non-interactive rebase and remind about
# git-rebase skill (backup-verify-cleanup pattern).
#
# PREVENTION HIERARCHY:
# - Original: Documentation in CLAUDE.md (ignored)
# - Improvement 1: PostToolUse detection (warns after damage)
# - Improvement 2: PreToolUse reminder (warns BEFORE execution)
#
# This hook reminds the agent to use:
# - git-squash skill for interactive rebase (squashing)
# - git-rebase skill for non-interactive rebase (rebasing on branch)

set -eo pipefail
trap 'echo "ERROR in remind-git-squash-skill.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Read JSON context from stdin
INPUT=$(cat)

# Only process Bash tool
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')
if [[ "$TOOL_NAME" != "Bash" ]]; then
	exit 0
fi

# Get the command
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# Rate limit function
should_remind() {
	local skill_name="$1"
	local RATE_LIMIT_FILE="/tmp/git-${skill_name}-reminder-$(date +%Y%m%d%H)"
	local RATE_LIMIT_MINUTE=$(($(date +%M) / 10))
	local RATE_LIMIT_KEY="${RATE_LIMIT_FILE}-${RATE_LIMIT_MINUTE}"

	if [[ -f "$RATE_LIMIT_KEY" ]]; then
		return 1  # Already reminded recently
	fi
	touch "$RATE_LIMIT_KEY"
	return 0
}

# Detect interactive rebase commands (squashing)
if [[ "$COMMAND" =~ git[[:space:]]+(rebase[[:space:]]+-i|rebase[[:space:]]+--interactive) ]]; then
	if should_remind "squash"; then
		echo "" >&2
		echo "================================================================" >&2
		echo "  REMINDER: git-squash skill for squashing operations" >&2
		echo "================================================================" >&2
		echo "" >&2
		echo "You're using 'git rebase -i'. If your goal is to SQUASH commits:" >&2
		echo "" >&2
		echo "  - Ad-hoc squash produces CONCATENATED messages (all messages joined)" >&2
		echo "  - git-squash skill enforces UNIFIED messages (describes final result)" >&2
		echo "" >&2
		echo "RECOMMENDATION:" >&2
		echo "  Skill: git-squash" >&2
		echo "" >&2
		echo "If you're reordering commits (not squashing), proceed with rebase." >&2
		echo "================================================================" >&2
		echo "" >&2
	fi
# Detect non-interactive rebase commands (rebasing on branch)
# Matches: git rebase main, git rebase origin/main, git rebase --onto X Y
# Excludes: git rebase -i, git rebase --interactive, git rebase --continue/--abort/--skip
elif [[ "$COMMAND" =~ git[[:space:]]+rebase[[:space:]]+ ]] && \
     [[ ! "$COMMAND" =~ git[[:space:]]+rebase[[:space:]]+(-i|--interactive) ]] && \
     [[ ! "$COMMAND" =~ git[[:space:]]+rebase[[:space:]]+--(continue|abort|skip) ]]; then
	if should_remind "rebase"; then
		echo "" >&2
		echo "================================================================" >&2
		echo "  REMINDER: git-rebase skill for rebasing operations" >&2
		echo "================================================================" >&2
		echo "" >&2
		echo "You're using 'git rebase'. The git-rebase skill provides:" >&2
		echo "" >&2
		echo "  - Automatic backup branch before rebase" >&2
		echo "  - Conflict handling with verification" >&2
		echo "  - Cleanup of backup after successful rebase" >&2
		echo "" >&2
		echo "RECOMMENDATION:" >&2
		echo "  Skill: git-rebase" >&2
		echo "" >&2
		echo "================================================================" >&2
		echo "" >&2
	fi
fi

# Allow command to proceed (reminder only, not blocking)
exit 0
