#!/bin/bash
# PostToolUse hook: Detect concatenated commit messages after git operations
#
# ADDED: 2025-12-21 after using ad-hoc git rebase -i instead of git-squash skill
# PREVENTS: Concatenated commit messages from ad-hoc squashing
#
# INEFFECTIVE FIX IMPROVEMENT:
# - Original fix (a11a529, Dec 15): Documentation in CLAUDE.md
# - Gap: Documentation alone didn't prevent violation
# - Improvement: Add enforcement hook (config → hook in prevention hierarchy)

set -eo pipefail
trap 'echo "ERROR in detect-concatenated-commit-message.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Read JSON context from stdin (Claude Code passes hook context via stdin)
INPUT=$(cat)

# Only process Bash tool results
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')
if [[ "$TOOL_NAME" != "Bash" ]]; then
	exit 0
fi

# Only process git commands
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
if [[ ! "$COMMAND" =~ git ]]; then
	exit 0
fi

# Check if command created a new commit
# (rebase, commit, merge commands can create commits)
if [[ ! "$COMMAND" =~ (rebase|commit|merge) ]]; then
	exit 0
fi

# Get the most recent commit message
COMMIT_MSG=$(git log -1 --format=%B 2>/dev/null || echo "")

if [[ -z "$COMMIT_MSG" ]]; then
	# No commits yet or not in a git repository
	exit 0
fi

# Detect concatenated message pattern: Multiple "Co-Authored-By" sections
# A proper unified message has ONE Co-Authored-By at the end
# A concatenated message has MULTIPLE Co-Authored-By sections (one per original commit)
CO_AUTHORED_COUNT=$(echo "$COMMIT_MSG" | grep -c "^Co-Authored-By:" || true)

if [[ "$CO_AUTHORED_COUNT" -gt 1 ]]; then
	COMMIT_HASH=$(git log -1 --format=%h)

	echo "" >&2
	echo "⚠️  CONCATENATED COMMIT MESSAGE DETECTED" >&2
	echo "" >&2
	echo "Commit $COMMIT_HASH has $CO_AUTHORED_COUNT 'Co-Authored-By' lines." >&2
	echo "This indicates a concatenated message from ad-hoc squashing." >&2
	echo "" >&2
	echo "CLAUDE.md § Always Use git-squash Skill:" >&2
	echo "  Ad-hoc 'git rebase -i' with squash produces concatenated messages." >&2
	echo "  Squashed commits need UNIFIED messages describing the final result." >&2
	echo "" >&2
	echo "RECOMMENDATION:" >&2
	echo "  Use the git-squash skill which enforces writing a new unified message." >&2
	echo "  The skill prompts you to describe what the final code DOES." >&2
	echo "" >&2
	echo "TO FIX THIS COMMIT:" >&2
	echo "  1. git reset --soft HEAD~1  # Unstage commit" >&2
	echo "  2. git commit  # Write unified message" >&2
	echo "  Or use: git commit --amend  # Rewrite message" >&2
	echo "" >&2

	# WARNING mode - don't block, just warn
	# Could change to exit 2 for blocking mode if violations continue
fi

exit 0
