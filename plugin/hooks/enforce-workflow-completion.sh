#!/bin/bash
# Hook: enforce-workflow-completion
# Trigger: PreToolUse:Edit
# Purpose: Prevent marking task as closed without completing workflow phases
#
# M217: Completion bias led to skipping stakeholder_review and approval_gate phases.
# This hook detects attempts to set task status to "closed" and warns if workflow
# phases appear incomplete.

set -euo pipefail

HOOK_DATA="${1:-}"
if [[ -z "$HOOK_DATA" ]]; then
    echo '{}'; exit 0
fi

# Extract file path and new content from hook data
FILE_PATH=$(echo "$HOOK_DATA" | jq -r '.tool_input.file_path // ""')
NEW_STRING=$(echo "$HOOK_DATA" | jq -r '.tool_input.new_string // ""')

# Only check STATE.md files in task directories
if [[ ! "$FILE_PATH" =~ \.claude/cat/v[0-9]+/v[0-9]+\.[0-9]+/[^/]+/STATE\.md$ ]]; then
    echo '{}'; exit 0
fi

# Check if setting status to closed
if [[ ! "$NEW_STRING" =~ Status.*closed ]] && [[ ! "$NEW_STRING" =~ status.*closed ]]; then
    echo '{}'; exit 0
fi

# Extract task directory from path
TASK_DIR=$(dirname "$FILE_PATH")
TASK_NAME=$(basename "$TASK_DIR")

# Build warning message - use "approve" not "allow" per hook schema
# Use jq to properly escape the multi-line message as JSON
MESSAGE=$(cat << 'MSGEOF'
⚠️ WORKFLOW COMPLETION CHECK (M217)

You are marking task '$TASK_NAME' as closed.

Before completing a task via /cat:work, verify ALL phases are done:

1. **Setup** ✓ (worktree created, task loaded)
2. **Implementation** ✓ (code written, tests pass, committed)
3. **Reviewing** - Did you complete:
   - [ ] stakeholder_review (run parallel stakeholder reviews)
   - [ ] approval_gate (present changes for USER approval)
4. **Merging** - Did you:
   - [ ] Ask user if they want to merge
   - [ ] Run squash_commits, merge, cleanup

If you skipped phases 3-4, STOP and return to the /cat:work workflow.
Committing code does NOT complete the task - user review and merge are required.

If this is a legitimate completion (all phases done), proceed with the edit.
MSGEOF
)

# Substitute task name and output as JSON
MESSAGE="${MESSAGE//\$TASK_NAME/$TASK_NAME}"
jq -n --arg msg "$MESSAGE" '{"decision": "approve", "systemMessage": $msg}'
