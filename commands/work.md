---
name: cat:work
description: Work on tasks (auto-continues when trust >= medium)
argument-hint: "[version | taskId] [--override-gate]"
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
8. Squashes commits by type
9. Runs user approval gate (interactive mode)
10. Merges task branch to main
11. Cleans up worktrees
12. Updates STATE.md
13. Updates changelogs (minor/major CHANGELOG.md)
14. Offers next task

</objective>

<progress_output>

**MANDATORY: Display progress at each major step.**

This workflow has 17 steps. Display progress at each step using the format from
[display-standards.md § Step Progress Format](.claude/cat/references/display-standards.md#step-progress-format):
1. Verify planning structure
2. Find/load task
3. Acquire task lock
4. Load task details
5. Analyze task size
6. Choose approach (fork in the road)
7. Create worktree and branch
8. Execute task (spawn subagent)
9. Collect subagent results
10. Evaluate token usage
11. Handle discovered issues (patience setting)
12. Verify changes (based on verify setting)
13. Run stakeholder review
14. Squash commits
15. User approval gate
16. Merge to main
17. Update state and changelog

**Track timing:**
- Record start time at command begin: `START_TIME=$(date +%s)`
- Calculate elapsed at each step: `ELAPSED=$(($(date +%s) - START_TIME))`
- Estimate remaining based on average step time

</progress_output>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/work.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/merge-and-cleanup.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/agent-architecture.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/subagent-delegation.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/commit-types.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/changelog.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/spawn-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/merge-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/stakeholder-review/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/choose-approach/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/index.md

</execution_context>

<conditional_context>

**Load on demand when specific scenarios occur:**

| Scenario | Load Workflow |
|----------|---------------|
| Minor/major version completes | @${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/version-completion.md |
| Task discovered as duplicate | @${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/duplicate-task.md |
| Compaction events or high token usage | @${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/token-warning.md |

</conditional_context>

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
[ ! -d .claude/cat ] && echo "ERROR: No .claude/cat/ directory. Run /cat:init first." && exit 1
[ ! -f .claude/cat/cat-config.json ] && echo "ERROR: No cat-config.json. Run /cat:init first." && exit 1
```

**Load configuration:**

Read `.claude/cat/cat-config.json` to determine:
- `trust` - trust level (high = skip approval gates)
- `contextLimit` - total context window size
- `targetContextUsage` - soft limit for task size

</step>

<step name="find_task">

**Identify task to execute:**

**Session ID**: The session ID is automatically available as `${CLAUDE_SESSION_ID}` in this command.
All bash commands below use this value directly.

**Argument parsing:**

| Argument Format | Example | Behavior |
|-----------------|---------|----------|
| (none) | `/cat:work` | Work on first available task in first incomplete minor version |
| `major` | `/cat:work 0` | Work through ALL minors in major v0 (v0.0 → v0.1 → ...) |
| `major.minor` | `/cat:work 0.5` | Work through ALL tasks in v0.5 |
| `major.minor-task` | `/cat:work 0.5-parse-tokens` | Work on specific task only |
| `--override-gate` | `/cat:work 0.5 --override-gate` | Skip entry gate check |

**Scope tracking (stored in memory during session):**

```bash
# Track execution scope for auto-continue decisions
WORK_SCOPE="task"      # "task" | "minor" | "major" | "all"
WORK_TARGET=""         # e.g., "0" for major, "0.5" for minor, "0.5-parse" for task
```

| Argument | WORK_SCOPE | WORK_TARGET | Continue Until |
|----------|------------|-------------|----------------|
| (none) | `all` | - | All tasks in all versions complete |
| `0` | `major` | `0` | All tasks in v0.x complete |
| `0.5` | `minor` | `0.5` | All tasks in v0.5 complete |
| `0.5-task` | `task` | `0.5-task` | Single task complete |

**If $ARGUMENTS is a major version (single number, e.g., "0", "1"):**
- Set `WORK_SCOPE="major"` and `WORK_TARGET="{major}"`
- Find first incomplete minor in that major (v{major}.0, v{major}.1, ...)
- Find first executable task in that minor
- After task completes, continue to next task in minor, then next minor in major

**If $ARGUMENTS is a minor version (major.minor format, e.g., "0.5"):**
- Set `WORK_SCOPE="minor"` and `WORK_TARGET="{major}.{minor}"`
- Find the first executable task in that specific minor version
- Tasks are ordered by: in-progress first, then pending by dependency order
- After task completes, continue to next task in same minor until all complete

**If $ARGUMENTS is a task ID (major.minor-task-name format):**
- Parse as `major.minor-task-name` format (e.g., `1.0-parse-tokens`)
- Validate task exists at `.claude/cat/v{major}/v{major}.{minor}/task/{task-name}/`
- **Try to acquire lock BEFORE loading task details** (see lock check below)
- Load its STATE.md and PLAN.md

**If $ARGUMENTS empty:**
- Find the first incomplete minor version (scan v0.0, v0.1, ... until one has pending tasks)
- Scan tasks in that minor to find first executable:
  1. Status is `pending` or `in-progress`
  2. All task dependencies are `completed`
  3. Version entry gate is satisfied (see below)
  4. **Task is not locked by another session** (see lock check below)

```bash
# Find all task STATE.md files (task/ subdirectory contains task directories)
find .claude/cat/v*/v*.*/task -mindepth 2 -maxdepth 2 -name "STATE.md" 2>/dev/null
```

**MANDATORY: Lock Check Before Offering Task (M097)**

For each candidate task (whether from $ARGUMENTS or auto-discovery), attempt to acquire the lock
BEFORE offering it as available:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
# Session ID is auto-substituted

# Try to acquire lock
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" acquire "$TASK_ID" "${CLAUDE_SESSION_ID}")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  OWNER=$(echo "$LOCK_RESULT" | jq -r '.owner // "unknown"')
  echo "⏸️ Task $TASK_ID is locked by session: $OWNER"
  # Skip this task and try the next candidate
  # Do NOT offer this task as available
  continue
fi

# Lock acquired - this task is now ours
echo "✓ Lock acquired for task: $TASK_ID"
```

**Why check locks early:**
- Prevents offering tasks that another Claude instance is already executing
- Avoids wasted exploration/planning work on locked tasks
- Provides clear feedback about why certain tasks are unavailable

**MANDATORY: Agent behavior when encountering locks (M097):**
1. **Report** the lock exists and which session holds it
2. **Skip** this task and try the next candidate
3. Only mention `/cat:cleanup` if ALL tasks are locked (no alternatives)

**NEVER:**
- Investigate lock validity (commit counts, worktree state, timestamps are IRRELEVANT)
- Label locks as "stale" based on any evidence
- Offer to remove locks proactively
- Question whether the lock owner is still active

Locks may be held by active sessions that haven't committed yet. Only the USER decides if stale.

**Entry Gate Evaluation:**

For each candidate task, read the version's PLAN.md and extract the `## Gates` → `### Entry` section.

Evaluate each entry condition:

| Condition Type | How to Evaluate |
|----------------|-----------------|
| `Previous minor version (X.Y) complete` | All tasks in vX.Y must have status: completed |
| `Previous major version (N) complete` | All minor versions in vN must be complete |
| `Task X.Y-task-name complete` | That specific task must have status: completed |
| `Version X.Y complete` | All tasks in that version must be complete |
| `Manual approval required` | Check STATE.md for `Entry Approved: true` |
| `Inherits from Major N gates` | Read Major N's PLAN.md, evaluate those gates |
| `No prerequisites` | Always satisfied |
| Custom conditions | Display to user, they decide |

**If no `## Gates` section exists**, fall back to default rules:
- First minor of first major (v0.0): No prerequisites
- Subsequent minors (e.g., v0.5): Previous minor must be complete
- First minor of new major (e.g., v1.0): Last minor of previous major must be complete

For each task, check:
- Parse STATUS from STATE.md
- Parse DEPENDENCIES from STATE.md
- Verify each task dependency has status: completed
- **Check if task is an exit gate task** (see below)
- **Evaluate entry gate from version PLAN.md**
- **Try to acquire lock (skip if locked by another session)**

**Exit Gate Task Dependency Check:**

Exit gate tasks are identified by the `[task]` prefix in the `### Exit` section of PLAN.md. Tasks
marked with this prefix have an implicit dependency on ALL other tasks in the same version and can
only execute when every non-exit-gate task is completed.

Example PLAN.md structure:
```markdown
## Gates

### Entry
- Previous version (v0.4) complete

### Exit
- [task] validate-spring-framework-parsing
```

**Parsing exit gate tasks:**
1. Read the `### Exit` section under `## Gates`
2. Look for lines matching `- [task] {task-name}`
3. Extract task names (strip the `[task]` prefix)

For a task marked with `[task]` in the Exit section:
1. Get all tasks in the same minor version
2. Exclude tasks that are also marked as exit gate tasks
3. If ANY non-exit-gate task has status other than `completed`, this task is blocked

Display if blocked:
```
⏸️ Task {task-name} blocked by exit gate rule:
   🚧 Waiting on: {count} non-gating tasks to complete
   📋 Pending: {list of incomplete non-gating tasks}
```

**If entry gate not satisfied for a task:**

Display the blocking gate condition:
```
⏸️ Task {task-name} blocked by entry gate:
   🚧 Waiting on: {unmet condition}

To override and work on this task anyway, explicitly request:
   /cat:work {major}.{minor}-{task-name} --override-gate
```

Continue scanning for next eligible task. Only if user explicitly provides `--override-gate`
argument, skip the entry gate check for that specific task.

**If no executable task found:**

```
No executable tasks found.

Possible reasons:
- All tasks completed
- Remaining tasks have unmet dependencies
- Exit gate tasks waiting for non-gating tasks to complete
- Entry gates not satisfied
- All eligible tasks are locked by other sessions
- No tasks defined yet

Use /cat:status to see current state and gate status.
Use /cat:add-task to add new tasks.

If you believe locks are from crashed sessions, run /cat:cleanup.
```

Exit command.

</step>

<step name="acquire_lock">

**Verify task lock (already acquired in find_task step):**

The lock was already acquired during the find_task step (M097). This step verifies the lock is held
and displays confirmation.

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
echo "✓ Lock held for task: $TASK_ID (acquired in find_task step)"
```

**Note:** If you reach this step without a lock, something went wrong in find_task. Re-run
the command to properly acquire a lock before proceeding.

</step>

<step name="load_task">

**Load task details:**

Read the task's:
- `STATE.md` - current status, progress, dependencies
- `PLAN.md` - execution plan with steps
- Parent minor's `STATE.md` - for context
- Parent major's `STATE.md` - for context

Present task overview with visual progress bar
(see [display-standards.md § Progress Bar Format](.claude/cat/references/display-standards.md#progress-bar-format)).

**CRITICAL: Output directly WITHOUT code blocks (M125).** Markdown `**bold**` renders correctly
when output as plain text, but shows as literal asterisks inside triple-backtick code blocks.

Output format (do NOT wrap in ```):

## Task: {task-name}

**Version:** {major}.{minor}
**Status:** {status}
**Progress:** [==========>         ] {progress}%

**Goal:**
{goal from PLAN.md}

**Approach:**
{approach from PLAN.md}

</step>

<step name="validate_requirements_coverage">

**MANDATORY: Validate all requirements are covered before execution.**

This step ensures 100% requirements traceability before implementation begins.

**Extract requirements from PLAN.md:**

```bash
# Extract REQ-* IDs from ## Requirements section
REQUIREMENTS=$(grep -oE 'REQ-[0-9]+' "$TASK_PATH/PLAN.md" | sort -u)
REQ_COUNT=$(echo "$REQUIREMENTS" | wc -l)
echo "Found $REQ_COUNT requirements"
```

**Extract covered requirements from ## Requirements Traceability:**

```bash
# Extract requirements mentioned in Covered By column
COVERED=$(grep -A100 '### Requirements Traceability' "$TASK_PATH/PLAN.md" | \
          grep -oE 'REQ-[0-9]+' | sort -u)
COVERED_COUNT=$(echo "$COVERED" | wc -l)
echo "Covered requirements: $COVERED_COUNT"
```

**Identify uncovered requirements:**

```bash
# Find requirements without traceability
UNCOVERED=$(comm -23 <(echo "$REQUIREMENTS") <(echo "$COVERED"))
UNCOVERED_COUNT=$(echo "$UNCOVERED" | grep -c 'REQ-' || echo 0)
```

**If uncovered requirements exist:**

```
❌ REQUIREMENTS COVERAGE VALIDATION FAILED

Uncovered requirements: {UNCOVERED_COUNT}
{list of REQ-* IDs without traceability}

Each requirement MUST be linked to at least one execution step
in the Requirements Traceability table.

Options:
1. Update PLAN.md to add traceability for missing requirements
2. Remove requirements that are out of scope for this task
3. Override and proceed (--override-coverage)
```

Use AskUserQuestion:
- header: "Coverage Gate"
- question: "Requirements coverage validation failed. How to proceed?"
- options:
  - "Update plan" - I'll add the missing traceability
  - "Override" - Proceed without full coverage (not recommended)
  - "Abort" - Stop execution

**If all requirements covered:**

```
✓ Requirements coverage: {REQ_COUNT}/{REQ_COUNT} (100%)
Proceeding to task size analysis.
```

**Skip conditions:**
- Task has no `## Requirements` section (legacy tasks)
- User provided `--override-coverage` flag
- Task type is `bugfix` or `refactor` (requirements optional)

</step>

<step name="analyze_task_size">

**MANDATORY: Analyze task complexity BEFORE execution.**

Read the task's PLAN.md and estimate context requirements.

Output format (do NOT wrap in ```):

## Task Size Analysis

**Indicators of large task:**
- Multiple distinct features or components
- Many files to create/modify (> 5)
- Multiple test suites required
- Complex logic requiring exploration
- Estimated steps > 10

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

**MANDATORY: Store the estimate for later comparison:**

After calculating the estimate, record it for comparison with actual usage:

```bash
# Store estimate for later comparison (used in collect_and_report step)
ESTIMATED_TOKENS={calculated_estimate}
echo "Estimated tokens: ${ESTIMATED_TOKENS}"
```

This estimate will be compared against actual subagent token usage to detect estimation errors.

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

**Sub-task 1 (parallel):** 1.2a-parser-lexer, 1.2c-parser-tests
**Sub-task 2 (after sub-task 1):** 1.2b-parser-ast

Spawning {N} subagents for sub-task 1...
```

Use `/cat:parallel-execute` skill to spawn multiple subagents.

**If task size is within threshold:**

```
✓ Task size OK: ~{estimate} tokens ({percentage}% of threshold)
Proceeding with single subagent execution.
```

Continue to choose_approach step.

</step>

<step name="choose_approach">

**Present approach options if task has multiple viable paths:**

This step implements the "Fork in the Road" experience. It only presents choices when:
- PLAN.md has 2+ genuinely different approaches
- User's stored preferences don't clearly favor one path
- Approaches have meaningfully different tradeoffs

**Load user preferences:**

```bash
# Read behavior preferences (1.9+)
USER_CURIOSITY=$(jq -r '.curiosity // "medium"' .claude/cat/cat-config.json)
USER_PATIENCE=$(jq -r '.patience // "medium"' .claude/cat/cat-config.json)
```

**Analyze PLAN.md for approaches:**

Look for:
- "## Approach" or "## Approaches" section
- "## Alternatives" section
- Risk Assessment section

**Decision matrix:**

| Task Pattern | Curiosity Level | Action |
|--------------|-----------------|--------|
| Single approach | Any | Auto-proceed |
| Low risk, obvious | Any | Auto-proceed |
| High complexity | Any | Show choice, recommend "Research first" |
| Multiple approaches | low | Show choice, recommend safer path |
| Multiple approaches | high | Show choice, recommend comprehensive path |
| Multiple approaches | medium | Show choice, no recommendation |

**If auto-proceed:**

```
✓ Approach: [approach name]
  (Auto-selected: [reason - e.g., "single viable approach" or "matches conservative style"])
```

**If choice needed, display fork using wizard-style format:**

See [display-standards.md § Fork in the Road](.claude/cat/references/display-standards.md#fork-in-the-road)
and [choose-approach skill](skills/choose-approach/SKILL.md) for full format.

```
🔀 FORK IN THE ROAD
╭─────────────────────────────────────────────────────────────────╮
   Task: {task-name}
   Risk: {LOW|MEDIUM|HIGH}

   CHOOSE YOUR PATH
   ─────────────────────────────────────────────────────────────────

   [A] 🛡️ Conservative
       {scope description}
       Risk: LOW | Scope: {N} files | ~{N}K tokens

   [B] ⚖️ Balanced
       {scope description}
       Risk: MEDIUM | Scope: {N} files | ~{N}K tokens

   [C] ⚔️ Aggressive
       {scope description}
       Risk: HIGH | Scope: {N} files | ~{N}K tokens

   ─────────────────────────────────────────────────────────────────
   ANALYSIS
   ─────────────────────────────────────────────────────────────────

   ⭐ QUICK WIN: [{letter}] {approach name}
      {rationale for immediate completion}

   🏆 LONG-TERM: [{letter}] {approach name}
      {rationale for project health over time}

   {Note if they differ}

╰───────────────────────────────────────────────────────────────────╯
```

Use AskUserQuestion to capture selection.

**Record choice in STATE.md:**

Add to task's STATE.md:
```yaml
- **Approach Selected:** [approach name]
- **Selection Reason:** [user choice | auto-selected: reason]
```

**Pass to subagent:**

The selected approach will be included in the subagent prompt to guide implementation.

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

# Create worktree (use absolute path to avoid cwd dependency)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/$TASK_BRANCH"
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

1. Read preferences and include in subagent prompt:
   ```bash
   CURIOSITY=$(jq -r '.curiosity // "medium"' .claude/cat/cat-config.json)
   # Note: PATIENCE is used by main agent AFTER collecting results, not passed to subagent
   ```

   **Curiosity instruction** (for IMPLEMENTATION subagent — whether to NOTE issues):
   | Level | Implementation Subagent Instruction |
   |-------|-------------------------------------|
   | `low` | "Focus ONLY on the assigned task. Do NOT note or report issues outside the immediate scope." |
   | `medium` | "While working, NOTE obvious issues in files you touch. Report them in .completion.json but do NOT fix them." |
   | `high` | "Actively look for issues and improvement opportunities. Report ALL findings in .completion.json but do NOT fix them." |

   **IMPORTANT:** The implementor subagent NEVER fixes discovered issues directly. It follows
   instructions mechanically and reports issues for the main agent to handle.

2. Invoke `/cat:spawn-subagent` skill with:
   - Task path
   - PLAN.md contents (with Selected Approach filled in)
   - Worktree path
   - Token tracking enabled
   - Curiosity instruction (determines issue reporting, NOT fixing)

3. Monitor subagent via `/cat:monitor-subagents`:
   - Check for compaction events
   - Track token usage
   - Handle early failures

4. Collect results via `/cat:collect-results`:
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

After subagent completes, invoke `/cat:collect-results` and present metrics.

Output format (do NOT wrap in ```):

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

**Why mandatory:** Users cannot observe subagent execution. Token metrics are the only visibility
into execution quality. Compaction events indicate potential quality degradation.

**MANDATORY: Compare actual vs estimated tokens:**

After collecting metrics, compare actual token usage against the estimate from step 5:

```bash
# Calculate variance (actual vs estimated)
ACTUAL_TOKENS={total_tokens_from_collect_results}
VARIANCE_THRESHOLD=125  # 25% higher = 125% of estimate

# Calculate percentage: (actual / estimated) * 100
ACTUAL_PERCENT=$((ACTUAL_TOKENS * 100 / ESTIMATED_TOKENS))

if [ "${ACTUAL_PERCENT}" -ge "${VARIANCE_THRESHOLD}" ]; then
  echo "⚠️ TOKEN ESTIMATE VARIANCE DETECTED"
  echo "Estimated: ${ESTIMATED_TOKENS}"
  echo "Actual: ${ACTUAL_TOKENS} (${ACTUAL_PERCENT}% of estimate)"
  echo "Variance exceeds 25% threshold - triggering learn-from-mistakes"
  # MANDATORY: Invoke learn-from-mistakes
fi
```

**If actual >= estimate × 1.25 (25% or more higher):**

Invoke `/cat:learn-from-mistakes` with:
- Description: "Token estimate underestimated actual usage by {variance}%"
- Estimated tokens: {ESTIMATED_TOKENS}
- Actual tokens: {ACTUAL_TOKENS}
- Task: {task-name}
- Compaction events: {N}

This helps calibrate estimation factors over time and identify patterns in underestimation.

</step>

<step name="token_check">

**Evaluate token metrics for decomposition:**

**→ Load token-warning.md workflow if compaction events > 0 or tokens exceed threshold.**

See `.claude/cat/workflows/token-warning.md` for:
- Compaction event warning and user decision
- High token usage informational warning
- Decomposition recommendations
- Token estimate variance check (M099)

**Quick reference:**
- Compaction events > 0 → Strong decomposition recommendation with user choice
- High tokens, no compaction → Informational warning only

</step>

<step name="handle_discovered_issues">

**Handle issues discovered by subagent (patience setting):**

After collecting results, check `.completion.json` for discovered issues:

```bash
COMPLETION_FILE="${WORKTREE}/.completion.json"
ISSUES=$(jq -r '.discoveredIssues // []' "$COMPLETION_FILE")
ISSUE_COUNT=$(echo "$ISSUES" | jq 'length')

if [ "$ISSUE_COUNT" -gt 0 ]; then
  PATIENCE=$(jq -r '.patience // "high"' .claude/cat/cat-config.json)
  echo "Found $ISSUE_COUNT discovered issues. Patience: $PATIENCE"
fi
```

**If no issues discovered:** Skip to stakeholder_review.

**If issues discovered, handle based on patience:**

| Patience | Action |
|----------|--------|
| `high` | Create tasks in FUTURE version backlog (prioritized by benefit/cost) |
| `medium` | Create tasks in CURRENT version backlog |
| `low` | Resume PLANNER subagent to update plan, then re-execute |

**For patience: high**

```bash
# Add issues to future version backlog
for issue in $(echo "$ISSUES" | jq -c '.[]'); do
  BENEFIT_COST=$(echo "$issue" | jq -r '.benefitCost // 1')
  # Create task in next minor version based on priority
  echo "Backlog: $(echo "$issue" | jq -r '.description')"
done
```

Display to user:
```
📋 DISCOVERED ISSUES → FUTURE BACKLOG

{N} issues noted during implementation have been added to the backlog
for future versions (sorted by benefit/cost ratio).

Issues will not block current task completion.
```

**For patience: medium**

```bash
# Add issues as tasks in current version
for issue in $(echo "$ISSUES" | jq -c '.[]'); do
  # Create task in current minor version
  echo "New task: $(echo "$issue" | jq -r '.description')"
done
```

Display to user:
```
📋 DISCOVERED ISSUES → CURRENT VERSION

{N} issues noted during implementation have been added as tasks
in the current minor version.

Issues will not block current task completion.
```

**For patience: low**

Resume the PLANNER subagent to incorporate fixes into the current task:

```bash
# Resume planner subagent with discovered issues
PLANNER_ID=$(cat "${WORKTREE}/.planner_agent_id" 2>/dev/null)
```

Display to user:
```
🔄 DISCOVERED ISSUES → IMMEDIATE ACTION

{N} issues noted during implementation. Patience is LOW.
Resuming planner subagent to update the plan with fixes...
```

Use Task tool with `resume: PLANNER_ID`:
- Provide discovered issues as context
- Request updated PLAN.md incorporating fixes
- Then spawn new implementation subagent with updated plan

**Important:** The implementor does NOT fix issues directly. The planner updates the plan,
and a new implementation pass executes the expanded plan mechanically.

</step>

<step name="verify_changes">

**Run verification based on verify setting:**

```bash
VERIFY_LEVEL=$(jq -r '.verify // "changed"' .claude/cat/cat-config.json)
echo "Verification level: $VERIFY_LEVEL"
```

**MANDATORY (M110): Check for actual source changes first:**

```bash
# Check if any source files changed (not just STATE.md or CHANGELOG.md)
MAIN_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")
SOURCE_CHANGES=$(git diff --name-only ${MAIN_BRANCH}..HEAD | grep -v "\.claude/cat/" | grep -v "CHANGELOG.md" | head -1)

if [[ -z "$SOURCE_CHANGES" ]]; then
  echo "⚡ VERIFICATION: SKIPPED (no source files changed)"
  echo "Only metadata files changed (.claude/cat/, CHANGELOG.md)"
  # Skip to stakeholder_review
fi
```

**Skip verification for validation-only tasks** that don't modify source code (e.g., running
parser against external files). Verification is only meaningful when source files changed.

| verify | Action |
|--------|--------|
| `none` | Skip verification entirely (fastest iteration) |
| `changed` | Run tests on changed files/modules only |
| `all` | Run full project verification (build + all tests) |

**For verify: none**

```
⚡ VERIFICATION: SKIPPED (verify: none)

Proceeding without running tests or build verification.
Use for rapid prototyping; recommend verify: changed for production code.
```

Skip to stakeholder_review.

**For verify: changed**

Run targeted verification on changed files/modules only:

```bash
# Get list of changed files
CHANGED_FILES=$(git diff --name-only origin/HEAD..HEAD)
```

**Approach:**
1. Identify changed files from git diff
2. Detect project type (see build-verification.md for detection logic)
3. Run project-appropriate targeted tests for those files/modules
4. Report results

**Examples by project type:**
| Type | Targeted Test Command |
|------|----------------------|
| Maven | `./mvnw test -pl {changed-modules} -am` |
| Node | `npm test -- --findRelatedTests {files}` |
| Python | `pytest {changed-modules}` |
| Go | `go test {changed-packages}` |
| Rust | `cargo test {changed-crates}` |

Display result:
```
📦 VERIFICATION: CHANGED FILES ONLY (verify: changed)

Changed files: {N}
Tests run: {M} (targeting changed modules)
Result: {PASS|FAIL}
```

**For verify: all**

Run full project verification (build + all tests):

**Approach:**
1. Detect project type from marker files (pom.xml, package.json, etc.)
2. Run the project's standard build command
3. Run the project's full test suite
4. Report results

**Examples by project type:**
| Type | Full Verification Command |
|------|--------------------------|
| Maven | `./mvnw verify` |
| Node | `npm run build && npm test` |
| Python | `pytest` |
| Go | `go build ./... && go test ./...` |
| Rust | `cargo build && cargo test` |
| Make | `make && make test` |

Display result:
```
🔒 VERIFICATION: FULL PROJECT (verify: all)

Build: {PASS|FAIL}
Tests: {N} passed, {M} failed
Result: {PASS|FAIL}
```

**On verification failure:**

Block progression and present options:

```
❌ VERIFICATION FAILED

{Error details}

Options:
1. Fix issues and retry
2. Override and proceed (not recommended)
3. Abort task
```

Use AskUserQuestion to capture decision.

</step>

<step name="stakeholder_review">

**Multi-perspective stakeholder review gate:**

**Skip conditions:**

```bash
# Read trust level
TRUST_LEVEL=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

**Review triggering:**

| Trust | Action |
|-------|--------|
| `high` | Skip review (autonomous mode) |
| `medium` | Run review |
| `low` | Run review |

**High-risk detection:** Read the task's PLAN.md Risk Assessment section. Task is high-risk if ANY of:
- Risk section mentions "breaking change", "data loss", "security", "production"
- Task modifies authentication, authorization, or payment code
- Task touches 5+ files
- Task is marked as HIGH risk

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

Behavior depends on trust level:

| Trust | Rejection Behavior |
|-------|-------------------|
| `low` | Ask user: Fix / Override / Abort |
| `medium` | Auto-loop to fix (up to 3 iterations) |

Note: `trust: "high"` skips review entirely, so rejection handling doesn't apply.

**For `trust: "low"` (user decides):**

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

**For `trust: "medium"` or `trust: "high"` (auto-fix):**

```
## Stakeholder Review: REJECTED (Auto-fixing)

Iteration {N}/3 - Automatically addressing concerns...

**Concerns being fixed:**
{list concerns with locations}
```

- Record concerns in task context
- Loop back to `execute` step automatically (no user prompt)
- Subagent receives concerns as additional requirements
- Repeat until APPROVED or max iterations (3) reached

**If max iterations reached (any trust level):**
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

Skip if `trust: "high"` in config.

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

Present work summary with adventure-style checkpoint.

**CRITICAL: Output directly WITHOUT code blocks (M125).** Markdown `**bold**` renders correctly
when output as plain text, but shows as literal asterisks inside triple-backtick code blocks.

Output format (do NOT wrap in ```):

✅ **CHECKPOINT: Task Complete**
╭──────────────────────────────────────────────────────────────────────────────────────────────╮
│                                                                                              │
│  **Quest:** {task-name}                                                                      │
│  **Approach:** {selected approach from choose_approach step}                                 │
│                                                                                              │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│  **Time:** {N} minutes | **Tokens:** {N} ({percentage}% of context)                          │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│  **Branch:** {task-branch}                                                                   │
│                                                                                              │
╰──────────────────────────────────────────────────────────────────────────────────────────────╯

**Anti-pattern (M089):** Presenting subagent branch (e.g., `task-sub-uuid`) instead of task branch.
Users review the task branch which contains merged subagent work, not the internal subagent branch.

**CRITICAL:** Token metrics MUST be included. If unavailable (e.g., `.completion.json` not found),
parse session file directly or report "Metrics unavailable - manual review recommended."

Use AskUserQuestion with adventure-style options:
- header: "Next Step"
- question: "What would you like to do?"
- options:
  - "✓ Approve and merge" - Merge to main, continue adventure
  - "🔍 Review changes first" - I'll examine the diff
  - "✏️ Request changes" - Need modifications before proceeding
  - "✗ Abort" - Discard work entirely

**If "Review changes first":**
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

# Optionally delete branch if autoRemoveWorktrees is true
git branch -d "{task-branch}" 2>/dev/null || true

# Release task lock (substitute actual SESSION_ID from context)
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" release "$TASK_ID" "${CLAUDE_SESSION_ID}"
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

**MANDATORY: Provide next steps to user (M120).**

After cleanup, ALWAYS show user their available options. Never end with just "Task Complete" -
user needs guidance on what to do next.

**Find next executable task** (pending + dependencies met + not locked).

**MANDATORY: Try to acquire lock before offering next task.**

For each candidate task:
1. Check status is `pending` or `in-progress`
2. Check all dependencies are `completed`
3. **Try to acquire lock** - skip if locked by another session

```bash
NEXT_TASK_ID="${MAJOR}.${MINOR}-${NEXT_TASK_NAME}"

# Try to acquire lock for next task
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" acquire "$NEXT_TASK_ID" "${CLAUDE_SESSION_ID}")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  # This task is locked, try the next candidate
  continue
fi

# Lock acquired - keep it for auto-continue or release for manual
```

**Auto-continue behavior (trust >= medium):**

```bash
TRUST_LEVEL=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

| Trust Level | Behavior |
|-------------|----------|
| `high` | Auto-continue to next task immediately (no prompt) |
| `medium` | Auto-continue to next task immediately (no prompt) |
| `low` | Show next task, wait for user to invoke `/cat:work` |

**Scope-aware task selection:**

When finding the next task, respect the original WORK_SCOPE:

| WORK_SCOPE | Next Task Selection |
|------------|---------------------|
| `task` | **STOP** - single task complete, don't continue |
| `minor` | Find next task in same minor version only |
| `major` | Find next task in same minor, or first task in next minor within same major |
| `all` | Find next task anywhere (current minor → next minor → next major) |

```bash
case "$WORK_SCOPE" in
  "task")
    # Single task mode - do not auto-continue
    NEXT_TASK=""
    ;;
  "minor")
    # Find next task only within v{WORK_TARGET}
    NEXT_TASK=$(find_next_task_in_minor "$WORK_TARGET")
    ;;
  "major")
    # Find next task in current minor, or advance to next minor in v{WORK_TARGET}.x
    NEXT_TASK=$(find_next_task_in_major "$WORK_TARGET")
    ;;
  "all")
    # Find next task anywhere
    NEXT_TASK=$(find_next_task_globally)
    ;;
esac
```

**If trust >= medium and next task found (within scope):**

```
╭─── ✓ Task Complete ───────────────────────────────────────╮
│                                                            │
│  **{task-name}** merged to main.                           │
│                                                            │
├────────────────────────────────────────────────────────────┤
│  **Next:** {next-task-name}                                │
│  {goal from PLAN.md}                                       │
│                                                            │
│  Auto-continuing in 3s...                                  │
│  • Type "stop" to pause after this task                    │
│  • Type "abort" to cancel immediately                      │
├────────────────────────────────────────────────────────────┤
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

**Brief pause for user intervention:**

After displaying the message, pause briefly (3 seconds conceptually) to allow user to type:
- **"stop"** or **"pause"** → Complete current display, do NOT start next task
- **"abort"** or **"cancel"** → Stop immediately, release locks, clean up

If no input received, **loop back to find_task step** with the next task, continuing execution.

**Note:** In practice, this is a conceptual pause. The agent displays the message and checks if the user
has sent a follow-up message before proceeding. If user says "stop", the agent acknowledges and ends
the work session gracefully.

**If scope complete (no more tasks within scope):**

```
╭─── ✓ Scope Complete ──────────────────────────────────────╮
│                                                            │
│  **{scope description}** - all tasks complete!             │
│                                                            │
│  {For minor: "v0.5 complete"}                              │
│  {For major: "v0.x complete"}                              │
│  {For all: "All versions complete!"}                       │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

**If trust == low and next task found:**

Release the lock (user will re-acquire when they invoke the command):

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" release "$NEXT_TASK_ID" "${CLAUDE_SESSION_ID}"
```

```
╭─── ✓ Task Complete ───────────────────────────────────────╮
│                                                            │
│  **{task-name}** merged to main.                           │
│                                                            │
├────────────────────────────────────────────────────────────┤
│  **Next Up:** {next-task-name}                             │
│  {goal from PLAN.md}                                       │
│                                                            │
│  `/cat:work` to continue                                   │
├────────────────────────────────────────────────────────────┤
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

If no more tasks in the current minor version (all completed, blocked, or locked):

**→ Load version-completion.md workflow for full handling.**

See `.claude/cat/workflows/version-completion.md` for:
- Minor version completion check and celebration
- Stakeholder review prompt
- Major version completion check
- Next steps guidance

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

**MANDATORY: User review before merge (unless trust: "high").**

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

**MANDATORY: Main agent delegates ALL work phases to subagents.**

The main agent is an ORCHESTRATOR. All phases MUST be delegated to subagents:

| Phase | Delegate To | Anti-Pattern |
|-------|-------------|--------------|
| Exploration | Exploration subagent | M088: Main agent reading source files directly |
| Planning | Planning subagent | M091: Main agent making architectural decisions |
| Implementation | Implementation subagent | M063: Main agent editing source code |

**Main agent responsibilities (ONLY these):**
- Read orchestration files (STATE.md, PLAN.md, CHANGELOG.md)
- Run diagnostic commands (git status, build output, test results)
- Edit orchestration files (STATE.md, PLAN.md, CHANGELOG.md)
- Present summaries and ask questions to user
- Invoke skills and spawn subagents
- Aggregate and present subagent findings

**Subagent responsibilities (ALL substantive work):**
- **Exploration subagent:** Read and analyze source code, report findings
- **Planning subagent (two stages):**
  - Stage 1: Produce high-level outlines for three approaches (lightweight)
  - Stage 2: (resumed) Produce detailed spec for selected approach
- **Implementation subagent:** Write/edit source code, tests, fix bugs

**Orchestration Enforcement (A014):**

Before any file read or code analysis, ask: "Should a subagent do this?"

| Action | Main Agent OK? | Why |
|--------|----------------|-----|
| Read STATE.md | ✅ Yes | Orchestration file |
| Read Parser.java | ❌ No | Delegate to exploration subagent |
| Decide which API to use | ❌ No | Delegate to planning subagent |
| Write test code | ❌ No | Delegate to implementation subagent |
| Present approval gate | ✅ Yes | Orchestration action |

**Correct workflow pattern (two-stage planning for token efficiency):**

```
1. Main agent spawns EXPLORATION subagent: "Find all X and report findings"
2. Exploration subagent returns: "Found X at locations A, B, C with patterns..."

PLANNING STAGE 1 (lightweight):
3. Main agent spawns PLANNING subagent: "Produce HIGH-LEVEL outlines for three approaches"
4. Planning subagent returns brief summaries (save agent_id for resumption):
   - Conservative: [1-2 sentence scope, risk: LOW]
   - Balanced: [1-2 sentence scope, risk: MEDIUM]
   - Aggressive: [1-2 sentence scope, risk: HIGH]
   - agent_id: {uuid} ← SAVE THIS

APPROACH SELECTION:
5. Main agent selects approach (auto-select if preference matches, else ask user)

PLANNING STAGE 2 (detailed):
6. Main agent RESUMES planning subagent (using saved agent_id):
   "User selected [approach]. Now produce the DETAILED implementation spec."
7. Planning subagent returns comprehensive PLAN.md with execution steps

IMPLEMENTATION:
8. Main agent spawns IMPLEMENTATION subagent: "Execute this spec mechanically"
9. Implementation subagent returns: "Completed. Commits: ..."
10. Main agent presents approval gate to user
```

**Why two-stage planning:**
- Stage 1 uses ~5K tokens (outlines only)
- Selection happens with minimal context
- Stage 2 uses ~20K tokens (detailed spec)
- Total: ~25K vs ~60K if all three approaches were fully detailed

**Anti-pattern (M088):** Main agent reading source files "to understand the code" - delegate to exploration subagent.

**Anti-pattern (M091):** Main agent deciding "we should use pattern X" - delegate to planning subagent.

**Anti-pattern (M089):** Presenting subagent branch instead of task branch in approval gate.

**When user provides feedback requiring code changes:**

1. **Spawn a new subagent** with:
   - The feedback as context
   - Clear instructions on what to fix
   - The existing branch/worktree
2. **Use** `/cat:spawn-subagent` or continue in existing subagent

**Required behavior (M063):** "I'll spawn a subagent to address that feedback." [invokes spawn-subagent]

**Exception:** Trivial STATE.md updates that are purely orchestration (status changes, not code).

<pre_edit_checkpoint>

**MANDATORY Pre-Edit Self-Check (M088):**

BEFORE using the Edit tool on ANY source file (.java, .md code docs, etc.), STOP and verify:

1. **Am I the main agent?** (orchestrating a CAT task)
2. **Is this a source/documentation file?** (not STATE.md, PLAN.md, CHANGELOG.md)
3. **Is a subagent already running or could one be spawned?**

If answers are YES/YES/YES → **SPAWN SUBAGENT INSTEAD**

**This applies even for "simple" changes:**
- Variable renaming → subagent
- Comment updates → subagent
- Style fixes → subagent
- Convention updates to style guides → subagent

**Rationale:** "Simple" edits bypass the delegation boundary. If it touches code, delegate it.

</pre_edit_checkpoint>

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

**→ Load duplicate-task.md workflow when task is discovered to be duplicate.**

See `.claude/cat/workflows/duplicate-task.md` for full handling including:
- Signs of a duplicate task
- Verification process
- STATE.md resolution format
- Commit message format (no Task ID footer)
- Cleanup and next task flow

**Quick reference:** Set `resolution: duplicate` and `Duplicate Of: v{major}.{minor}-{original-task}` in STATE.md.

</duplicate_task_handling>

<success_criteria>

- [ ] **Task lock acquired BEFORE offering task (M097)**
- [ ] Task identified and loaded
- [ ] **Entry gate evaluated (blocked if unmet, unless --override-gate)**
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
- [ ] **Next task offered (lock checked first)**

</success_criteria>
