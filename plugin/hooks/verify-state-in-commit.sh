#!/bin/bash
# Hook: verify-state-in-commit.sh
# Trigger: PreToolUse for Bash
# Purpose: Ensure STATE.md is included in implementation commits (M076/M077 prevention)
# Return codes: 0=allow, 1=soft error, 2=block operation
#
# POSITIVE VERIFICATION:
#   Instead of warning "don't forget STATE.md", we verify it IS included
#   This catches the error at commit time, not after the fact
#
# See: Learning M076, M077 - STATE.md not in implementation commit

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"
source "${SCRIPT_DIR}/lib/json-output.sh"

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

# Skip if not a git commit command
if ! echo "$COMMAND" | grep -qE "(^|;|&&|\|)\s*git\s+commit"; then
    echo '{}'
    exit 0
fi

# Skip --amend commits (user is fixing, not initial commit)
if echo "$COMMAND" | grep -qE "\-\-amend"; then
    echo '{}'
    exit 0
fi

# Only check bugfix: and feature: commits (implementation commits)
if ! echo "$COMMAND" | grep -qE '\-m\s+.*\b(bugfix|feature):'; then
    echo '{}'
    exit 0
fi

# Check if we're in a task worktree (has .claude/cat/issues/v* structure)
TASK_STATE_FILE=""
if [[ -d ".claude/cat" ]]; then
    # Find STATE.md relative to current directory
    TASK_STATE_FILE=$(find .claude/cat -name "STATE.md" -path "*/task/*/STATE.md" 2>/dev/null | head -1)
fi

# If no task STATE.md found, this isn't a CAT task context - skip
if [[ -z "$TASK_STATE_FILE" ]]; then
    echo '{}'
    exit 0
fi

# Check if STATE.md is staged for commit
if ! git diff --cached --name-only 2>/dev/null | grep -q "STATE.md"; then
    output_hook_block "
**BLOCKED: Missing STATE.md in implementation commit (M076/M077)**

Implementation commits (bugfix:, feature:) MUST include the task STATE.md update.

**Found task STATE.md at:** $TASK_STATE_FILE

**To fix:**
1. Update STATE.md status to 'completed' and progress to '100%'
2. Stage it: git add $TASK_STATE_FILE
3. Then commit

**Why this matters:**
- STATE.md tracks task completion for CAT orchestration
- Including it in the implementation commit maintains atomic task completion
- Separate commits lose the connection between code and task status
"
    exit 0
fi

# Check if STATE.md shows completed status
STATE_CONTENT=$(cat "$TASK_STATE_FILE" 2>/dev/null || echo "")
if ! echo "$STATE_CONTENT" | grep -qE '\*\*Status:\*\*\s*completed'; then
    output_hook_warning "PreToolUse" "
⚠️  STATE.md is staged but status is not 'completed'

**Current content preview:**
$(head -10 "$TASK_STATE_FILE")

**Ensure STATE.md has:**
- Status: completed
- Progress: 100%
"
    exit 0
fi

# All checks passed - STATE.md is staged and shows completed
echo '{}'
exit 0
