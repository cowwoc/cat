<execution_context>

@${CLAUDE_PLUGIN_ROOT}/concepts/version-paths.md

</execution_context>

# Workflow: Execute Task

## Overview
Detailed workflow for executing a task from start to completion.

## Prerequisites
- Task exists with STATE.md, PLAN.md
- All task dependencies completed
- Main agent in orchestration mode
- **Task lock can be acquired** (not locked by another session)

## Subagent Batching Standards

**Hide tool calls by delegating batched operations to subagents.**

Subagent internal tool calls are invisible to the parent conversation. Instead of 20+
visible Read/Bash calls, users see 3-5 Task tool invocations with clean output.

See `@references/workflow-output.md` for complete batching strategy.

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

## Workflow Steps

### 1. Validate Task Ready and Acquire Lock

**Batched into Preparation subagent** - see Subagent Batching Standards above

```
Check STATE.md
    |
    +---> Status: pending or in-progress
    |
    +---> Task Dependencies: All completed
    |
    +---> Minor Version Dependency: Met
    |
    +---> Try to Acquire Lock (M097)
    |         |
    |         +---> If locked by another session: SKIP task, try next
    |         |
    |         +---> If lock acquired: Proceed
    |
    v
Proceed to execution
```

**MANDATORY: Lock Check Before Proceeding (M097)**

Before validating a task as executable, attempt to acquire its lock:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
# Session ID is auto-substituted as ${CLAUDE_SESSION_ID}

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

**Minor version dependency rules:**

| Scenario | Dependency |
|----------|------------|
| First minor of first major (v0.0) | None - always executable |
| Subsequent minors (e.g., v0.5) | Previous minor must be complete (v0.4) |
| First minor of new major (e.g., v1.0) | Last minor of previous major must be complete |

A minor is complete when all its tasks have `status: completed`.

If blocked:
- Identify blocking task dependencies
- Identify blocking minor version dependency
- **Check if task is locked by another session**
- Report to user or execute blockers first

### 2. Analyze Task Size and Auto-Decompose

**Batched into Preparation subagent** - see Subagent Batching Standards above

**MANDATORY: Estimate task complexity before execution.**

```yaml
# Context limits are fixed - see agent-architecture.md § Context Limit Constants

# Estimate task size - INCLUDE ALL PHASES
estimation_factors:
  # Fixed costs per subagent phase
  exploration_subagent: 10000    # codebase analysis
  planning_subagent: 15000       # approach design
  stakeholder_review: 5000       # per reviewer (5 reviewers typical)

  # Variable costs from PLAN.md (implementation phase)
  files_to_create: count × 5000
  files_to_modify: count × 3000
  test_files: count × 4000
  plan_steps: count × 2000
  exploration_buffer: 10000 if uncertain

# Total = exploration + planning + implementation + review
estimated_tokens: 10000 + 15000 + (implementation factors) + 25000
```

**If estimated_tokens > threshold:**

```
AUTO-DECOMPOSITION TRIGGERED

Estimated: ~85,000 tokens
Threshold: 80,000 tokens (40% of 200,000)

Invoking /cat:decompose-task...
```

1. Invoke decompose-task skill automatically
2. Decompose-task creates subtasks with dependencies
3. Decompose-task generates parallel execution plan
4. If parallel tasks exist, invoke parallel-execute:

```
Parallel Execution Plan:
  Sub-task 1: [1.2a-parser-lexer, 1.2c-parser-tests] (concurrent)
  Sub-task 2: [1.2b-parser-ast] (after sub-task 1)

Spawning 2 subagents for sub-task 1...
```

**If estimated_tokens <= threshold:**

```
Task size OK: ~65,000 tokens (81% of threshold)
Proceeding with single subagent.
```

Continue to create worktree step.

### 3. Create Task Worktree

**Batched into Preparation subagent** - see Subagent Batching Standards above

**MANDATORY: Check for existing worktree before creating (M236)**

An existing worktree strongly indicates work is in progress by another session (the lock may be
missing if that session crashed or ended abnormally). Before proceeding:

