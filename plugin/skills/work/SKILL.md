---
description: Work on issues (approval required unless trust=high; auto-continues to next task when trust >= medium)
argument-hint: "[version | taskId | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
---

# Work: Thin Orchestrator

Execute issues with worktree isolation, subagent orchestration, and quality gates.

**Architecture:** Main agent orchestrates 4 phase subagents. Each phase runs in isolation with
its own context, keeping main agent context minimal (~5-10K tokens).

## Arguments

| Format | Example | Behavior |
|--------|---------|----------|
| Empty | `/cat:work` | Work on next available task |
| Version | `/cat:work 2.1` | Work on tasks in version 2.1 |
| Task ID | `/cat:work 2.1-migrate-api` | Work on specific task |
| Filter | `/cat:work skip compression` | Filter task selection (natural language) |

**Flags:**
- `--override-gate` - Skip approval gate (use with caution)

**Filter examples:**
- `skip compression tasks` - exclude tasks with "compression" in name
- `only migration` - only tasks with "migration" in name

Filters are interpreted by the prepare phase subagent using natural language understanding.

## Progress Output

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-progress-banner.sh $ARGUMENTS --project-dir "${CLAUDE_PROJECT_DIR}" --session-id "${CLAUDE_SESSION_ID}"`

---

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-work-boxes.sh`

## Configuration

Read once at start:

```bash
TRUST=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
VERIFY=$(jq -r '.verify // "changed"' .claude/cat/cat-config.json)
AUTO_REMOVE=$(jq -r '.autoRemoveWorktrees // true' .claude/cat/cat-config.json)
```

## Phase 1: Prepare

Delegate to work-prepare subagent:

```
Task tool:
  description: "Prepare: find task, create worktree"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute the work-prepare phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
    ARGUMENTS: $ARGUMENTS
    TRUST_LEVEL: ${TRUST}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-prepare/SKILL.md

    Return JSON per the output contract.
```

**Handle result:**

| Status | Action |
|--------|--------|
| READY | Display progress banner, continue to Phase 2 |
| NO_TASKS | Display NO_EXECUTABLE_ISSUES box, stop |
| LOCKED | Display lock message, try next task |
| OVERSIZED | Invoke /cat:decompose-issue, then retry |
| ERROR | Display error, stop |

**Store for later phases:**
- `task_id`, `task_path`, `worktree_path`, `branch`, `base_branch`
- `estimated_tokens`

## Phase 2: Execute

Delegate to work-execute subagent:

```
Task tool:
  description: "Execute: implement task"
  subagent_type: "general-purpose"
  model: "sonnet"
  prompt: |
    Execute the work-execute phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    TASK_ID: ${task_id}
    TASK_PATH: ${task_path}
    WORKTREE_PATH: ${worktree_path}
    ESTIMATED_TOKENS: ${estimated_tokens}
    TRUST_LEVEL: ${TRUST}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-execute/SKILL.md

    Return JSON per the output contract.
```

**Handle result:**

| Status | Action |
|--------|--------|
| SUCCESS | Store metrics, continue to Phase 3 |
| PARTIAL | Warn user, continue to Phase 3 |
| FAILED | Display error, offer retry or abort |
| BLOCKED | Display blocker, stop |

**Token check:**
- If `compaction_events > 0`: Warn user, offer decomposition
- If `percent_of_context > 80`: Invoke learn-from-mistakes

**Store for later phases:**
- `commits`, `files_changed`, `tokens_used`

## Phase 3: Review

**Skip if:** `VERIFY == "none"` or `TRUST == "high"`

Delegate to work-review subagent:

```
Task tool:
  description: "Review: stakeholder quality check"
  subagent_type: "general-purpose"
  model: "sonnet"
  prompt: |
    Execute the work-review phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    TASK_ID: ${task_id}
    TASK_PATH: ${task_path}
    WORKTREE_PATH: ${worktree_path}
    TRUST_LEVEL: ${TRUST}
    VERIFY_LEVEL: ${VERIFY}
    EXECUTION_RESULT: ${execution_result_json}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-review/SKILL.md

    Return JSON per the output contract.
```

**Handle result:**

| Status | Action |
|--------|--------|
| APPROVED | Continue to user approval gate |
| CONCERNS | Note concerns, continue to user approval gate |
| REJECTED | If medium trust: auto-loop to fix. If low trust: ask user |

**User Approval Gate (if trust != high):**

Use AskUserQuestion:
- header: "Approval"
- question: "Ready to merge {task_id}?"
- options:
  - "Approve and merge"
  - "Request changes" (provide feedback)
  - "Abort"

## Phase 4: Merge

Delegate to work-merge subagent:

```
Task tool:
  description: "Merge: squash, merge, cleanup"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute the work-merge phase skill.

    SESSION_ID: ${CLAUDE_SESSION_ID}
    TASK_ID: ${task_id}
    TASK_PATH: ${task_path}
    WORKTREE_PATH: ${worktree_path}
    BRANCH: ${branch}
    BASE_BRANCH: ${base_branch}
    COMMITS: ${commits_json}
    AUTO_REMOVE_WORKTREES: ${AUTO_REMOVE}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-merge/SKILL.md

    Return JSON per the output contract.
```

**Handle result:**

| Status | Action |
|--------|--------|
| MERGED | Display success, check for next task |
| CONFLICT | Display conflicting files, ask user for resolution |
| ERROR | Display error, attempt manual cleanup |

## Next Task

After successful merge:

1. Check if more tasks in scope (based on original arguments)
2. If trust >= medium: Auto-continue after 3s delay
3. If trust == low: Display next task, wait for user

Use appropriate box from pre-rendered output.

## Error Handling

If any phase subagent fails unexpectedly:

1. Capture error message
2. Attempt lock release: `issue-lock.sh release ...`
3. Display error to user
4. Offer: Retry, Abort, or Manual cleanup

## Success Criteria

- [ ] Phase subagent spawned for each phase
- [ ] Results collected and parsed as JSON
- [ ] User approval gate respected (unless trust=high)
- [ ] Lock released on completion or error
- [ ] Progress banners displayed at phase transitions
