#!/bin/bash
# Hook: warn-base-branch-edit.sh
# Type: PreToolUse (Edit)
# Purpose: Warn when editing source files directly on base branch (M220)
#
# CAT workflow requires task work to happen in isolated worktrees.
# This hook warns when editing source files on a base branch like v2.0, main, etc.
#
# Allowed without warning:
# - .claude/ directory (orchestration files)
# - STATE.md, PLAN.md files
# - retrospectives/ directory
# - When in a task worktree (has cat-base file)

set -euo pipefail

# Source standard hook libraries
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize hook (reads JSON from stdin)
if ! init_hook; then
    echo '{}'
    exit 0
fi

# Only check Edit tool calls
if [[ "$TOOL_NAME" != "Edit" ]]; then
    echo '{}'
    exit 0
fi

# Extract file_path from tool_input
FILE_PATH=""
if $_JQ_AVAILABLE; then
    FILE_PATH=$(echo "$HOOK_JSON" | jq -r '.tool_input.file_path // empty' 2>/dev/null) || FILE_PATH=""
fi

if [[ -z "$FILE_PATH" ]]; then
    echo '{}'
    exit 0
fi

# Check if we're in a task worktree (has cat-base file)
GIT_DIR=$(git rev-parse --git-dir 2>/dev/null) || GIT_DIR=""
if [[ -n "$GIT_DIR" ]] && [[ -f "$GIT_DIR/cat-base" ]]; then
    # In a task worktree - all edits allowed
    echo '{}'
    exit 0
fi

# Allowed paths (CAT orchestration, not task implementation)
ALLOWED_PATTERNS=(
    ".claude/"
    "STATE.md"
    "PLAN.md"
    "CHANGELOG.md"
    "ROADMAP.md"
    "retrospectives/"
    "mistakes.json"
)

for pattern in "${ALLOWED_PATTERNS[@]}"; do
    if [[ "$FILE_PATH" == *"$pattern"* ]]; then
        echo '{}'
        exit 0
    fi
done

# Get current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || CURRENT_BRANCH=""

# Check if this looks like a base branch (v*.*, main, master, develop)
if [[ "$CURRENT_BRANCH" =~ ^v[0-9]+\.[0-9]+$ ]] || \
   [[ "$CURRENT_BRANCH" == "main" ]] || \
   [[ "$CURRENT_BRANCH" == "master" ]] || \
   [[ "$CURRENT_BRANCH" == "develop" ]]; then
    output_hook_warning "PreToolUse" "⚠️ BASE BRANCH EDIT DETECTED (M220)

Branch: $CURRENT_BRANCH
File: $FILE_PATH

You are editing source files directly on a base branch.
CAT workflow requires task work to happen in isolated worktrees.

If working on a task:
1. Run /cat:work to create a worktree
2. Or manually: git worktree add .worktrees/task-name -b task-branch

If this is intentional infrastructure work (not a task), proceed.

Proceeding with edit (warning only, not blocked)."
fi

echo '{}'
exit 0