```bash
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/{major}.{minor}-{task-name}"

if [ -d "$WORKTREE_PATH" ]; then
  # Check if there are commits on the task branch not on base
  cd "$WORKTREE_PATH"
  BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-base" 2>/dev/null || echo "main")
  COMMIT_COUNT=$(git rev-list --count ${BASE_BRANCH}..HEAD 2>/dev/null || echo "0")

  if [ "$COMMIT_COUNT" -gt 0 ]; then
    echo "⚠️ EXISTING WORKTREE WITH COMMITS DETECTED"
    echo "Worktree: $WORKTREE_PATH"
    echo "Commits ahead of base: $COMMIT_COUNT"
    echo ""
    echo "This indicates another session may have started work on this task."
    # MUST use AskUserQuestion - do NOT proceed automatically
  fi
  cd -
fi
```

**If worktree exists with commits, use AskUserQuestion:**
- header: "Existing Work Detected"
- question: "Worktree exists with {N} commit(s). How would you like to proceed?"
- options:
  - "Skip this task" - Find another task to work on (Recommended)
  - "Resume" - Continue from existing work (keep commits)
  - "Start fresh" - Delete worktree and start over (loses existing work)
  - "Abort" - Stop and investigate manually

**If "Skip this task":** Release the lock for this task and return to find_task step to find
the next available task. This is the safest option when another session may still be active.

**Only proceed to create worktree if it does NOT exist:**

```bash
# Main agent creates task branch and worktree
git worktree add ../cat-worktree-{task-name} -b {major}.{minor}-{task-name}

# MANDATORY: Update lock with worktree path (M196)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/{major}.{minor}-{task-name}"
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" update "${CLAUDE_PROJECT_DIR}" "$TASK_ID" "${CLAUDE_SESSION_ID}" "$WORKTREE_PATH"

# MANDATORY: Change to worktree directory for task execution
cd ../cat-worktree-{task-name}
pwd  # Verify we're in the worktree
```

Update STATE.md:
- Status: `in-progress`
- Last Updated: current timestamp

### 4. Delegate Exploration (with Duplicate Detection)

**CRITICAL**: Main agent is orchestrator only. Delegate ALL work to subagents.

Main agent does NOT read code files directly. Spawn an exploration subagent:

```
Task tool invocation:
  description: "Explore {task} implementation"
  subagent_type: "Explore"
  prompt: |
    Analyze code for {task-name}.

    **DUPLICATE CHECK (FIRST):**
    Before exploring, check if functionality already exists:
    1. Search for key methods/classes mentioned in PLAN.md
    2. Check if tests exist for scenarios in STATE.md
    3. If functionality exists AND tests pass:
       Return "DUPLICATE: [evidence]" and STOP immediately

    **If NOT duplicate, continue with:**
    1. Relevant method locations and signatures
    2. Current implementation patterns
    3. Existing test coverage
    4. Integration points

    RETURN FINDINGS ONLY. Do NOT implement changes.
```

**If exploration returns DUPLICATE:**

Skip remaining steps. Mark task as duplicate:
1. Update STATE.md: status=completed, resolution=duplicate
2. Commit STATE.md only (no Task ID footer)
3. Cleanup worktree, release lock
4. Offer next task

This saves ~10-15 minutes by avoiding unnecessary planning and implementation subagents.

### 5. Delegate Planning (M091)

**CRITICAL**: Main agent does NOT make decisions or write code. Delegate planning to a subagent.

After receiving exploration findings, spawn a planning subagent:

```
Task tool invocation:
  description: "Plan {task} implementation"
  subagent_type: "Plan"
  prompt: |
    Based on these exploration findings:
    {exploration_results}

    And the task PLAN.md:
    {plan_md_content}

    Create a detailed implementation specification:
    1. Make all architectural/design decisions
    2. Write explicit code examples (actual code, not descriptions)
    3. Specify exact file paths to modify
    4. Define verification steps with expected output
    5. Determine error handling approaches
    6. Compose the commit message

    RETURN IMPLEMENTATION SPEC ONLY. Do NOT implement changes.
```

### 6. Spawn Implementation Subagent

Main agent spawns implementation subagent with:
- Planning subagent's implementation specification
- Worktree path
- Session tracking info
- Token monitoring instructions

Subagent branch:
```
{major}.{minor}-{task-name}-sub-{uuid}
```

### 7. Subagent Execution

Subagent in worktree:
1. Read PLAN.md execution steps
2. Implement changes following TDD
3. Run tests continuously
4. Track token usage
5. Commit changes to subagent branch

Token tracking:
- Read session JSONL file
- Sum input_tokens + output_tokens
- Count compaction events

### 8. Subagent Completion

