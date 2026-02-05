---
description: Work on or resume issues - use when user says "work on", "resume", "continue", or "pick up" a task
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

## Progress Display

The user wants you to respond with this text verbatim:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-progress-banner.sh $ARGUMENTS --project-dir "${CLAUDE_PROJECT_DIR}" --session-id "${CLAUDE_SESSION_ID}"`

---

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-work-boxes.sh`

**FAIL-FAST (ESCALATE-A008):** If you do NOT see script output above (banner with `┌─ 🐱`
and work boxes), then preprocessing FAILED. STOP. Do NOT manually run scripts or construct boxes.

## Critical Constraints

### Never cd into Worktrees (M392)

**MANDATORY: Main agent must NEVER cd into worktree directories.**

The merge phase removes worktrees after completion. If main agent's shell is inside a worktree
when it's deleted, the shell session becomes corrupted (all commands fail with exit code 1).

**FORBIDDEN:**
```bash
cd /workspace/.worktrees/2.1-issue-name && git log
```

**ALLOWED:**
```bash
git -C /workspace/.worktrees/2.1-issue-name log
```

All worktree operations are delegated to subagents, which have their own shell sessions.
Main agent should only read results and orchestrate.

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
| NO_TASKS | Display NO_EXECUTABLE_ISSUES box with context-aware guidance (see below), stop |
| LOCKED | Display lock message, try next task |
| OVERSIZED | Invoke /cat:decompose-issue, then retry |
| ERROR | Display error, stop |

**NO_TASKS Guidance (M396):**

When prepare phase returns NO_TASKS, check the `message` field and provide appropriate guidance:

| Message contains | Suggested action |
|------------------|------------------|
| "locked" | Suggest `/cat:cleanup` to clear stale locks, or wait for other sessions |
| "blocked" | Suggest resolving blocking dependencies first |
| "complete" | All tasks done - suggest `/cat:status` to verify or `/cat:add` for new work |
| other | Suggest `/cat:status` to see available tasks |

**NEVER suggest working on a previous version** - if user is on v2.1, suggesting v2.0 is unhelpful.

**Store phase 1 results:**
- `issue_id`, `issue_path`, `worktree_path`, `branch`, `base_branch`
- `estimated_tokens`

## Phase 2-4: Delegate to work-with-issue

After Phase 1 returns READY, delegate remaining phases to the work-with-issue skill.

This skill receives the issue ID and metadata, allowing its exclamation-backtick preprocessing to
render progress banners automatically for all 4 phases.

**Invoke the work-with-issue skill:**

Use the Skill tool to invoke `/cat:work-with-issue` with JSON arguments:

```json
{
  "issue_id": "${issue_id}",
  "issue_path": "${issue_path}",
  "worktree_path": "${worktree_path}",
  "branch": "${branch}",
  "base_branch": "${base_branch}",
  "estimated_tokens": ${estimated_tokens},
  "trust": "${TRUST}",
  "verify": "${VERIFY}",
  "auto_remove": ${AUTO_REMOVE}
}
```

The skill will:
1. Render progress banners via exclamation-backtick preprocessing (now has issue_id available)
2. Execute Phase 2 (implementation subagent)
3. Execute Phase 3 (stakeholder review) if verify != none
4. Execute Phase 4 (merge and cleanup)
5. Return execution summary

**Expected return format:**

```json
{
  "status": "SUCCESS|FAILED",
  "issue_id": "2.1-issue-name",
  "commits": [...],
  "files_changed": 5,
  "tokens_used": 65000,
  "merged": true
}
```

**Store final results:**
- `commits`, `files_changed`, `tokens_used`, `merged`

## Next Task

After successful merge:

1. Check if more tasks in scope (based on original arguments)
2. If trust >= medium: Auto-continue after 3s delay
3. If trust == low: Display next task, wait for user

**MANDATORY (M389): Generate Issue Complete box using script, NOT manual construction:**

```bash
python3 "${CLAUDE_PLUGIN_ROOT}/scripts/get-issue-complete-box.py" \
  --issue-name "$ISSUE_NAME" \
  --next-issue "$NEXT_ISSUE_NAME" \
  --next-goal "$NEXT_ISSUE_GOAL" \
  --base-branch "$BASE_BRANCH"
```

Copy script output VERBATIM. NEVER manually construct boxes - LLMs cannot accurately count display widths.

For scope-complete (no more tasks): Use **SCOPE_COMPLETE** from script output Work Boxes.

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
