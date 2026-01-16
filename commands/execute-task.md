---
name: cat:execute-task
description: Execute task (continues incomplete work)
argument-hint: "[major.minor-task-name]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
  - Task
  - AskUserQuestion
  - SlashCommand
---

<objective>

Execute a task with worktree isolation, subagent orchestration, and quality gates.

**Concurrent Execution:** This command uses task-level locking to prevent multiple Claude instances
from executing the same task simultaneously. Locks persist until explicitly released.

This is CAT's core execution command. It:
1. Finds the next executable task (pending + dependencies met)
2. Acquires exclusive task lock (prevents concurrent execution)
3. Creates a task worktree and branch
4. Executes the PLAN.md (spawn subagent or work directly)
5. Monitors token usage throughout
6. Runs stakeholder review gate (multi-perspective quality review)
7. Loops back to fix concerns if review rejects
8. Runs user approval gate (interactive mode)
9. Squashes commits by type
10. Merges task branch to main
11. Cleans up worktrees
12. Updates STATE.md
13. Updates changelogs (minor/major CHANGELOG.md)
14. Offers next task

</objective>

<progress_output>

**MANDATORY: Display progress at each major step.**

This workflow has 14 major steps. Display progress at each step using the format from
[progress-display.md § Step Progress Format](.claude/cat/references/progress-display.md#step-progress-format):

```
[Step N/14] Step description [=====>              ] P% (Xs | ~Ys remaining)
✅ Step completed: result summary
```

**Major steps for progress tracking:**
1. Verify planning structure
2. Find/load task
3. Acquire task lock
4. Load task details
5. Analyze task size
6. Create worktree and branch
7. Execute task (spawn subagent)
8. Collect subagent results
9. Evaluate token usage
10. Run stakeholder review
11. User approval gate
12. Squash commits
13. Merge to main
14. Update state and changelog

**Track timing:**
- Record start time at command begin: `START_TIME=$(date +%s)`
- Calculate elapsed at each step: `ELAPSED=$(($(date +%s) - START_TIME))`
- Estimate remaining based on average step time

</progress_output>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/execute-task.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/merge-and-cleanup.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/agent-architecture.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/subagent-delegation.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/commit-types.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/changelog.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/spawn-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/merge-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/stakeholder-review/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/index.md

</execution_context>

<context>

Task path: $ARGUMENTS

**Load project state first:**
@.claude/cat/cat-config.json
@.claude/cat/PROJECT.md
@.claude/cat/ROADMAP.md

</context>

<process>

<step name="verify">

**MANDATORY FIRST STEP - Verify planning structure:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No .claude/cat/ directory. Run /cat:new-project first." && exit 1
[ ! -f .claude/cat/cat-config.json ] && echo "ERROR: No cat-config.json. Run /cat:new-project first." && exit 1
```

**Load configuration:**

Read `.claude/cat/cat-config.json` to determine:
- `yoloMode` - whether approval gates are skipped
- `contextLimit` - total context window size
- `targetContextUsage` - soft limit for task size

</step>

<step name="find_task">

**Identify task to execute:**

**If $ARGUMENTS provided:**
- Parse as `major.minor-task-name` format (e.g., `1.0-parse-tokens`)
- Validate task exists at `.claude/cat/v{major}/v{major}.{minor}/{task-name}/`
- Load its STATE.md and PLAN.md

**If $ARGUMENTS empty:**
- Scan all tasks to find first executable:
  1. Status is `pending` or `in-progress`
  2. All task dependencies are `completed`
  3. Minor version dependency is met (see below)

```bash
# Find all task STATE.md files (depth 3 under major version = task level)
find .claude/cat/v*/v*.* -mindepth 2 -maxdepth 2 -name "STATE.md" 2>/dev/null
```

**Minor version dependency rules:**

| Scenario | Dependency |
|----------|------------|
| First minor of first major (v0.0) | None - always executable |
| Subsequent minors (e.g., v0.5) | Previous minor must be complete (v0.4) |
| First minor of new major (e.g., v1.0) | Last minor of previous major must be complete |

**To check if a minor is complete:**
All tasks within that minor version must have `status: completed`.

For each task, check:
- Parse STATUS from STATE.md
- Parse DEPENDENCIES from STATE.md
- Verify each task dependency has status: completed
- Verify minor version dependency is met (all tasks in dependency minor are completed)

**If no executable task found:**

```
No executable tasks found.

Possible reasons:
- All tasks completed
- Remaining tasks have unmet dependencies
- No tasks defined yet

Use /cat:status to see current state.
Use /cat:add-task to add new tasks.
```

Exit command.

</step>

<step name="acquire_lock">

**Acquire task lock (concurrent execution safety):**

Before proceeding, acquire an exclusive lock to prevent multiple Claude instances from executing the
same task simultaneously.

**MANDATORY STOP POINT (M057):** First verify SESSION_ID is available in context.

**How to get SESSION_ID:**
1. Look for `Session ID: {uuid}` in the SessionStart system-reminder at conversation start
2. Extract the UUID (e.g., `5d3a593f-718d-4b8f-8830-e97e2c646713`)
3. Use that value in the commands below by substituting it directly

**If no "Session ID:" appears in the conversation context:**
1. **STOP** - Session ID is required before proceeding
2. Inform user: "Session ID not found in context. The echo-session-id.sh hook may not be registered."
3. Instruct: "Run `/cat:register-hook` to register required hooks, then restart Claude Code."
4. **EXIT this command** - Wait for user to register hooks and restart

**Only if SESSION_ID is found in context**, proceed with lock acquisition.

Substitute the actual session ID UUID into these commands:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
SESSION_ID="<substitute-actual-uuid-from-context>"

# Attempt to acquire lock
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" acquire "$TASK_ID" "$SESSION_ID")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  OWNER=$(echo "$LOCK_RESULT" | jq -r '.owner // "unknown"')
  echo "ERROR: Task $TASK_ID is locked by another session: $OWNER"
  echo "Another Claude instance is already executing this task."
  echo ""
  echo "MANDATORY: Execute a DIFFERENT task instead."
  echo "Run /cat:status to find other executable tasks."
  echo ""
  echo "REQUIRED ACTIONS:"
  echo "  - Choose a different task from /cat:status"
  echo "  - If session crashed, ask user to run /cat:cleanup to remove stale lock"
  exit 1
fi

echo "Lock acquired for task: $TASK_ID"
```

</step>

<step name="load_task">

**Load task details:**

Read the task's:
- `STATE.md` - current status, progress, dependencies
- `PLAN.md` - execution plan with steps
- Parent minor's `STATE.md` - for context
- Parent major's `STATE.md` - for context

Present task overview with visual progress bar
(see [progress-display.md § Progress Bar Format](.claude/cat/references/progress-display.md#progress-bar-format)):

```
## Task: {task-name}

**Version:** {major}.{minor}
**Status:** {status}
**Progress:** [==========>         ] {progress}%

**Goal:**
{goal from PLAN.md}

**Approach:**
{approach from PLAN.md}
```

</step>

<step name="analyze_task_size">

**MANDATORY: Analyze task complexity BEFORE execution.**

Read the task's PLAN.md and estimate context requirements:

```
## Task Size Analysis

**Indicators of large task:**
- Multiple distinct features or components
- Many files to create/modify (> 5)
- Multiple test suites required
- Complex logic requiring exploration
- Estimated steps > 10
```

**Calculate threshold from config:**

```bash
# Read from cat-config.json
CONTEXT_LIMIT=$(jq -r '.contextLimit // 200000' .claude/cat/cat-config.json)
TARGET_USAGE=$(jq -r '.targetContextUsage // 40' .claude/cat/cat-config.json)
THRESHOLD=$((CONTEXT_LIMIT * TARGET_USAGE / 100))

echo "Context threshold: ${THRESHOLD} tokens (${TARGET_USAGE}% of ${CONTEXT_LIMIT})"
```

**Estimate task size:**

Analyze PLAN.md to estimate token requirements:

| Factor | Weight | Estimation |
|--------|--------|------------|
| Files to create | 5K tokens each | Count × 5000 |
| Files to modify | 3K tokens each | Count × 3000 |
| Test files | 4K tokens each | Count × 4000 |
| Steps in PLAN.md | 2K tokens each | Count × 2000 |
| Exploration needed | 10K tokens | +10000 if uncertain |

**If estimated size > threshold:**

```
⚠️ TASK SIZE EXCEEDS CONTEXT THRESHOLD

Estimated tokens: ~{estimate}
Threshold: {THRESHOLD} ({TARGET_USAGE}% of {CONTEXT_LIMIT})

AUTO-DECOMPOSITION TRIGGERED
Invoking /cat:decompose-task to split into smaller tasks...
```

Invoke `/cat:decompose-task` automatically, then proceed with parallel execution.

**Decomposition output:**

decompose-task will produce:
1. List of subtasks with dependencies
2. Parallel execution plan (which tasks can run concurrently)
3. Updated STATE.md files

**After decomposition, invoke parallel execution:**

If decomposition created independent subtasks:

```
## Parallel Execution Plan

**Independent tasks (can run concurrently):**
- 1.2a-parser-lexer
- 1.2b-parser-ast (after 1.2a)
- 1.2c-parser-tests

**Wave 1 (parallel):** 1.2a-parser-lexer, 1.2c-parser-tests
**Wave 2 (after wave 1):** 1.2b-parser-ast

Spawning {N} subagents for wave 1...
```

Use `/cat:parallel-execute` skill to spawn multiple subagents.

**If task size is within threshold:**

```
✓ Task size OK: ~{estimate} tokens ({percentage}% of threshold)
Proceeding with single subagent execution.
```

Continue to create_worktree step.

</step>

<step name="create_worktree">

**Create task worktree and branch:**

Branch naming: `{major}.{minor}-{task-name}`

```bash
# Ensure we're on main branch
MAIN_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")

# Create branch if it doesn't exist
TASK_BRANCH="{major}.{minor}-{task-name}"
git branch "$TASK_BRANCH" "$MAIN_BRANCH" 2>/dev/null || true

# Create worktree
WORKTREE_PATH="../.worktrees/$TASK_BRANCH"
git worktree add "$WORKTREE_PATH" "$TASK_BRANCH" 2>/dev/null || \
    echo "Worktree already exists at $WORKTREE_PATH"

# MANDATORY: Change to worktree directory
cd "$WORKTREE_PATH"
pwd  # Verify we're in the worktree
```

**CRITICAL: Main agent MUST work from worktree directory**

After creating the worktree, `cd` into it and stay there for the remainder of task execution. This ensures:
- All file edits happen in the correct isolated context
- Git commands operate on the task branch
- STATE.md updates go to the worktree copy (not main workspace)
- No confusion between main workspace and worktree file paths

**Update task STATE.md:**

Set status to `in-progress` and record start time.

</step>

<step name="execute">

**Execute the PLAN.md:**

**MANDATORY: Always spawn subagent for implementation.**

Main agent is the orchestrator. Subagents do the work. This is NOT optional.

| Task Size | Strategy |
|-----------|----------|
| Any task | Spawn subagent via `/cat:spawn-subagent` |
| Large/complex | Consider `/cat:decompose-task` first, then spawn |

**Why subagents are mandatory (not optimization):**
- Fresh context = peak quality (no accumulated noise)
- Token tracking enables proactive decomposition
- Branch isolation provides safe rollback
- Main agent context preserved for orchestration
- Prevents quality degradation from context pressure

**Subagent execution workflow:**

1. Invoke `/cat:spawn-subagent` skill with:
   - Task path
   - PLAN.md contents
   - Worktree path
   - Token tracking enabled

2. Monitor subagent via `/cat:monitor-subagents`:
   - Check for compaction events
   - Track token usage
   - Handle early failures

3. Collect results via `/cat:collect-results`:
   - Get execution summary
   - Get token usage report
   - Get any issues encountered

**Error Handling:**

If execution fails:
- Capture error details
- Update STATE.md with error
- Present to user with remediation options
- Use AskUserQuestion:
  - "Retry" - Attempt again
  - "Skip" - Mark task blocked, continue to next
  - "Abort" - Stop execution entirely

</step>

<step name="collect_and_report">

**MANDATORY: Collect results and report token metrics to user.**

After subagent completes, invoke `/cat:collect-results` and present metrics:

```
## Subagent Execution Report

**Task:** {task-name}
**Status:** {success|partial|failed}

**Token Usage:**
- Total tokens: {N} ({percentage}% of context)
- Input tokens: {input_N}
- Output tokens: {output_N}
- Compaction events: {N}

**Work Summary:**
- Commits: {N}
- Files changed: {N}
- Lines: +{added} / -{removed}
```

**Why mandatory:** Users cannot observe subagent execution. Token metrics are the only visibility
into execution quality. Compaction events indicate potential quality degradation.

</step>

<step name="token_check">

**Evaluate token metrics for decomposition:**

**If compaction events > 0:**

Present strong recommendation:

```
⚠️ CONTEXT COMPACTION DETECTED

The subagent experienced {N} compaction event(s). This indicates:
- Context window was exhausted during execution
- Quality may have degraded as context was summarized
- Task may be too large for single-subagent execution

RECOMMENDATION: Decompose remaining work into smaller tasks.
```

Use AskUserQuestion:
- header: "Token Warning"
- question: "Task triggered context compaction. Decomposition is strongly recommended:"
- options:
  - "Decompose" - Split into smaller tasks via /cat:decompose-task (Recommended)
  - "Continue anyway" - Accept potential quality impact
  - "Abort" - Stop and review work quality

**If tokens >= targetContextUsage threshold (from cat-config.json) but no compaction:**

Informational warning:

```
📊 HIGH TOKEN USAGE: {N} tokens ({percentage}% of context)

The subagent used significant context (threshold: {targetContextUsage}%).
Consider decomposing similar tasks in the future.
```

</step>

<step name="stakeholder_review">

**Multi-perspective stakeholder review gate:**

Skip if `yoloMode: true` in config OR `stakeholderReview.enabled: false`.

**MANDATORY: Run stakeholder review BEFORE user approval.**

After implementation completes and token metrics are collected, run parallel stakeholder reviews
to identify concerns from multiple perspectives before presenting to user.

**Stakeholders:**

| Stakeholder | Focus | Reference |
|-------------|-------|-----------|
| architect | System design, modules, APIs | stakeholders/architect.md |
| security | Vulnerabilities, validation | stakeholders/security.md |
| quality | Code quality, complexity | stakeholders/quality.md |
| tester | Test coverage, edge cases | stakeholders/tester.md |
| performance | Efficiency, resources | stakeholders/performance.md |

**Execution:**

1. Identify changed files: `git diff --name-only ${MAIN_BRANCH}..HEAD`
2. Spawn 5 subagents in parallel (one per stakeholder)
3. Each reviews implementation against their criteria
4. Collect JSON responses with concerns and severity

**Aggregation rules:**

| Condition | Result |
|-----------|--------|
| Any CRITICAL concern | REJECTED |
| Any stakeholder REJECTED | REJECTED |
| 3+ HIGH concerns total | REJECTED |
| Only MEDIUM concerns | CONCERNS (proceed) |
| No concerns | APPROVED |

**If REJECTED:**

Present concerns to user:

```
## Stakeholder Review: REJECTED

**Critical Concerns (Must Fix):**
{list concerns with locations and recommendations}

**High Priority Concerns:**
{list concerns}
```

Use AskUserQuestion:
- header: "Review Gate"
- question: "Stakeholder review identified concerns that should be addressed:"
- options:
  - "Fix concerns" - Return to implementation with concern list (Recommended)
  - "Override and proceed" - Continue to user approval with concerns noted
  - "Abort" - Stop task execution

**If "Fix concerns":**
- Record concerns in task context
- Loop back to `execute` step
- Subagent receives concerns as additional requirements
- Repeat until APPROVED or max iterations (3) reached

**If max iterations reached:**
- Force escalation to user
- Present all remaining concerns
- User decides whether to override or abort

**If APPROVED or CONCERNS:**

Proceed to approval_gate with stakeholder summary:

```
## Stakeholder Review: PASSED

| Stakeholder | Status | Concerns |
|-------------|--------|----------|
| architect | ✓ APPROVED | 0 |
| security | ✓ APPROVED | 0 |
| quality | ⚠ CONCERNS | 2 medium |
| tester | ✓ APPROVED | 0 |
| performance | ✓ APPROVED | 0 |

**Medium Priority (Informational):**
{list if any}
```

</step>

<step name="approval_gate">

**Approval gate (Interactive mode only):**

Skip if `yoloMode: true` in config.

**MANDATORY: Verify commit exists before presenting approval (M072).**

Users cannot review uncommitted changes. Before presenting the approval gate:

```bash
# Verify there are commits on the task branch that aren't on main
MAIN_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")
COMMIT_COUNT=$(git rev-list --count ${MAIN_BRANCH}..HEAD 2>/dev/null || echo "0")

if [[ "$COMMIT_COUNT" -eq 0 ]]; then
  echo "ERROR: No commits on task branch. Commit changes before approval gate."
  # Stage and commit all task-related changes before proceeding
fi
```

**Anti-pattern (M072):** Presenting AskUserQuestion for approval with uncommitted changes.
The user sees a summary but cannot `git diff` or review actual file contents.

**MANDATORY: Verify STATE.md in implementation commit (M076/M085).**

Before presenting approval gate, verify STATE.md is included:

```bash
TASK_STATE=".claude/cat/v${MAJOR}/v${MAJOR}.${MINOR}/task/${TASK_NAME}/STATE.md"

# Check if STATE.md was modified in the commits
if ! git diff --name-only ${MAIN_BRANCH}..HEAD | grep -q "STATE.md"; then
  echo "ERROR: STATE.md not in commit. Must update STATE.md in same commit as implementation."
  echo "Fix: Amend the implementation commit to include STATE.md update."
  # DO NOT present approval gate until fixed
fi
```

**MANDATORY: STATE.md in implementation commit (M076).**

STATE.md tracks task state and MUST be updated in the same commit as the implementation,
not in a separate docs commit:

```bash
# Update task STATE.md before committing implementation
TASK_STATE=".claude/cat/v${MAJOR}/v${MAJOR}.${MINOR}/task/${TASK_NAME}/STATE.md"
```

**Required STATE.md fields for completion (M092):**

```markdown
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** [any-dependencies]
- **Completed:** {YYYY-MM-DD}
```

Resolution field is MANDATORY. Valid values: `implemented`, `duplicate`, `obsolete`.

**Why:** STATE.md reflects task completion. Separating it from the implementation:
- Creates disconnected commits that require extra squash/rebase
- Makes the implementation commit incomplete (doesn't reflect task state)
- Violates atomic commit principle (related changes in one commit)

**Correct pattern:** Subagent or main agent updates STATE.md → includes in implementation commit.

**Anti-pattern (M076):** Committing STATE.md separately as "docs: complete task {name}".

Present work summary with **mandatory token metrics**:

```
## Task Complete: {task-name}

**Subagent Metrics:**
- Tokens used: {N} ({percentage}% of context)
- Compaction events: {N}
- Execution quality: {good|degraded if compactions > 0}

**Files Changed:**
- path/to/file1.ext (+10, -5)
- path/to/file2.ext (+25, -0)

**Commits:**
- feature: add feature X
- test: add tests for feature X
- docs: update README

**Review branch:** {task-branch}
```

**Anti-pattern (M089):** Presenting subagent branch (e.g., `task-sub-uuid`) instead of task branch.
Users review the task branch which contains merged subagent work, not the internal subagent branch.

**CRITICAL:** Token metrics MUST be included. If unavailable (e.g., `.completion.json` not found),
parse session file directly or report "Metrics unavailable - manual review recommended."

Use AskUserQuestion:
- header: "Approval"
- question: "Review changes and approve merge to main?"
- options:
  - "Approve" - Merge to main
  - "Review first" - I'll check the changes
  - "Request changes" - Need modifications
  - "Abort" - Discard work

**If "Review first":**
Provide commands to review:
```bash
git log {main}..{task-branch} --oneline
git diff {main}...{task-branch}
```
Wait for user to respond with approval.

**If "Request changes":**
Receive feedback and loop back to execute step.

**If "Abort":**
Clean up worktree and branch, mark task as pending.

</step>

<step name="squash_commits">

**Squash commits by category:**

Group commits into two categories:

**Implementation commits** (squashed together):
- `feature:` - features
- `bugfix:` - bug fixes
- `test:` - tests
- `refactor:` - refactoring
- `docs:` - documentation

**Infrastructure commits** (squashed separately):
- `config:` - configuration and maintenance

Create one squashed commit per category:

```bash
# Example: squash all feat commits
git rebase -i --autosquash {base}
```

Use `/cat:git-squash` skill for safe squashing.

</step>

<step name="merge">

**Return to main workspace and merge:**

**MANDATORY: cd back to main workspace before merging.**

We've been working in the worktree directory. To merge, return to the main workspace (where `main` is checked out):

```bash
# Return to main workspace
cd /workspace  # Or wherever CLAUDE_PROJECT_DIR is
pwd  # Verify we're in main workspace (not worktree)
git branch  # Should show main (or master)
```

**Then merge the task branch:**

```bash
git merge --ff-only {task-branch} -m "$(cat <<'EOF'
{commit-type}: {summary from PLAN.md goal}
EOF
)"
```

**Note:** Use `--ff-only` for linear history (not `--no-ff`). See M047.

Handle merge conflicts:
1. Identify conflicting files
2. Attempt automatic resolution
3. If unresolvable, present to user

</step>

<step name="cleanup">

**Clean up worktree and release lock:**

```bash
# Remove worktree
git worktree remove "$WORKTREE_PATH" --force

# Optionally delete branch if autoCleanupWorktrees is true
git branch -d "{task-branch}" 2>/dev/null || true

# Release task lock (substitute actual SESSION_ID from context)
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" release "$TASK_ID" "$SESSION_ID"
echo "Lock released for task: $TASK_ID"
```

**Note:** Lock is also released automatically by `session-unlock.sh` hook on session end, providing
a safety net if the agent crashes or is interrupted.

</step>

<step name="update_state">

**Update STATE.md files:**

**Note:** Task STATE.md was already updated and committed with the implementation (M076).
This step handles the parent STATE.md rollup updates.

1. **Minor STATE.md:**
   - Recalculate progress based on task completion
   - Update status if all tasks complete

2. **Major STATE.md:**
   - Recalculate progress based on minor completion
   - Update status if all minors complete

3. **Dependent tasks:**
   - Find all tasks in the same minor version that list this task in Dependencies
   - For each dependent task, check if ALL its dependencies are now completed
   - If all dependencies met, the task is now executable (no longer blocked)

</step>

<step name="commit_metadata">

**Commit parent STATE.md rollup updates:**

Only commit Minor/Major STATE.md updates here. Task STATE.md was already committed
with the implementation per M076.

```bash
git add .claude/cat/v*/STATE.md .claude/cat/v*/v*.*/STATE.md
git commit -m "$(cat <<'EOF'
config: update progress for v{major}.{minor}

Task {task-name} completed. Updates minor/major STATE.md progress.
EOF
)"
```

</step>

<step name="update_changelogs">

**Update version changelogs:**

1. **Minor version CHANGELOG.md** (`.claude/cat/v{major}/v{major}.{minor}/CHANGELOG.md`):
   - If file doesn't exist, create from template with pending status
   - Add completed task to Tasks Completed table:
     ```markdown
     | {task-name} | {commit-type} | {goal from PLAN.md} |
     ```

2. **Major version CHANGELOG.md** (`.claude/cat/v{major}/CHANGELOG.md`):
   - If file doesn't exist, create from template with pending status
   - Update aggregate summary when minor version completes

> See `templates/changelog.md` for full format. Task details are in commit messages.

</step>

<step name="next_task">

**Offer next task:**

Find next executable task (pending + dependencies met).

If found:

```
---

## Task Complete

**{task-name}** merged to main.

## Next Up

**{next-task-name}** - {goal from PLAN.md}

<sub>`/clear` first -> fresh context window</sub>

`/cat:execute-task {major}.{minor}/{next-task-name}`

---
```

If no more tasks:

```
---

## Task Complete

**{task-name}** merged to main.

## All Tasks Complete

Minor version {major}.{minor} is complete!

Use `/cat:status` to see overall progress.
Use `/cat:add-task` to add more tasks.
Use `/cat:add-minor-version` to add a new minor version.

---
```

</step>

</process>

<deviation_rules>

During execution, handle discoveries automatically:

1. **Auto-fix bugs** - Fix immediately, document in CHANGELOG
2. **Auto-add critical** - Security/correctness gaps, add and document
3. **Auto-fix blockers** - Can't proceed without fix, do it and document
4. **Ask about architectural** - Major structural changes, stop and ask user
5. **Log enhancements** - Nice-to-haves, propose as new task, continue

Only rule 4 requires user intervention.

</deviation_rules>

<plan_change_checkpoint>

**MANDATORY: Announce plan changes BEFORE implementation.**

If during execution you discover the plan needs modification:

1. **STOP** implementation immediately
2. **ANNOUNCE** the change to user with:
   - What the original plan said
   - What needs to change
   - Why the change is needed
3. **WAIT** for user acknowledgment before proceeding
4. **DOCUMENT** the change in commit message under "Deviations from Plan"

**Anti-Pattern (M034):** Changing plan silently and only mentioning it after user asks.

**Examples requiring announcement:**
- Removing a planned feature or flag
- Adding unplanned dependencies
- Changing the approach/architecture
- Skipping planned tests

**Examples NOT requiring announcement:**
- Minor implementation details
- Bug fixes discovered during implementation
- Adding helper methods not in plan

</plan_change_checkpoint>

<user_review_checkpoint>

**MANDATORY: User review before merge (unless yoloMode).**

Before merging any work to main:

1. Present complete summary of changes
2. **WAIT** for explicit approval via AskUserQuestion
3. Require explicit "Approve" response before proceeding
4. If changes are made after initial review, request re-approval

**Required behavior (M035):** Always pause for user review before marking complete.

**User review includes:**
- All files changed with diffs
- All commits with messages
- Token usage and compaction events
- Any deviations from original plan

</user_review_checkpoint>

<main_agent_boundaries>

**MANDATORY: Main agent delegates ALL implementation to subagents.**

The main agent is an ORCHESTRATOR. All code implementation MUST be delegated to subagents.

**Main agent responsibilities:**
- Read files for analysis
- Run diagnostic commands (git status, build, tests)
- Edit STATE.md, PLAN.md, CHANGELOG.md (orchestration files)
- Present summaries and ask questions
- Invoke skills and spawn subagents

**Subagent responsibilities (Learning M063):**
- Write and edit source code files (.java, .ts, .py, etc.)
- Write and edit test files
- Fix bugs
- Make corrections to code
- Implement any code changes

**When user provides feedback requiring code changes:**

1. **Spawn a new subagent** with:
   - The feedback as context
   - Clear instructions on what to fix
   - The existing branch/worktree
2. **Use** `/cat:spawn-subagent` or continue in existing subagent

**Required behavior (M063):** "I'll spawn a subagent to address that feedback." [invokes spawn-subagent]

**Exception:** Trivial STATE.md updates that are purely orchestration (status changes, not code).

</main_agent_boundaries>

<commit_rules>

**Per-Step Commits:**

After each execution step:
1. Stage only files modified by that step
2. Commit with appropriate type prefix
3. Types: feature, bugfix, test, refactor, docs, config, performance

**Always stage files individually:**
```bash
git add path/to/specific/file.java
git add path/to/another/file.java
```

Avoid broad staging (`git add .`, `git add -A`, `git add src/`) which captures unintended files.

**Enhanced Commit Message Format (replaces task CHANGELOG.md):**

The final squashed commit message MUST include changelog content. The commit diff
already shows Files Modified, Files Created, and Test Coverage - omit these from the message.

```
{type}: {concise description}

## Problem Solved
[WHY this task was needed - what wasn't working or was missing]
- {Problem 1}
- {Problem 2 if applicable}

## Solution Implemented
[HOW the problem was solved - the approach taken]
- {Key implementation detail 1}
- {Key implementation detail 2}

## Decisions Made (optional)
- {Decision}: {rationale}

## Known Limitations (optional)
- {Limitation}: {why accepted or deferred}

## Deviations from Plan (optional)
- {Deviation}: {reason and impact}

Task ID: v{major}.{minor}-{task-name}
```

**Example:**
```
feature: add lambda expression parsing

## Problem Solved
- Parser failed on multi-parameter lambdas: `(a, b) -> a + b`
- 318 parsing errors in Spring Framework codebase

## Solution Implemented
- Added lookahead in parsePostfix() to detect lambda arrow
- Reused existing parameter parsing for lambda parameters
- Handles both inferred and explicit type parameters

## Decisions Made
- Reuse parameter parsing: Maintains consistency with method parameters

Task ID: v1.0-parse-lambdas
```

</commit_rules>

<duplicate_task_handling>

**Detecting Duplicate Tasks**

During task execution, you may discover the task is a duplicate - another task already
implemented the same functionality.

**Signs of a duplicate:**
1. Investigation reveals the functionality already exists
2. Tests for this task's scenarios already pass
3. Another task addressed the same root cause

**How to handle:**

1. **Stop execution** - skip worktree creation and implementation
2. **Verify** - test the specific scenarios from this task's PLAN.md
3. **Identify original** - find which task/commit implemented the fix
4. **Update STATE.md** with duplicate resolution:

```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-task-name}
- **Completed:** {date}
```

5. **Commit STATE.md only** (no Task ID footer):

```bash
git commit -m "config: close duplicate task {task-name}

Duplicate of {original-task} (commit {hash}).
Verification confirmed all scenarios from PLAN.md pass.
"
```

6. **Release lock and cleanup** - same as normal task completion
7. **Offer next task** - continue to next executable task

**Important:** Duplicate tasks omit the `Task ID:` footer (reserved for implementation commits).
See [task-resolution.md](../references/task-resolution.md) for details.

</duplicate_task_handling>

<success_criteria>

- [ ] Task identified and loaded
- [ ] Task lock acquired (SESSION_ID verified)
- [ ] **Task size analyzed (estimate vs threshold)**
- [ ] **If oversized: auto-decomposition triggered**
- [ ] **If decomposed: parallel execution plan generated**
- [ ] Worktree(s) created with correct branch(es)
- [ ] PLAN.md executed successfully via subagent(s)
- [ ] **Token metrics collected and reported to user**
- [ ] **Compaction events evaluated (decomposition offered if > 0)**
- [ ] **Stakeholder review passed (or concerns addressed)**
- [ ] Approval gate passed (if interactive)
- [ ] Commits squashed by type
- [ ] Branch(es) merged to main
- [ ] Worktree(s) cleaned up
- [ ] Lock released
- [ ] STATE.md files updated
- [ ] Next task offered

</success_criteria>
