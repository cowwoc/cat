#!/bin/bash
# Hook: enforce-workflow-completion
# Trigger: PreToolUse:Edit
# Purpose: Prevent marking task as completed without completing workflow phases
#
# M217: Completion bias led to skipping stakeholder_review and approval_gate phases.
# This hook detects attempts to set task status to "completed" and warns if workflow
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

# Check if setting status to completed
if [[ ! "$NEW_STRING" =~ Status.*completed ]] && [[ ! "$NEW_STRING" =~ status.*completed ]]; then
    echo '{}'; exit 0
fi

# Extract task directory from path
TASK_DIR=$(dirname "$FILE_PATH")
TASK_NAME=$(basename "$TASK_DIR")

# Build warning message - use "approve" not "allow" per hook schema
cat << EOF
{
  "decision": "approve",
  "systemMessage": "⚠️ WORKFLOW COMPLETION CHECK (M217)\n\nYou are marking task '$TASK_NAME' as completed.\n\nBefore completing a task via /cat:work, verify ALL phases are done:\n\n1. **Setup** ✓ (worktree created, task loaded)\n2. **Implementation** ✓ (code written, tests pass, committed)\n3. **Reviewing** - Did you complete:\n   - [ ] stakeholder_review (run parallel stakeholder reviews)\n   - [ ] approval_gate (present changes for USER approval)\n4. **Merging** - Did you:\n   - [ ] Ask user if they want to merge\n   - [ ] Run squash_commits, merge, cleanup\n\nIf you skipped phases 3-4, STOP and return to the /cat:work workflow.\nCommitting code does NOT complete the task - user review and merge are required.\n\nIf this is a legitimate completion (all phases done), proceed with the edit."
}
EOF
