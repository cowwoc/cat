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

Values are pre-loaded by handler preprocessing (shown above in CONFIGURATION section).
Use these values: TRUST, VERIFY, AUTO_REMOVE.

## Phase 1: Prepare

Delegate to work-prepare subagent using the Task tool with these JSON parameters:

- **description:** `"Prepare: find task, create worktree"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values)

Prompt for the subagent:

> Execute the work-prepare phase skill.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
> ARGUMENTS: $ARGUMENTS
> TRUST_LEVEL: ${TRUST}
>
> Load and follow: ${CLAUDE_PLUGIN_ROOT}/skills/work-prepare/SKILL.md
>
> Your FINAL message must be ONLY the JSON result object — no surrounding text, no explanation.
> This is critical because the parent agent parses your response as JSON.

**Handle result:**

| Status | Action |
|--------|--------|
| READY | Display progress banner, continue to Phase 2 |
| READY + `potentially_complete: true` | Ask user to verify (see below), then skip or continue |
| NO_TASKS | Display extended diagnostics (see below), stop |
| LOCKED | Display lock message, try next task |
| OVERSIZED | Invoke /cat:decompose-issue, then retry |
| ERROR | Display error, stop |
| No JSON / empty | Subagent failed to produce output - display error, release lock if acquired, stop |

**Parsing the result (M448):** The subagent's final message is returned as text. Extract the JSON
object from it — look for `{` through the matching `}`. If the result contains surrounding text,
ignore the text and parse just the JSON block.

**No-result handling (M441, M444):** If the prepare subagent returns no parseable JSON (empty output,
turn limit exceeded, or no JSON block found), treat as ERROR and STOP. Do NOT attempt to reconstruct
the result by listing worktrees or reading lock files. Artifacts from other sessions may exist and will
mislead you into working on the wrong task.

Display: "Prepare phase failed to return a result. The subagent may have exceeded its turn budget."
Then STOP. Do not proceed to work-with-issue.

**NO_TASKS Guidance (M396, M441):**

When prepare phase returns NO_TASKS, use extended failure fields to provide specific diagnostics:

1. If `blocked_tasks` is non-empty: list each blocked task and what it's blocked by
2. If `locked_tasks` is non-empty: suggest `/cat:cleanup` to clear stale locks
3. If `closed_count == total_count`: all tasks done - suggest `/cat:add` for new work
4. Otherwise: suggest `/cat:status` to see available tasks

Fallback to `message` field if extended fields are absent:

| Message contains | Suggested action |
|------------------|------------------|
| "locked" | Suggest `/cat:cleanup` to clear stale locks, or wait for other sessions |
| "blocked" | Suggest resolving blocking dependencies first |
| "closed" | All tasks done - suggest `/cat:status` to verify or `/cat:add` for new work |
| other | Suggest `/cat:status` to see available tasks |

**NEVER suggest working on a previous version** - if user is on v2.1, suggesting v2.0 is unhelpful.

**Potentially Complete Handling (M443):**

When prepare returns READY with `potentially_complete: true`, work may already exist on the base branch
with STATE.md not reflecting completion (e.g., stale merge overwrote status).

1. Display the suspicious commits from `suspicious_commits` field
2. Use AskUserQuestion to ask user whether the issue is already complete:
   - **"Already complete"** - Fix STATE.md to closed, release lock, clean up worktree, select next task
   - **"Not complete, continue"** - Proceed to Phase 2 normally
3. Do NOT proceed to Phase 2 without user confirmation

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

After successful merge, generate the Issue Complete box and discover the next task using a single
script call. This consolidates lock release, task discovery, and box rendering into one step.

**Generate the Issue Complete box:**

```bash
python3 "${CLAUDE_PLUGIN_ROOT}/scripts/get-next-task-box.py" \
  --completed-issue "${issue_id}" \
  --base-branch "${base_branch}" \
  --session-id "${CLAUDE_SESSION_ID}" \
  --project-dir "${CLAUDE_PROJECT_DIR}"
```

If the original ARGUMENTS contained a filter (e.g., "skip compression"), add `--exclude-pattern "compress*"`.

**Copy the script output VERBATIM.** Do NOT attempt to modify, reformat, or reconstruct the box.

**Parse the box to determine next task status:**
- If box contains "**Next:**" followed by an issue ID → next task found
- If box contains "Scope Complete" → no next task

**Route based on trust level:**

| Condition | Action |
|-----------|--------|
| No next task | Scope complete - stop |
| Next task + trust == "low" | Display box, stop for user |
| Next task + trust >= "medium" | Display box, auto-continue to `/cat:work ${next_issue_id}` |

**Low-trust stop message:**

If trust == "low" and next task found, display after the box:

```
Ready to continue to next task. Use /cat:work to continue, or /cat:status to review remaining tasks.
```

**Auto-continue (trust >= medium):**

Invoke the Skill tool again with `/cat:work ${next_issue_id}` to continue to the next task.
No delay needed - the work skill handles its own orchestration.

## Error Handling

If any phase subagent fails unexpectedly:

1. Capture error message
2. Release the lock using positional arguments:
   ```bash
   "${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release <project-dir> <issue-id> <session-id>
   # Example: issue-lock.sh release /workspace 2.1-compress-batch-1 $SESSION_ID
   ```
3. Display error to user
4. Offer: Retry, Abort, or Manual cleanup

## Success Criteria

- [ ] Phase subagent spawned for each phase
- [ ] Results collected and parsed as JSON
- [ ] User approval gate respected (unless trust=high)
- [ ] Lock released on completion or error
- [ ] Progress banners displayed at phase transitions