On completion, subagent returns via `.completion.json`:
```json
{
  "status": "success|failure",
  "tokensUsed": 75000,
  "compactionEvents": 0,
  "summary": "Implemented switch statement parsing"
}
```

### 9. MANDATORY: Report Token Metrics to User

**Uses collect-results skill** - already batched

**Invoke `/cat:collect-results` to get authoritative token metrics (M146).**

After the Task tool completes, invoke `/cat:collect-results` to extract accurate token usage.
This skill reads the session file and returns structured JSON with actual metrics.

**MANDATORY: Use skill-derived metrics, NOT estimates or manual parsing.**

```bash
# Invoke the collect-results skill to get authoritative metrics
/cat:collect-results
```

The skill returns:
- `tokensUsed`: Actual tokens from session file (authoritative)
- `compactionEvents`: Number of context compaction events
- `commits`: List of commits made by subagent
- `filesChanged`: Files modified with line counts

**Anti-pattern:** Manually parsing the Task tool output summary line (e.g., "Done (14 tool uses ·
27.8k tokens)"). This is unreliable - agents often ignore manual parsing instructions and report
pre-execution estimates instead. Always invoke the skill.

**Anti-pattern:** Using the pre-execution task size ESTIMATE from step 5 as the reported value.
The estimate is for decomposition decisions. The skill output shows ACTUAL usage.

**After collecting subagent results, ALWAYS present token metrics to user:**

```
## Subagent Execution Report

**Task:** {task-name}
**Status:** {success|partial|failed}

**Token Usage:**
- Total tokens: 75,000 (37.5% of 200K context)
- Compaction events: 0
- Execution quality: Good ✓

**Work Summary:**
- Commits: 3
- Files changed: 5
- Lines: +450 / -120
```

**Why mandatory:**
- Users cannot observe subagent execution in real-time
- Token metrics are the only visibility into execution quality
- Compaction events indicate potential quality degradation
- Enables informed decisions about decomposition

**If compaction events > 0:**

```
⚠️ CONTEXT COMPACTION DETECTED

Compaction events: 2
Execution quality: DEGRADED - context was summarized during execution

RECOMMENDATION: Invoke /cat:decompose-task for remaining similar work.
The subagent may have lost context and produced lower quality output.
```

Present AskUserQuestion with decomposition as recommended option.

### 9b. MANDATORY: Verify Acceptance Criteria (M277)

**CRITICAL: Before proceeding to finalization, verify ALL acceptance criteria from PLAN.md have evidence.**

```bash
PLAN_PATH=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${TASK_NAME}/PLAN.md"
CRITERIA=$(grep -A20 "## Acceptance Criteria" "$PLAN_PATH" | grep "^\- \[" || echo "")
```

**For each criterion:**
1. Check if subagent output contains evidence of satisfaction
2. If evidence missing, obtain it (method depends on criterion type - see skill docs)
3. If criterion is unmet, FAIL-FAST before approval gate

**FAIL-FAST on missing evidence:**
```
❌ ACCEPTANCE CRITERIA NOT MET

Required: {criterion from PLAN.md}
Evidence: {missing | actual value}

BLOCKING: Cannot proceed to approval without validation evidence.
```

**Why M277 exists:** Without explicit verification step, main agent may skip to approval without evidence.

## Why Finalization Uses Direct Execution (Not Subagent Batching)

**Finalization phase (steps 10-16) uses direct execution instead of subagent batching.**

Unlike Exploration, Planning, and Implementation phases:

1. **Happens AFTER user approval** - User has already reviewed and approved all changes
2. **Minimal tool calls** - Only 3-5 operations (merge, cleanup, state updates) vs 20+ in exploration
3. **Low user benefit from hiding** - No noise to hide; operations are already approved
4. **Better error handling** - Merge conflicts or cleanup failures should surface immediately for user intervention
5. **Simplicity** - Direct execution is simpler than subagent overhead for post-approval cleanup

**Result:** Users see straightforward cleanup steps after approval, not wrapped in a Task tool invocation.

### 10. Main Agent Merge

**Direct execution** - Finalization steps run after user approval with minimal tool calls.

```bash
# In task worktree
git merge {subagent-branch} --ff-only
```

If conflicts:
- Attempt automatic resolution
- Escalate to user if unresolved

### 11. Cleanup Subagent Resources

**Direct execution** - Finalization steps run after user approval with minimal tool calls.

