---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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
| Bare name | `/cat:work migrate-api` | Work on specific task by name only (resolves to current branch version) |
| Filter | `/cat:work skip compression` | Filter task selection (natural language) |

**Flags:**
- `--override-gate` - Skip approval gate (use with caution)

**Bare name format:** Issue name without version prefix, starting with a letter (e.g., `fix-work-prepare-issue-name-matching`). If multiple versions contain the same issue name, prefers the version matching the current git branch. Falls back to first match if no branch version match exists.

**Filter examples:**
- `skip compression tasks` - exclude tasks with "compression" in name
- `only migration` - only tasks with "migration" in name

Filters are interpreted by the prepare phase subagent using natural language understanding.

## Critical Constraints

### Worktree Directory Safety

**Work from inside the worktree.** After setup, `cd` into the worktree directory and work from there.

**CRITICAL SAFETY RULE:** Before removing a worktree (during merge/cleanup), ensure your shell is
NOT inside the worktree directory. If a shell is inside a directory when it's deleted, the shell
session becomes corrupted (all commands fail with exit code 1).

**Use `/cat:safe-rm`** before removing worktrees to verify no shells are inside the target directory.

## Configuration

Values are pre-loaded by handler preprocessing (shown above in CONFIGURATION section).
Use these values: TRUST, VERIFY, AUTO_REMOVE.

!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/progress-banner" --phase preparing`

## Phase 1: Prepare

Execute the deterministic preparation script directly (no subagent needed).

**Call the prepare script:**

Run `python3 "${CLAUDE_PLUGIN_ROOT}/scripts/work-prepare.py" --arguments "${ARGUMENTS}"` and parse the JSON output from stdout.

**Handle result:**

| Status | Action |
|--------|--------|
| READY | Continue to Phase 2 |
| READY + `potentially_complete: true` | Ask user to verify (see below), then skip or continue |
| NO_TASKS | Display extended diagnostics (see below), stop |
| LOCKED | Display lock message, try next task |
| OVERSIZED | Invoke /cat:decompose-issue, then retry |
| ERROR | Display error, stop |
| No JSON / empty | Subagent failed to produce output - display error, release lock if acquired, stop |

**Parsing the result:** The script returns JSON to stdout. Parse it directly.

**No-result handling:** If the prepare script returns no parseable JSON (empty output
or malformed JSON), treat as ERROR and STOP. Do NOT attempt to reconstruct the result by listing
worktrees or reading lock files. Artifacts from other sessions may exist and will mislead you into
working on the wrong task.

Display: "Prepare phase failed to return a result. The script may have encountered an error."
Then STOP. Do not proceed to work-with-issue.

**NO_TASKS Guidance:**

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

**Potentially Complete Handling:**

When prepare returns READY with `potentially_complete: true`, work may already exist on the base branch
with STATE.md not reflecting completion (e.g., stale merge overwrote status).

1. Display the suspicious commits from `suspicious_commits` field
2. Use AskUserQuestion to ask user whether the issue is already complete:
   ```
   AskUserQuestion:
     header: "${issue_id}"
     question: "Is ${issue_id} already complete?"
     options:
       - "Already complete" (Fix STATE.md to closed, release lock, clean up worktree, select next task)
       - "Not complete, continue" (Proceed to Phase 2 normally)
   ```
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

After successful merge, invoke `/cat:work-complete` with positional arguments:

```
/cat:work-complete ${issue_id} ${base_branch}
```

Output the skill result verbatim.

**Parse the result to determine next task status:**
- If result contains "**Next:**" followed by an issue ID → next task found
- If result contains "Scope Complete" → no next task

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
2. Run `"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release "${CLAUDE_PROJECT_DIR}" "${issue_id}" "${CLAUDE_SESSION_ID}"` to release the lock.
3. Display error to user
4. Offer: Retry, Abort, or Manual cleanup

## Success Criteria

- [ ] Phase subagent spawned for each phase
- [ ] Results collected and parsed as JSON
- [ ] User approval gate respected (unless trust=high)
- [ ] Lock released on completion or error
- [ ] Progress banners displayed at phase transitions
