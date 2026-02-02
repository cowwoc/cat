---
description: Execute work phases with task-aware progress banner rendering (internal skill, invoked by /cat:work)
user-invocable: false
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
---

# Work With Task: Phase Executor

Execute all 4 work phases (prepare, execute, review, merge) with automatic progress banner rendering.

**Architecture:** This skill is invoked by `/cat:work` after task discovery (Phase 1). It receives
the task ID and metadata as arguments, allowing exclamation-backtick preprocessing to render progress
banners with the actual task ID.

## Arguments Format

The main `/cat:work` skill invokes this with JSON-encoded arguments:

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
  "auto_remove": true
}
```

## Progress Banners

Progress banners for all 4 phases are pre-rendered by the work_with_task_handler and provided
in the SCRIPT OUTPUT PROGRESS BANNERS section. The handler parses the task_id from the skill
invocation arguments and calls get-progress-banner.sh to generate all phase banners.

Use the banners from **SCRIPT OUTPUT PROGRESS BANNERS** - they are correctly formatted with
box-drawing characters and proper display widths.

**Phase symbols:** `○` Pending | `●` Complete | `◉` Active | `✗` Failed

**Banner pattern by phase:**
- Preparing: `◉ ○ ○ ○`
- Executing: `● ◉ ○ ○`
- Reviewing: `● ● ◉ ○`
- Merging: `● ● ● ◉`

---

## Configuration

Extract configuration from arguments:

```bash
# Parse JSON arguments
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

## Check for Existing Work (M362)

**MANDATORY: Before Phase 2, check if work already exists on the branch.**

```bash
if [[ "$HAS_EXISTING_WORK" == "true" ]]; then
  echo "Task has ${EXISTING_COMMITS} existing commit(s) - skipping execution phase"
  # Skip to Phase 3 (Review)
fi
```

When `has_existing_work: true`:
1. Display message: "Resuming task with existing work - skipping to review"
2. Skip Phase 2 entirely
3. Proceed directly to Phase 3 (Review)

This prevents spawning an execution subagent for work that's already committed.

## Phase 2: Execute

Display the **Executing phase** banner from SCRIPT OUTPUT PROGRESS BANNERS (● ◉ ○ ○ pattern).

Delegate to work-execute subagent:

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
    All file edits must be to files within that worktree.

    Return JSON per the output contract.
```

**Handle result:**

| Status | Action |
|--------|--------|
| SUCCESS | Store metrics, continue to Phase 3 |
| PARTIAL | Warn user, continue to Phase 3 |
| FAILED | Display error, offer retry or abort |
| BLOCKED | Display blocker, stop |

### Protocol Violation Handling (M348)

**MANDATORY: Correctness over completion.**

After receiving execution result, check for protocol violations:

1. **Check skill invocation compliance**: If PLAN.md specified skills (e.g., `/cat:shrink-doc`),
   verify subagent actually invoked them (check for skill invocation in result or commits)

2. **If protocol violation detected:**
   - Do NOT rationalize ("tests pass", "looks reasonable", "saves tokens")
   - Do NOT proceed to review phase
   - REJECT the work and re-execute with explicit instruction:
     "Previous execution violated M333 - MUST invoke /cat:shrink-doc skill, not manually edit files"

3. **Why this matters:** Manual workarounds skip validation that skills provide. Accepting
   protocol-violating work means accepting unvalidated work.

**Anti-pattern:** "The work looks correct so I'll proceed despite the protocol violation."
**Correct behavior:** "Protocol was violated. Re-executing with proper skill invocation."

**Token check:**
- If `compaction_events > 0`: Warn user, offer decomposition
- If `percent_of_context > 80`: Invoke learn-from-mistakes

**Store for later phases:**
- `commits`, `files_changed`, `tokens_used`

### Compression Task Validation (M347)

**MANDATORY for tasks with "compress" or "shrink" in task_id:**

Before proceeding to Phase 3, validate execution result contains per-file metrics:

1. Check if `task_metrics.equivalence_scores` exists and is non-empty
2. Display the compression report to user:

```
## Compression Results

| File | Before | After | Reduction | Score | Status |
|------|--------|-------|-----------|-------|--------|
| {filename} | {tokens} | {tokens} | {%} | {score} | {PASS/FAIL} |
```

**If validation fails:**
- Missing `task_metrics`: Return to execution phase with explicit requirement
- Any score < 1.0: Block merge, require iteration

**When results are unexpected (M357):**

If subagent results differ from expectations or another validation source, investigate the SOURCE:

1. **Review the delegation prompt** - Did it prime the subagent toward certain outputs?
2. **Review the skill files** - Are the skill instructions clear and unambiguous?
3. **Check for methodology differences** - Different extraction or comparison approaches yield different scores

Do NOT add independent validation layers. Another subagent running the same skill is no more "independent"
than the original. Fix the prompt or skill that produces unexpected results.

**This validation enforces M346 (per-file reporting) at the orchestration level.**

## Phase 3: Review

**Skip if:** `VERIFY == "none"` or `TRUST == "high"`

Display the **Reviewing phase** banner from SCRIPT OUTPUT PROGRESS BANNERS (● ● ◉ ○ pattern).

Delegate to work-review subagent:

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

**Handle result:**

| Status | Action |
|--------|--------|
| APPROVED | Continue to user approval gate |
| CONCERNS | Note concerns, continue to user approval gate |
| REJECTED | If medium trust: auto-loop to fix. If low trust: ask user |

**User Approval Gate (if trust != high):**

Before asking for approval, display the task's goal to remind the user what this task is about:

1. Read `${TASK_PATH}/PLAN.md`
2. Extract the content between `## Goal` and the next `##` heading
3. Display to user: "**Task Goal:** {extracted goal text}"

Then use AskUserQuestion:
- header: "Approval"
- question: "Ready to merge {TASK_ID}?"
- options:
  - "Approve and merge"
  - "Request changes" (provide feedback)
  - "Abort"

## Phase 4: Merge

Display the **Merging phase** banner from SCRIPT OUTPUT PROGRESS BANNERS (● ● ● ◉ pattern).

Delegate to work-merge subagent:

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

**Handle result:**

| Status | Action |
|--------|--------|
| MERGED | Display success, return to main work skill |
| CONFLICT | Display conflicting files, ask user for resolution |
| ERROR | Display error, attempt manual cleanup |

## Return to Main Skill

After Phase 4 completes (successfully or with error), return control to the main `/cat:work` skill
so it can check for next tasks and handle auto-continuation.

Return a summary of the execution:

```json
{
  "status": "SUCCESS|FAILED",
  "task_id": "2.1-task-name",
  "commits": [...],
  "files_changed": 5,
  "tokens_used": 65000,
  "merged": true
}
```

## Error Handling

If any phase subagent fails unexpectedly:

1. Capture error message
2. Attempt lock release: `${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh release ...`
3. Display error to user
4. Return error status to main work skill

## Success Criteria

- [ ] Progress banners displayed automatically via preprocessing for all 4 phases
- [ ] Phase subagents spawned for execute, review, merge
- [ ] Results collected and parsed as JSON
- [ ] User approval gate respected (unless trust=high)
- [ ] Lock released on completion or error