**After merging subagent branch to task branch, cleanup BEFORE approval gate:**

```bash
# Remove subagent worktree
git worktree remove {subagent-worktree-path}

# Delete subagent branch
git branch -d {subagent-branch}
```

This ensures:
- Only the task branch remains for review
- No orphaned worktrees/branches if user rejects
- Clean state for approval decision

### 12. Update State

**Direct execution** - Finalization steps run after user approval with minimal tool calls.

**MANDATORY (M153): Set STATE.md to FINAL state before approval gate.**

Files proposed for merge should reflect their final state. Update task STATE.md to completed:

```markdown
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Completed:** {YYYY-MM-DD HH:MM}
- **Tokens Used:** {N}
```

This ensures the commit being approved already shows task completion.

### 13. Approval Gate (Interactive Mode)

**Interactive - not batched** (needs user response)

Present to user:
- Summary of changes
- Files modified
- Branch for review
- Test results

**MANDATORY: Show Diff to User**

Before asking for approval, display the changes using the configured terminal width:

```bash
# Read terminal width from config (default 50)
WIDTH=$(jq -r '.terminalWidth // 50' .claude/cat/cat-config.json)
```

**Diff Format (Variant 2 - Isolated Changes):**

Use this format which isolates only the changed phrases while preserving context:

```
## {Section Name} (lines {start}-{end})
──────────────────────────────────────────────────

  {unchanged context, wrapped to $WIDTH}

- {removed text only}
+ {added text only}

  {more unchanged context}

──────────────────────────────────────────────────
```

**Example at 50 chars:**
```
## Governing Law (lines 234-238)
──────────────────────────────────────────────────

  This license shall be governed
  by and construed in accordance
  with the laws of

- Delaware,
+ the jurisdiction in which the
+ Licensor resides,

  without regard to its conflict
  of law provisions.

──────────────────────────────────────────────────
```

**Why this format:**
- Unchanged context is unmarked (easy to skip)
- Only actual changes get +/- markers
- Works well when lines wrap at narrow widths
- Clearer than marking entire wrapped lines

**Structure: File by File, Section by Section**

Show changes organized hierarchically.

```
╭─ Task Diff: {task-name} ────────────────────────╮
│ Files: {count} │ +{added}/-{removed} lines      │
╰─────────────────────────────────────────────────╯

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FILE 1/N: {filename}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## Section: {section name} (lines {start}-{end})
──────────────────────────────────────────────────

  {unchanged context}

- {removed text}
+ {added text}

  {unchanged context}

──────────────────────────────────────────────────

## Section: {next section} (lines {start}-{end})
──────────────────────────────────────────────────

  {context and changes...}

──────────────────────────────────────────────────

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FILE 2/N: {next filename}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

{sections...}
```

