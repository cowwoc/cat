<execution_context>

@${CLAUDE_PLUGIN_ROOT}/concepts/version-paths.md

</execution_context>

# Workflow: Execute Task

## Overview

Core task execution workflow for CAT. Orchestrates subagent execution across four phases:
Prepare, Execute, Review, and Merge.

**Full phase details are in phase files** - this document covers orchestration patterns only.

## Prerequisites

- Task exists with STATE.md, PLAN.md
- All task dependencies completed
- Main agent in orchestration mode
- **Task lock can be acquired** (not locked by another session)

## Subagent Batching Standards

**Hide tool calls by delegating batched operations to subagents.**

Subagent internal tool calls are invisible to the parent conversation. Instead of 20+ visible
Read/Bash calls, users see 3-5 Task tool invocations with clean output.

**Phase batches:**

| Batch | Subagent | Operations |
|-------|----------|------------|
| Preparation | Exploration | Validate, analyze, create worktree |
| Discovery | Exploration | Search codebase, check duplicates |
| Planning | Plan | Make decisions, create spec |
| Implementation | general-purpose | Execute spec, commit |
| Review | general-purpose | Orchestrate reviewers |
| Finalization | Main agent (direct) | Merge, cleanup, update state |

**Output pattern:**
```
◆ {Phase}...
[Task tool - single collapsed block]
✓ {Result summary}
```

## CRITICAL: Worktree Isolation (M101)

**ALL task implementation work MUST happen in the task worktree, NEVER in `/workspace` main.**

```
/workspace/                    ← MAIN WORKTREE - READ-ONLY during task execution
├── .worktrees/
│   └── 0.5-task-name/        ← TASK WORKTREE - All edits happen here
│       └── parser/src/...
└── parser/src/...            ← NEVER edit these files during task execution
```

**Rules:**
1. After creating worktree, immediately `cd` to it and verify with `pwd`
2. All file edits, git commits, and builds happen in the task worktree
3. Return to `/workspace` ONLY for final merge and cleanup
4. If confused about location, run `pwd` and `git branch --show-current`

**Why:** Multiple parallel tasks create separate worktrees. Editing main worktree:
- Corrupts other parallel tasks
- Creates merge conflicts
- Makes rollback impossible

## Task Discovery (M282)

**MANDATORY: Use get-available-issues.sh script FIRST. FAIL-FAST if script fails.**

Task discovery uses the dedicated script. The script uses **self-discovery** to find paths
(via `git rev-parse` and `BASH_SOURCE`), so no environment variables need to be set.

**Session ID:** Extract from the session context injected by hooks (look for "Session ID: ..." in
system-reminders from SessionStart).

```bash
# Run task discovery - script auto-discovers project path
# SESSION_ID from context (injected by echo-session-id.sh hook)
# Path: plugin/scripts/ in development, or use CLAUDE_PLUGIN_ROOT in installed context
RESULT=$(plugin/scripts/get-available-issues.sh --session-id "${SESSION_ID}")

if echo "$RESULT" | jq -e '.status == "found"' > /dev/null 2>&1; then
  ISSUE_ID=$(echo "$RESULT" | jq -r '.issue_id')
  ISSUE_PATH=$(echo "$RESULT" | jq -r '.issue_path')
else
  echo "$RESULT" | jq -r '.message // .status'
  # FAIL-FAST: No executable tasks or script error - STOP here
fi
```

**FAIL-FAST means NO FALLBACKS:**
- If script returns error → STOP and report the error
- If no tasks found → STOP and report "no executable tasks"
- NEVER fall back to manual Glob/Read/Bash exploration

**Anti-pattern (M282):** Manual file exploration as workaround for script failures.
If the script fails, the correct fix is to diagnose and fix the script, not to work around it.

## Lock Management (M097)

**MANDATORY: Lock Check Before Proceeding**

Before validating a task as executable, attempt to acquire its lock:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" acquire "${CLAUDE_PROJECT_DIR}" "$TASK_ID" "${CLAUDE_SESSION_ID}")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  echo "⏸️ Task $TASK_ID is locked by another session"
  # Skip this task, try next candidate
fi
```

This prevents:
- Offering tasks that another Claude instance is executing
- Wasted exploration/planning on locked tasks
- Confusion about task availability

## Minor Version Dependency Rules

| Scenario | Dependency |
|----------|------------|
| First minor of first major (v0.0) | None - always executable |
| Subsequent minors (e.g., v0.5) | Previous minor must be complete (v0.4) |
| First minor of new major (e.g., v1.0) | Last minor of previous major must be complete |

A minor is complete when all its tasks have `status: completed`.

## Error Recovery

### Subagent Failure
1. Subagent returns error status
2. Main agent logs failure
3. Attempt resolution or escalate

### Merge Conflict
1. Identify conflicting files
2. Attempt automatic resolution
3. Escalate with conflict details if unresolved

### Session Interruption
1. STATE.md preserves progress
2. Worktree may have partial work
3. Resume resumes from last state

## Parallel Execution

For independent tasks:
```
Main Agent
    |
    +---> Subagent A (task-1)
    +---> Subagent B (task-2)
    +---> Subagent C (task-3)
    |
    v
Process completions as they arrive
```

## Why Finalization Uses Direct Execution

**Finalization phase uses direct execution instead of subagent batching.**

Unlike Exploration, Planning, and Implementation phases:

1. **Happens AFTER user approval** - User has already reviewed and approved all changes
2. **Minimal tool calls** - Only 3-5 operations (merge, cleanup, state updates) vs 20+ in exploration
3. **Low user benefit from hiding** - No noise to hide; operations are already approved
4. **Better error handling** - Merge conflicts or cleanup failures should surface immediately
5. **Simplicity** - Direct execution is simpler than subagent overhead for post-approval cleanup

**Result:** Users see straightforward cleanup steps after approval.
