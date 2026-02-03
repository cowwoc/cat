---
description: Batch executor for work phases - executes all phases in single subagent to minimize visible output
user-invocable: false
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
---

# Work Batch Executor

Execute all work phases (execute, review, merge) in a single subagent context. This design
minimizes visible output to the user by keeping intermediate tool calls within the subagent
(invisible to parent conversation).

**Architecture:** Main agent shows Banner1 (Preparing), spawns this batch executor which
internally spawns phase subagents. User sees clean output: Banner1 -> (single spawn) ->
Banner2/3/4 as text output -> Result.

## Input

The work-with-task skill provides via JSON arguments:

```json
{
  "task_id": "2.1-task-name",
  "task_path": "/workspace/.claude/cat/issues/v2/v2.1/task-name",
  "worktree_path": "/workspace/.worktrees/2.1-task-name",
  "branch": "2.1-task-name",
  "base_branch": "v2.1",
  "estimated_tokens": 45000,
  "trust": "medium",
  "verify": "changed",
  "auto_remove": true,
  "has_existing_work": false,
  "existing_commits": 0
}
```

The handler provides pre-rendered banners in **SCRIPT OUTPUT PROGRESS BANNERS** section:
- banner_executing: Banner with (` ` pattern)
- banner_reviewing: Banner with (` ` ` pattern)
- banner_merging: Banner with (` ` ` ` pattern)

## Output Contract

Return JSON on success:

```json
{
  "status": "SUCCESS|FAILED",
  "task_id": "2.1-task-name",
  "commits": [...],
  "files_changed": 5,
  "tokens_used": 65000,
  "merged": true,
  "approval_required": false
}
```

Return JSON when approval gate reached (trust != high):

```json
{
  "status": "APPROVAL_REQUIRED",
  "task_id": "2.1-task-name",
  "execution_result": {...},
  "review_result": {...},
  "goal": "Task goal text from PLAN.md"
}
```

Return JSON on failure:

```json
{
  "status": "FAILED",
  "phase": "execute|review|merge",
  "message": "Human-readable explanation",
  "partial_work": {...}
}
```

## Process

### Step 1: Parse Configuration

Extract all parameters from arguments JSON.

```bash
TASK_ID=$(echo "$ARGUMENTS" | jq -r '.task_id')
TASK_PATH=$(echo "$ARGUMENTS" | jq -r '.task_path')
WORKTREE_PATH=$(echo "$ARGUMENTS" | jq -r '.worktree_path')
BRANCH=$(echo "$ARGUMENTS" | jq -r '.branch')
BASE_BRANCH=$(echo "$ARGUMENTS" | jq -r '.base_branch')
ESTIMATED_TOKENS=$(echo "$ARGUMENTS" | jq -r '.estimated_tokens')
TRUST=$(echo "$ARGUMENTS" | jq -r '.trust')
VERIFY=$(echo "$ARGUMENTS" | jq -r '.verify')
AUTO_REMOVE=$(echo "$ARGUMENTS" | jq -r '.auto_remove')
HAS_EXISTING_WORK=$(echo "$ARGUMENTS" | jq -r '.has_existing_work // false')
EXISTING_COMMITS=$(echo "$ARGUMENTS" | jq -r '.existing_commits // 0')
```

### Step 2: Execute Phase (unless existing work)

**Output the Executing banner** from SCRIPT OUTPUT PROGRESS BANNERS (visible to user).

If `has_existing_work == true`:
- Output: "Resuming task with existing work - skipping to review"
- Skip to Step 3

Otherwise, spawn work-execute subagent:

```
Task tool:
  description: "Execute: implement task"
  subagent_type: "general-purpose"
  model: "sonnet"
  prompt: |
    Execute the work-execute phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    TASK_ID: ${TASK_ID}
    TASK_PATH: ${TASK_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-execute/SKILL.md

    CRITICAL WORKING DIRECTORY: You MUST work in the worktree at ${WORKTREE_PATH}

    Return JSON per the output contract.
```

**Note (M388):** work-execute implements the task DIRECTLY - it does NOT spawn another subagent.
This keeps the chain at 2 levels (batch-executor → work-execute) while preserving work-execute's
task-type handling logic.

Handle execution result:
- SUCCESS: Store metrics, continue
- PARTIAL: Warn, continue
- FAILED: Return FAILED status immediately
- BLOCKED: Return FAILED with blocker info

### Step 3: Review Phase

**Output the Reviewing banner** from SCRIPT OUTPUT PROGRESS BANNERS (visible to user).

Skip if: `VERIFY == "none"` or `TRUST == "high"`

Spawn work-review subagent:

```
Task tool:
  description: "Review: stakeholder quality check"
  subagent_type: "general-purpose"
  model: "sonnet"
  prompt: |
    Execute the work-review phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    TASK_ID: ${TASK_ID}
    TASK_PATH: ${TASK_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    TRUST_LEVEL: ${TRUST}
    VERIFY_LEVEL: ${VERIFY}
    EXECUTION_RESULT: ${execution_result_json}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-review/SKILL.md

    Return JSON per the output contract.
```

Handle review result:
- APPROVED: Continue to approval gate
- CONCERNS: Note concerns, continue to approval gate
- REJECTED: If trust=medium auto-loop to fix, else return for user decision

### Step 4: Approval Gate (trust != high)

**If trust != high:**

Read task goal from `${TASK_PATH}/PLAN.md` (extract ## Goal section).

Return APPROVAL_REQUIRED status with execution and review results. The parent skill will
handle the AskUserQuestion interaction.

```json
{
  "status": "APPROVAL_REQUIRED",
  "task_id": "${TASK_ID}",
  "execution_result": {...},
  "review_result": {...},
  "goal": "extracted goal text"
}
```

**If trust == high:** Continue directly to Step 5.

### Step 5: Merge Phase

**Output the Merging banner** from SCRIPT OUTPUT PROGRESS BANNERS (visible to user).

Spawn work-merge subagent:

```
Task tool:
  description: "Merge: squash, merge, cleanup"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute the work-merge phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    TASK_ID: ${TASK_ID}
    TASK_PATH: ${TASK_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    BASE_BRANCH: ${BASE_BRANCH}
    COMMITS: ${commits_json}
    AUTO_REMOVE_WORKTREES: ${AUTO_REMOVE}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-merge/SKILL.md

    Return JSON per the output contract.
```

Handle merge result:
- MERGED: Continue to success
- CONFLICT: Return FAILED with conflict details
- ERROR: Return FAILED with error

### Step 6: Return Success

```json
{
  "status": "SUCCESS",
  "task_id": "${TASK_ID}",
  "commits": [...],
  "files_changed": N,
  "tokens_used": N,
  "merged": true,
  "approval_required": false
}
```

## Error Handling

If any phase fails:

1. Capture error message and phase name
2. Attempt lock release: `${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh release ...`
3. Return FAILED status with phase and error details

```json
{
  "status": "FAILED",
  "phase": "execute",
  "message": "Build failed: missing dependency",
  "partial_work": {
    "commits": [...],
    "files_changed": 3
  }
}
```

## Banner Output Format

When outputting banners, use the exact text from SCRIPT OUTPUT PROGRESS BANNERS.
Output as plain text (not in a tool call) so it's visible to the user.

Example output pattern:
```
**Executing phase** (` ` ` pattern):
```
[banner from SCRIPT OUTPUT]
```
```

## Context Efficiency

This skill is designed to run as a subagent. All internal phase spawns (work-execute,
work-review, work-merge) are invisible to the parent conversation. Only the banner
text outputs and final JSON result are visible.

**Chain Design (M388):** batch-executor → work-execute (2 levels). work-execute implements
directly without spawning a nested subagent. This keeps work-execute's task-type handling
logic while eliminating the 3-level chain that caused guidance loss.

This reduces visible output from:
- 4 Task spawns (prepare, execute, review, merge)
- 4 JSON parsing operations
- Multiple state transitions

To:
- 1 Task spawn (batch executor)
- Banner text outputs
- 1 final result