**Section identification rules:**
- For Markdown: Use headings (##) as section boundaries
- For code: Use function/class definitions as boundaries
- For config: Use top-level keys as boundaries
- Include 2-3 lines of unchanged context around changes

**CRITICAL: Approval Protocol**

1. Use AskUserQuestion with explicit "Approve" / "Reject" options
2. Wait for explicit approval response
3. **If user provides feedback instead of approval:**
   - **DELEGATE fixes to a new subagent** - main agent does NOT implement directly (M063)
   - Address the feedback via subagent execution
   - **RE-PRESENT the approval gate** with updated changes
   - **Feedback is NOT approval** - do not proceed to merge
4. Only proceed to Final Merge after explicit "Approve" response

```
Approval Gate Flow:

Present changes → User responds
                      |
        +-------------+-------------+
        |                           |
    "Approve"                   Feedback/Request
        |                           |
        v                           v
    Proceed to              Address feedback
    Final Merge                     |
                                    v
                            RE-PRESENT approval gate
                                    |
                                    v
                            (loop until Approve/Reject)
```

**Anti-pattern (M052):** Interpreting feedback as implicit approval and merging without re-confirmation.

### 14. Final Merge

**Direct execution** - Finalization steps run after user approval with minimal tool calls.

After approval:

**CRITICAL (M070/M090): Update STATE.md AND CHANGELOG.md BEFORE squashing (same commit as implementation)**

Per commit-types.md, task STATE.md changes must be in the SAME commit as implementation.
Minor version CHANGELOG.md should also be updated in the same commit for atomicity.

```bash
# In task worktree - update STATE.md to completed
# Edit .claude/cat/issues/v{major}/v{major}.{minor}/{task-name}/STATE.md:
#   status: completed
#   progress: 100%
#   resolution: implemented
#   completed: {date}

# In task worktree - update minor version CHANGELOG.md
# Edit .claude/cat/issues/v{major}/v{major}.{minor}/CHANGELOG.md:
# Add task entry to Tasks Completed table:
#   | {task-name} | {commit-type} | {description from PLAN.md} | implemented |

# Stage STATE.md and CHANGELOG.md with implementation
git add .claude/cat/issues/v{major}/v{major}.{minor}/{task-name}/STATE.md
git add .claude/cat/issues/v{major}/v{major}.{minor}/CHANGELOG.md
git commit --amend --no-edit  # Include in last implementation commit

# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
[[ ! -f "$CAT_BASE_FILE" ]] && echo "ERROR: cat-base file missing. Recreate worktree." && exit 1
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

# Squash commits by type
git rebase -i "$BASE_BRANCH"  # Group by feature, bugfix, refactor, etc.

# Merge to base branch from worktree (no checkout needed)
git push . "HEAD:${BASE_BRANCH}"
```

**Anti-pattern (M090):** Committing CHANGELOG.md as separate commit after merge.

**If push fails (M081):** Base branch has diverged from task branch base.

```bash
# DO NOT merge base into task branch (creates non-linear history)
# INSTEAD: Rebase task branch onto base first

# In task worktree:
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
[[ ! -f "$CAT_BASE_FILE" ]] && echo "ERROR: cat-base file missing. Recreate worktree." && exit 1
BASE_BRANCH=$(cat "$CAT_BASE_FILE")
git fetch origin
git rebase "origin/$BASE_BRANCH"  # Or use /cat:git-rebase skill

# Then merge with fast-forward from worktree
git push . "HEAD:${BASE_BRANCH}"
```

**Anti-pattern (M081):** Merging base INTO task branch creates merge commits and non-linear history.

**Anti-pattern (M070):** Committing STATE.md update as separate "planning:" commit after merge.

### 15. Update Parent State (Rollup Only) - IN WORKTREE

**Direct execution** - Finalization steps run after user approval with minimal tool calls.

**CRITICAL (M230): Parent STATE.md updates MUST happen IN THE WORKTREE before merge.**

All related changes (implementation + task STATE.md + parent STATE.md rollup) must be committed
together in the worktree. This ensures a single squashed commit contains all task-related updates.

**Still in task worktree** (NOT main workspace):

```bash
pwd  # Verify still in worktree: .worktrees/{task-branch}
```

This step handles:
1. Parent STATE.md progress rollup (minor/major)
2. Major version CHANGELOG.md (if minor version completes)

**NOTE**: Minor version CHANGELOG.md was already updated in step 13 with the implementation commit.

**MANDATORY: Validate Before Marking Minor Version Complete (M150)**

Before setting a minor version's STATE.md to `status: completed`, verify all nested tasks are complete:

```bash
MINOR_PATH=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}"
TOTAL_TASKS=$(find "$MINOR_PATH" -mindepth 1 -maxdepth 1 -type d -exec test -f {}/STATE.md \; -print 2>/dev/null | wc -l)
COMPLETED_TASKS=$(find "$MINOR_PATH" -mindepth 1 -maxdepth 1 -type d -exec grep -l "\*\*Status:\*\*.*completed" {}/STATE.md \; 2>/dev/null | wc -l)

if [[ "$COMPLETED_TASKS" -lt "$TOTAL_TASKS" ]]; then
  echo "⚠️ Cannot mark minor complete: $((TOTAL_TASKS - COMPLETED_TASKS))/$TOTAL_TASKS tasks still pending"
  # Do NOT update minor STATE.md to completed
else
  # Safe to mark minor version as completed
  # Update minor STATE.md: - **Status:** completed
fi
```

**Anti-pattern (M150):** Marking minor version complete without verifying all nested tasks are complete.

**MANDATORY: Validate Before Marking Major Version Complete (M150)**

Before setting a major version's STATE.md to `- **Status:** completed`, verify all nested minor versions are complete:

```bash
MAJOR_PATH=".claude/cat/issues/v${MAJOR}"
TOTAL_MINORS=$(find "$MAJOR_PATH" -mindepth 1 -maxdepth 1 -type d -name "v${MAJOR}.*" -exec test -f {}/STATE.md \; -print 2>/dev/null | wc -l)
COMPLETED_MINORS=$(find "$MAJOR_PATH" -mindepth 1 -maxdepth 1 -type d -name "v${MAJOR}.*" -exec grep -l "\*\*Status:\*\*.*completed" {}/STATE.md \; 2>/dev/null | wc -l)

if [[ "$COMPLETED_MINORS" -lt "$TOTAL_MINORS" ]]; then
  echo "⚠️ Cannot mark major complete: $((TOTAL_MINORS - COMPLETED_MINORS))/$TOTAL_MINORS minor versions still pending"
  # Do NOT update major STATE.md to completed
else
  # Safe to mark major version as completed
  # Update major STATE.md: status: completed
fi
```

**Anti-pattern (M150):** Marking major version complete without verifying all nested minor versions are complete.

**Major version CHANGELOG.md** (`.claude/cat/issues/v{major}/CHANGELOG.md`):

Update aggregate summary only when a minor version completes (all tasks done).

**CHANGELOG table format** (minor version):
```markdown
| Task | Type | Description | Resolution |
|------|------|-------------|------------|
| {task-name} | {commit-type} | {description from PLAN.md} | implemented |
```

If CHANGELOG.md doesn't exist yet, create it using the template format with:
- Version header and pending status
- Empty Tasks Completed table with correct column order
- Placeholder sections

**Commit parent STATE.md updates in worktree:**

```bash
# Still in worktree - include parent STATE.md in the squashed commit
git add .claude/cat/issues/v${MAJOR}/STATE.md
git add .claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/STATE.md
git commit --amend --no-edit  # Include in the same commit as implementation
```

**Anti-pattern (M230):** Updating parent STATE.md AFTER merge in main workspace. This creates a
separate commit that requires manual squashing.

### 16. Cleanup

**Direct execution** - Finalization steps run after user approval with minimal tool calls.

```bash
# MANDATORY: Return to main workspace before removing worktree
cd /workspace
pwd  # Verify we're in main workspace (not worktree)

# Task worktree and branch (subagent already cleaned in step 10)
git worktree remove ../cat-worktree-{task-name}
git branch -d {task-branch}
```

### 17. Version Boundary Gate (Next Task Selection)

When auto-continuing to the next task, detect if the next task is in a different version:

**Version Boundary Detection:**

Supports all versioning schemes (MAJOR, MAJOR.MINOR, or MAJOR.MINOR.PATCH):

> **See also:** [version-scheme.md](version-scheme.md) for scheme detection and boundary rules.

```bash
# Track completed task version (empty values for unused levels)
COMPLETED_MAJOR="{major from completed task}"
COMPLETED_MINOR="{minor from completed task, empty if major-only}"
COMPLETED_PATCH="{patch from completed task, empty if no patch level}"

# After finding next task
NEXT_MAJOR=$(echo "$NEXT_TASK_RESULT" | jq -r '.major')
NEXT_MINOR=$(echo "$NEXT_TASK_RESULT" | jq -r '.minor // empty')
NEXT_PATCH=$(echo "$NEXT_TASK_RESULT" | jq -r '.patch // empty')
```

**Boundary detection adapts to versioning scheme:**

| Scheme | Boundary Crossed When |
|--------|----------------------|
| MAJOR only | `COMPLETED_MAJOR != NEXT_MAJOR` |
| MAJOR.MINOR | `COMPLETED_MAJOR != NEXT_MAJOR OR COMPLETED_MINOR != NEXT_MINOR` |
| MAJOR.MINOR.PATCH | Any component differs |

**If BOUNDARY_CROSSED is true:**

1. Display **VERSION_BOUNDARY_GATE** box showing:
   - Completed version summary
   - Tasks completed count
   - Reminder to consider publishing/tagging
   - Next version and task preview

2. Present AskUserQuestion with options:
   - "Continue to next version" - Proceed with auto-continue
   - "Exit to publish first" - Exit work loop for user to publish/release
   - "Stop" - Exit work loop immediately

**Why this gate exists:**
- Allows users to publish/release completed versions before moving on
- Prevents accidentally starting work on a new version without releasing the previous one
- Provides a natural checkpoint for git tagging and documentation updates

**If user selects "Continue to next version":**
Proceed with existing auto-continue logic.

**If user selects "Exit to publish first" or "Stop":**
Release lock, exit workflow gracefully.

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
