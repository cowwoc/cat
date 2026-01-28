#!/bin/bash
# Hook: warn-base-branch-edit.sh
# Type: PreToolUse (Write|Edit)
# Purpose: Warn when editing source files directly (A003/M097/M220/M302)
#
# CAT workflow requires:
# 1. Task work happens in isolated worktrees (M220)
# 2. Main agent delegates source edits to subagents (A003/M097)
#
# This hook warns when editing source files outside proper workflow.
#
# Allowed without warning:
# - .claude/ directory (orchestration files)
# - STATE.md, PLAN.md, CHANGELOG.md, ROADMAP.md files
# - CLAUDE.md, PROJECT.md (project instructions)
# - retrospectives/ directory
# - mistakes.json, retrospectives.json
# - hooks/, skills/ directories
# - When in a task worktree editing orchestration files only

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

# Only check Edit and Write tool calls
if [[ "$TOOL_NAME" != "Edit" ]] && [[ "$TOOL_NAME" != "Write" ]]; then
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
IN_TASK_WORKTREE=false
if [[ -n "$GIT_DIR" ]] && [[ -f "$GIT_DIR/cat-base" ]]; then
    IN_TASK_WORKTREE=true
fi

# Allowed paths (CAT orchestration, not task implementation)
ALLOWED_PATTERNS=(
    ".claude/"
    "STATE.md"
    "PLAN.md"
    "CHANGELOG.md"
    "ROADMAP.md"
    "CLAUDE.md"
    "PROJECT.md"
    "retrospectives/"
    "mistakes.json"         # Legacy single file
    "mistakes-"             # Split files: mistakes-YYYY-MM.json
    "retrospectives.json"   # Legacy single file
    "retrospectives-"       # Split files: retrospectives-YYYY-MM.json
    "index.json"            # Retrospective index
    "hooks/"
    "skills/"
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
    echo '{}'
    exit 0
fi

# ESCALATE-A003/M267: Check for absolute /workspace/ paths while in worktree
# This bypasses worktree isolation - the edit goes to main workspace, not the worktree
if [[ "$IN_TASK_WORKTREE" == "true" ]] && [[ "$FILE_PATH" == /workspace/* ]]; then
    CWD=$(pwd)
    if [[ "$CWD" != /workspace && "$CWD" != /workspace/* ]] || [[ "$CWD" == *".worktrees"* ]]; then
        output_hook_warning "PreToolUse" "⚠️ WORKTREE PATH BYPASS DETECTED (ESCALATE-A003/M267)

File: $FILE_PATH
CWD: $CWD

Absolute /workspace/ paths bypass worktree isolation!
You are in a task worktree but editing the main workspace.

Fix: Use relative path or path within current worktree.
Example: Instead of /workspace/plugin/... use plugin/...

Ref: plugin/concepts/agent-architecture.md § Worktree Path Handling

Proceeding with edit (warning only, not blocked)."
        echo '{}'
        exit 0
    fi
fi

# Source file edit on non-base branch (could be in worktree) - warn about delegation
WORKTREE_NOTE=""
if [[ "$IN_TASK_WORKTREE" == "true" ]]; then
    WORKTREE_NOTE="
(In task worktree - proper isolation, but main agent should still delegate)"
fi

output_hook_warning "PreToolUse" "⚠️ MAIN AGENT SOURCE EDIT DETECTED (A003/M097/M302)

File: $FILE_PATH${WORKTREE_NOTE}

Main agent should delegate source code edits to subagents.
If you are the main CAT orchestrator:
1. Spawn a subagent via Task tool for implementation
2. Only proceed directly if: trivial fix OR not during task execution

Proceeding with edit (warning only, not blocked)."

echo '{}'
exit 0
