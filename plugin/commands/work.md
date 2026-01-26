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
**MANDATORY: Use output template progress format from handler.**

Check for "OUTPUT TEMPLATE WORK PROGRESS FORMAT" in context and render progress using those templates.

**CRITICAL (M246/M257):** Use box templates from OUTPUT TEMPLATE WORK BOXES section verbatim.
- Locate the specific box by name (e.g., "--- CHECKPOINT_TASK_COMPLETE ---")
- Use that exact structure - do NOT manually reconstruct box characters (│╭╮╰╯├┤─)
- Replace ONLY placeholder text inside the box (e.g., {task-name} → actual value)
- Manual typing of box characters causes alignment problems

When the instructions say "Use the **XXX** box from OUTPUT TEMPLATE WORK BOXES":
1. Search conversation for "--- XXX ---" in the system-reminder
2. Copy the ENTIRE box after that marker (all lines with ╭╮╰╯│ characters)
3. Paste it EXACTLY - do NOT manually type or reconstruct it
4. Only replace placeholder text like `{task-name}` with actual values

**If OUTPUT TEMPLATE WORK PROGRESS FORMAT found:**
Use the templates directly. Locate box by name, preserve structure exactly.

### Phase Mapping

| Phase | Steps Included | Complete When |
|-------|----------------|---------------|
| Preparing | verify, find_task, acquire_lock, load_task, validate_requirements, analyze_task_size, choose_approach, create_worktree | Worktree created, ready to execute |
| Executing | execute, collect_and_report, token_check, handle_discovered_issues, verify_changes | Subagent complete, changes verified |
| Reviewing | stakeholder_review, approval_gate | Review passed, user approved |
| Merging | squash_commits, merge, cleanup, update_state, commit_metadata, update_changelogs, next_task | Merged to main, cleanup done |

### Key Principles

1. **Use templates from handler** - Render progress inline using provided formats
2. **Full task ID required** - Format: `{major}.{minor}-{task-name}`
3. **4 phases, not 17 steps** - Users see meaningful stages, not micro-steps
4. **Update at transitions** - Display progress banner when phase changes

</progress_output>

<execution_context>

<!-- SKILL.md vs PLAN.md (A015/M172): Always reference SKILL.md for skill usage.
     PLAN.md = what to build (task planning). SKILL.md = how to use it (authoritative). -->

@${CLAUDE_PLUGIN_ROOT}/concepts/work.md
@${CLAUDE_PLUGIN_ROOT}/concepts/merge-and-cleanup.md
@${CLAUDE_PLUGIN_ROOT}/concepts/agent-architecture.md
@${CLAUDE_PLUGIN_ROOT}/concepts/subagent-delegation.md
@${CLAUDE_PLUGIN_ROOT}/concepts/commit-types.md
@${CLAUDE_PLUGIN_ROOT}/templates/changelog.md
@${CLAUDE_PLUGIN_ROOT}/skills/spawn-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/skills/merge-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/skills/stakeholder-review/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/stakeholders/index.md

</execution_context>

<conditional_context>

**Load on demand when specific scenarios occur:**

| Scenario | Load Workflow |
|----------|---------------|
| Minor/major version completes | @${CLAUDE_PLUGIN_ROOT}/concepts/version-completion.md |
| Task discovered as duplicate | @${CLAUDE_PLUGIN_ROOT}/concepts/duplicate-task.md |
| Compaction events or high token usage | @${CLAUDE_PLUGIN_ROOT}/concepts/token-warning.md |

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

**Note:** Context limits are fixed (not configurable). See agent-architecture.md for details.

</step>

<step name="find_task">

**Identify task to execute:**

**Optional: Use find-task.sh script**

For programmatic task discovery, use the `find-task.sh` script:

```bash
# Find next available task
RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/find-task.sh" "${CLAUDE_PROJECT_DIR}" --session-id "$SESSION_ID")

# Parse result
if echo "$RESULT" | jq -e '.status == "found"' > /dev/null 2>&1; then
  TASK_ID=$(echo "$RESULT" | jq -r '.task_id')
  TASK_PATH=$(echo "$RESULT" | jq -r '.task_path')
  MAJOR=$(echo "$RESULT" | jq -r '.major')
  MINOR=$(echo "$RESULT" | jq -r '.minor')
  TASK_NAME=$(echo "$RESULT" | jq -r '.task_name')
  echo "✓ Found task: $TASK_ID"
else
  echo "No executable tasks found"
  echo "$RESULT" | jq -r '.message // .status'
fi
```

The script handles argument parsing, version filtering, dependency checks, lock acquisition, and gate evaluation.

**Session ID**: The session ID is automatically available as `${CLAUDE_SESSION_ID}` in this command.
All bash commands below use this value directly.

**Argument parsing:**

| Argument Format | Example | Behavior |
|-----------------|---------|----------|
| (none) | `/cat:work` | Work on first available task in first incomplete minor version |
| `major` | `/cat:work 0` | Work through ALL minor versions in v0 (v0.0 → v0.1 → ...) |
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
- Validate task exists at `.claude/cat/issues/v{major}/v{major}.{minor}/{task-name}/`
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
# Find all task STATE.md files (tasks are directly under minor version directories)
find .claude/cat/issues/v*/v*.*/ -mindepth 2 -maxdepth 2 -name "STATE.md" 2>/dev/null
```

**MANDATORY: Lock Check Before Offering Task (M097)**

For each candidate task (whether from $ARGUMENTS or auto-discovery), attempt to acquire the lock
BEFORE offering it as available:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
# Session ID is auto-substituted

# Try to acquire lock
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" acquire "${CLAUDE_PROJECT_DIR}" "$TASK_ID" "${CLAUDE_SESSION_ID}")

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
- Subsequent minor versions (e.g., v0.5): Previous minor version must be complete
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

**MANDATORY: Copy-paste the exact box from system-reminder (M246)**

Search the conversation context for "--- NO_EXECUTABLE_TASKS ---" in the PRE-COMPUTED WORK BOXES
section. Copy the ENTIRE box structure verbatim - do NOT manually type it. Box characters and
emoji widths vary across terminals; only the pre-computed version is guaranteed aligned.

**MANDATORY: Accept find-task.sh results (M245)**

When find-task.sh returns "no executable tasks", this is the CORRECT answer. Do NOT:
- Manually search for pending tasks to work around this result
- Try to acquire locks on tasks that the script already determined are unavailable
- Second-guess the script's lock checking logic

The script already checks all tasks for: status, dependencies, entry gates, AND locks.
If it says no tasks are available, there are no tasks available.

**Explain why no tasks are available** (provide one or more reasons):
- Tasks blocked by dependencies (list the blocking tasks)
- Tasks have existing worktrees (indicate likely in use by another session)
- Tasks locked by another session
- All tasks completed

**Suggest `/cat:cleanup`** if worktrees exist:
- Present as an option ONLY if user believes the worktrees are stale
- Do NOT assume worktrees are stale or offer to investigate them

**NEVER offer to resume existing worktrees (M239):**
- Existing worktrees are treated as "in use by another session"
- The correct response is to skip them and find alternative work
- If no alternative work exists, inform user and suggest cleanup only if they believe worktrees are stale

**If specific task requested but not found:**

Use the **TASK_NOT_FOUND** box from OUTPUT TEMPLATE WORK BOXES.
Replace `{task-name}` and `{suggestion}` with actual values.

Exit command.

**If no executable task found:**
Use **NO_EXECUTABLE_TASKS** box from OUTPUT TEMPLATE WORK BOXES.
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
(see display-standards.md § Progress Bar Format).

**CRITICAL: Output directly WITHOUT code blocks (M125).** Markdown `**bold**` renders correctly
when output as plain text, but shows as literal asterisks inside triple-backtick code blocks.

Output format (do NOT wrap in ```):

## Task: {task-name}

**Version:** {major}.{minor}
**Status:** {status}
**Progress:** [==========>         ] {progress}%

**Goal:**
{goal from PLAN.md}

</step>

<step name="validate_requirements_coverage">

**MANDATORY: Validate all requirements are covered before execution.**

This step ensures 100% requirements traceability before implementation begins.
Requirements can be defined at any version level (major, minor, or patch), and this
validation works regardless of which level the task's parent version is.

**Extract requirements from PLAN.md:**

```bash
# Extract REQ-* IDs from ## Requirements section
# Works for any version level's PLAN.md (major, minor, or patch)
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

**Option: Use Exploration Subagent**

For cleaner UX, spawn an exploration subagent to handle preparation, exploration, and verification
phases internally. This hides noisy tool calls (Bash, Read, Grep) from the user and returns
structured JSON. See `spawn-subagent` skill → "Expanded Exploration Subagent" section.

```bash
# Spawn exploration subagent for preparation + exploration
/cat:spawn-subagent --type exploration --task "$TASK_PATH"
# Returns JSON with: status, preparation.estimatedTokens, findings, verification
```

**Alternative: Inline Analysis (current default)**

Read the task's PLAN.md and estimate context requirements.

Output format (do NOT wrap in ```):

## Task Size Analysis

**Indicators of large task:**
- Multiple distinct features or components
- Many files to create/modify (> 5)
- Multiple test suites required
- Complex logic requiring exploration
- Estimated steps > 10

**Calculate threshold and hard limit (fixed values):**

```bash
# Values from agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=...
SOFT_TARGET_PCT=...
HARD_LIMIT_PCT=...
SOFT_TARGET=$((CONTEXT_LIMIT * SOFT_TARGET_PCT / 100))
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))

echo "Soft target: ${SOFT_TARGET} tokens (${SOFT_TARGET_PCT}% of ${CONTEXT_LIMIT})"
echo "Hard limit: ${HARD_LIMIT} tokens (${HARD_LIMIT_PCT}% of ${CONTEXT_LIMIT})"
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

**Hard Limit Enforcement (A018):**

```bash
if [ "${ESTIMATED_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "🛑 TASK EXCEEDS HARD LIMIT - MANDATORY DECOMPOSITION"
  echo "Estimated: ${ESTIMATED_TOKENS} tokens"
  echo "Hard limit: ${HARD_LIMIT} tokens (80% of context)"
  echo "Decomposition is REQUIRED. Do NOT spawn subagent."
  # MANDATORY: invoke /cat:decompose-task
fi
```

**If estimated size >= hard limit (80%):**

```
🛑 TASK EXCEEDS HARD LIMIT - MANDATORY DECOMPOSITION

Estimated tokens: ~{estimate}
Hard limit: {HARD_LIMIT} (80% of {CONTEXT_LIMIT})

MANDATORY: Task MUST be decomposed before execution.
Invoking /cat:decompose-task...
```

Invoke `/cat:decompose-task` automatically. Do NOT proceed with single subagent.

**If estimated size > soft threshold but < hard limit:**

```
⚠️ TASK SIZE EXCEEDS SOFT THRESHOLD

Estimated tokens: ~{estimate}
Soft threshold: {THRESHOLD} ({TARGET_USAGE}% of {CONTEXT_LIMIT})
Hard limit: {HARD_LIMIT} (80% of {CONTEXT_LIMIT})

RECOMMENDATION: Consider decomposing for optimal quality.
Proceeding is allowed but may result in quality degradation.
```

Use AskUserQuestion:
- header: "Task Size"
- question: "Task exceeds soft threshold ({TARGET_USAGE}% of context). How would you like to proceed?"
- options:
  - "Decompose into subtasks (Recommended)" - Invoke /cat:decompose-task to split into smaller tasks
  - "Proceed anyway" - Continue with single subagent execution (may degrade quality)
  - "Abort" - Stop and review task scope

**If "Decompose into subtasks":**
Invoke `/cat:decompose-task` automatically, then proceed with parallel execution.

**If "Proceed anyway":**

**If estimated size <= soft threshold:**

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

**Present approach choice if conditions warrant:**

This step implements the "Fork in the Road" experience. It presents choices when:
- Trust is NOT high (low or medium)
- PLAN.md has 2+ genuinely different approaches
- User's config doesn't clearly predict one path (< 85% confidence)

**Step 1: Check trust level**

```bash
TRUST_LEVEL=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
if [[ "$TRUST_LEVEL" == "high" ]]; then
  echo "✓ Trust: high - auto-selecting best approach based on config"
  # Skip to auto-selection
fi
```

**Step 2: Extract approaches from PLAN.md**

Look for `## Approaches` section with subsections like:
```markdown
## Approaches

### A: [Name]
- Risk: LOW|MEDIUM|HIGH
- Scope: N files
- Description...

### B: [Name]
...
```

If only one approach exists or no Approaches section:
```
✓ Single approach identified - proceeding automatically
```
Skip to create_worktree.

**Step 3: Calculate confidence scores**

For each approach, calculate alignment with user's config:

| Config Setting | Approach Characteristic | Alignment |
|----------------|------------------------|-----------|
| trust: low | Risk: LOW | +1.0 |
| trust: low | Risk: MEDIUM | +0.3 |
| trust: low | Risk: HIGH | +0.0 |
| trust: medium | Risk: LOW | +0.5 |
| trust: medium | Risk: MEDIUM | +1.0 |
| trust: medium | Risk: HIGH | +0.5 |
| curiosity: low | Scope: minimal/focused | +1.0 |
| curiosity: medium | Scope: moderate/balanced | +1.0 |
| curiosity: high | Scope: comprehensive/broad | +1.0 |

**Confidence calculation:**
```
score = (trust_alignment + curiosity_alignment) / 2
confidence = max_score / sum_of_all_scores * 100
```

**Step 4: Decision logic**

```
if max_confidence >= 85%:
  auto_select(highest_scoring_approach)
  display: "✓ Approach: [name] (auto-selected: {confidence}% config alignment)"
else:
  present_wizard()
```

**Step 5: Present wizard (if needed)**

Use the **FORK_IN_THE_ROAD** box from PRE-COMPUTED WORK BOXES.
Replace placeholders with actual approach data.

Use AskUserQuestion to capture selection:
- header: "Approach"
- question: "Which approach would you like to use?"
- options: [List of approaches with alignment percentages]

**Step 6: Record selection**

Add to task's STATE.md:
```yaml
- **Approach Selected:** [approach name]
- **Selection Reason:** [user choice | auto-selected: {confidence}% alignment]
```

Pass selected approach to subagent prompt to guide implementation.

</step>

<step name="create_worktree">

**Create task worktree and branch:**

Branch naming: `{major}.{minor}-{task-name}`

```bash
# Detect base branch (currently checked out branch in main worktree)
BASE_BRANCH=$(git branch --show-current)
echo "Base branch for task: $BASE_BRANCH"

# Create task branch from current branch (not hardcoded main)
TASK_BRANCH="{major}.{minor}-{task-name}"
git branch "$TASK_BRANCH" "$BASE_BRANCH" 2>/dev/null || true

# Create worktree (use absolute path to avoid cwd dependency)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/$TASK_BRANCH"
git worktree add "$WORKTREE_PATH" "$TASK_BRANCH" 2>/dev/null || \
    echo "Worktree already exists at $WORKTREE_PATH"

# Store base branch in worktree metadata (auto-deleted when worktree removed)
echo "$BASE_BRANCH" > "$(git rev-parse --git-common-dir)/worktrees/$TASK_BRANCH/cat-base"

# MANDATORY: Change to worktree directory
cd "$WORKTREE_PATH"
pwd  # Verify we're in the worktree
```

**Base Branch Configuration:**

The base branch is stored in the worktree's metadata directory at `.git/worktrees/<task>/cat-base`.
This file is automatically deleted when the worktree is removed, preventing orphaned config entries.
Tasks can be forked from any branch (main, v1.10, feature branches, etc.) and merge back to
the same branch when complete.

**CRITICAL: Main agent MUST work from worktree directory**

After creating the worktree, `cd` into it and stay there for the remainder of task execution. This ensures:
- All file edits happen in the correct isolated context
- Git commands operate on the task branch
- STATE.md updates go to the worktree copy (not main workspace)
- No confusion between main workspace and worktree file paths

**Read Git Workflow Preferences (if configured):**

```bash
# Check if Git Workflow section exists in PROJECT.md
WORKFLOW_SECTION=$(grep -A50 "^## Git Workflow" .claude/cat/PROJECT.md 2>/dev/null | head -50)

if [[ -n "$WORKFLOW_SECTION" ]]; then
  # Extract branching strategy
  BRANCH_STRATEGY=$(echo "$WORKFLOW_SECTION" | grep -oP "(?<=branchingStrategy.*:\s*\")[^\"]+|(?<=Strategy:\s)\w+" | head -1 || echo "feature")

  # Extract merge method preference
  MERGE_METHOD=$(echo "$WORKFLOW_SECTION" | grep "MUST use" | grep -oP "(fast-forward|merge commit|squash)" | head -1 || echo "fast-forward")

  echo "Git workflow configured:"
  echo "  Branching: $BRANCH_STRATEGY"
  echo "  Merge: $MERGE_METHOD"

  # For version-branch strategy, verify we're branching from correct version branch
  if [[ "$BRANCH_STRATEGY" == "version" ]]; then
    EXPECTED_BASE="v${MAJOR}.${MINOR}"
    if [[ "$BASE_BRANCH" != "$EXPECTED_BASE" && "$BASE_BRANCH" != "main" && ! "$BASE_BRANCH" =~ ^v[0-9]+\.[0-9]+$ ]]; then
      echo "⚠️ WARNING: Git workflow configured for version branches"
      echo "   Expected base: $EXPECTED_BASE or a version branch"
      echo "   Actual base: $BASE_BRANCH"
      echo "   Task will still proceed, but verify this is intentional."
    fi
  fi
else
  echo "No Git Workflow section in PROJECT.md - using defaults"
fi
```

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
   - PLAN.md contents
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

<step name="aggregate_token_report">

**Aggregate token usage across all subagents (multi-subagent tasks):**

For tasks that spawned multiple subagents (parallel execution or decomposed tasks), aggregate
token metrics from all `.completion.json` files.

```bash
# Values from agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=...
HARD_LIMIT_PCT=...
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))

# Find all subagent completion files for this task
TASK_WORKTREES=$(find .worktrees -name ".completion.json" -path "*${TASK_ID}*" 2>/dev/null)

# Aggregate metrics
TOTAL_TOKENS=0
TOTAL_EXCEEDED=0

echo "## Aggregate Token Report"
echo ""
echo "| Subagent | Type | Tokens | % of Limit | Status |"
echo "|----------|------|--------|------------|--------|"

for completion_file in $TASK_WORKTREES; do
  SUBAGENT_NAME=$(dirname "$completion_file" | xargs basename)
  TOKENS=$(jq -r '.tokensUsed // 0' "$completion_file")
  # Get subagent type from completion.json or parse from directory name
  SUBAGENT_TYPE=$(jq -r '.subagentType // "implementation"' "$completion_file")
  PERCENT=$((TOKENS * 100 / CONTEXT_LIMIT))
  TOTAL_TOKENS=$((TOTAL_TOKENS + TOKENS))

  if [ "$TOKENS" -ge "$HARD_LIMIT" ]; then
    STATUS="EXCEEDED"
    TOTAL_EXCEEDED=$((TOTAL_EXCEEDED + 1))
  elif [ "$TOKENS" -ge "$((HARD_LIMIT * 90 / 100))" ]; then
    STATUS="WARNING"
  else
    STATUS="OK"
  fi

  echo "| $SUBAGENT_NAME | $SUBAGENT_TYPE | $TOKENS | ${PERCENT}% | $STATUS |"
done

echo ""
echo "**Total tokens:** $TOTAL_TOKENS"
echo "**Subagents exceeded hard limit:** $TOTAL_EXCEEDED"
```

**If any subagent exceeded hard limit:**

```bash
if [ "$TOTAL_EXCEEDED" -gt 0 ]; then
  echo ""
  echo "⚠️ CONTEXT LIMIT VIOLATIONS DETECTED"
  echo ""
  echo "$TOTAL_EXCEEDED subagent(s) exceeded the 80% hard limit."
  echo "Triggering learn-from-mistakes for each violation..."

  # For each exceeded subagent, invoke learn-from-mistakes
  for completion_file in $TASK_WORKTREES; do
    TOKENS=$(jq -r '.tokensUsed // 0' "$completion_file")
    if [ "$TOKENS" -ge "$HARD_LIMIT" ]; then
      SUBAGENT_NAME=$(dirname "$completion_file" | xargs basename)
      # Invoke /cat:learn-from-mistakes with A018 reference
      echo "Recording violation: $SUBAGENT_NAME used $TOKENS tokens (limit: $HARD_LIMIT)"
    fi
  done
fi
```

**Output format for aggregate report:**

```
## Aggregate Token Report

| Subagent | Type | Tokens | % of Limit | Status |
|----------|------|--------|------------|--------|
| task-sub-a1b2c3d4 | exploration | 65,000 | 32% | OK |
| task-sub-e5f6g7h8 | implementation | 170,000 | 85% | EXCEEDED |
| task-sub-i9j0k1l2 | planning | 45,000 | 22% | OK |

**Total tokens:** 280,000
**Subagents exceeded hard limit:** 1

⚠️ CONTEXT LIMIT VIOLATIONS DETECTED

1 subagent(s) exceeded the 80% hard limit.
Triggering learn-from-mistakes for each violation...
```

</step>

<step name="token_check">

**Evaluate token metrics for decomposition:**

**→ Load token-warning.md workflow if compaction events > 0 or tokens exceed threshold.**

See `concepts/token-warning.md` for:
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
# Read base branch from worktree metadata (set during worktree creation)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: Base branch file not found: $CAT_BASE_FILE"
  echo "This worktree was not created properly. Recreate with /cat:work."
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")
SOURCE_CHANGES=$(git diff --name-only ${BASE_BRANCH}..HEAD | grep -v "\.claude/cat/" | grep -v "CHANGELOG.md" | head -1)

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
2. Detect project type (see [build-verification.md](../concepts/build-verification.md) for detection logic)
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

**Review triggering (based on verify level, NOT trust):**

| Verify | Action |
|--------|--------|
| `none` | Skip all stakeholder reviews |
| `changed` | Run stakeholder reviews |
| `all` | Run stakeholder reviews |

```bash
VERIFY_LEVEL=$(jq -r '.verify // "changed"' .claude/cat/cat-config.json)
if [[ "$VERIFY_LEVEL" == "none" ]]; then
  echo "⚡ Stakeholder review: SKIPPED (verify: none)"
  # Skip to approval_gate
fi
```

**High-risk detection:** Read the task's PLAN.md Risk Assessment section. Task is high-risk if ANY of:
- Risk section mentions "breaking change", "data loss", "security", "production"
- Task modifies authentication, authorization, or payment code
- Task touches 5+ files
- Task is marked as HIGH risk

**MANDATORY: Run stakeholder review BEFORE user approval.**

After implementation completes and token metrics are collected, run parallel stakeholder reviews
to identify concerns from multiple perspectives before presenting to user.

**Stakeholders:**

| Stakeholder | Focus |
|-------------|-------|
| requirements | Requirement satisfaction |
| architect | System design, modules, APIs |
| security | Vulnerabilities, validation |
| design | Code quality, complexity |
| testing | Test coverage, edge cases |
| performance | Efficiency, resources |

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

### Pre-Approval Checklist (MANDATORY - A010)

**BLOCKING: Do NOT present approval until ALL items are verified.**

Before presenting the approval gate, complete this checklist in order:

| # | Check | How to Verify | Fix if Failed |
|---|-------|---------------|---------------|
| 1 | Commits squashed by type | `git log --oneline ${BASE_BRANCH}..HEAD` shows 1-2 commits | Use `/cat:git-squash` skill |
| 2 | STATE.md status = completed | `grep "Status:" STATE.md` shows `completed` | Edit STATE.md |
| 3 | STATE.md progress = 100% | `grep "Progress:" STATE.md` shows `100%` | Edit STATE.md |
| 4 | STATE.md in commit | `git diff --name-only ${BASE_BRANCH}..HEAD \| grep STATE.md` | Amend commit to include |
| 5 | Diff content ready | `git diff ${BASE_BRANCH}..HEAD` output captured | Run diff command |

**Anti-patterns this checklist prevents:**
- M151: Approval with unsquashed commits
- M153: Approval with STATE.md at 90% in-progress
- M157: Approval without squashing first
- M160/M161: Approval without showing diff

```bash
# Run all checks in one script
BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-base")
TASK_STATE=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${TASK_NAME}/STATE.md"

# Check 1: Commit count (should be 1-2 after squash)
COMMIT_COUNT=$(git rev-list --count ${BASE_BRANCH}..HEAD)
if [[ "$COMMIT_COUNT" -gt 3 ]]; then
  echo "FAIL: $COMMIT_COUNT commits - need to squash first"
  exit 1
fi

# Check 2 & 3: STATE.md status and progress
if ! grep -q "Status.*completed" "$TASK_STATE"; then
  echo "FAIL: STATE.md status not 'completed'"
  exit 1
fi
if ! grep -q "Progress.*100%" "$TASK_STATE"; then
  echo "FAIL: STATE.md progress not '100%'"
  exit 1
fi

# Check 4: STATE.md in commit
if ! git diff --name-only ${BASE_BRANCH}..HEAD | grep -q "STATE.md"; then
  echo "FAIL: STATE.md not in commit"
  exit 1
fi

echo "PASS: All pre-approval checks passed"
```

**MANDATORY: Verify commit exists before presenting approval (M072).**

Users cannot review uncommitted changes. Before presenting the approval gate:

```bash
# Verify there are commits on the task branch that aren't on base branch
# Read base branch from worktree metadata (set during worktree creation)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: Base branch file not found: $CAT_BASE_FILE"
  echo "This worktree was not created properly. Recreate with /cat:work."
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")
COMMIT_COUNT=$(git rev-list --count ${BASE_BRANCH}..HEAD 2>/dev/null || echo "0")

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
TASK_STATE=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/task/${TASK_NAME}/STATE.md"

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
TASK_STATE=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/task/${TASK_NAME}/STATE.md"
```

**Required STATE.md fields for completion (M092):**

```markdown
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** [any-dependencies]
- **Completed:** {YYYY-MM-DD HH:MM}
- **Tokens Used:** {N}
```

Resolution field is MANDATORY. Valid values: `implemented`, `duplicate`, `obsolete`.

**Why:** STATE.md reflects task completion. Separating it from the implementation:
- Creates disconnected commits that require extra squash/rebase
- Makes the implementation commit incomplete (doesn't reflect task state)
- Violates atomic commit principle (related changes in one commit)

**Correct pattern:** Subagent or main agent updates STATE.md → includes in implementation commit.

**Anti-pattern (M076):** Committing STATE.md separately as "docs: complete task {name}".

Present work summary with checkpoint display.

**CRITICAL: Output directly WITHOUT code blocks (M125).** Markdown `**bold**` renders correctly
when output as plain text, but shows as literal asterisks inside triple-backtick code blocks.

Use the **CHECKPOINT_TASK_COMPLETE** box from PRE-COMPUTED WORK BOXES.
Replace placeholders with actual values: `{task-name}`, `{N}`, `{percentage}`, `{task-branch}`.

**Anti-pattern (M089):** Presenting subagent branch (e.g., `task-sub-uuid`) instead of task branch.
Users review the task branch which contains merged subagent work, not the internal subagent branch.

**CRITICAL:** Token metrics MUST be included. If unavailable (e.g., `.completion.json` not found),
parse session file directly or report "Metrics unavailable - manual review recommended."

**MANDATORY: Show diff BEFORE approval question (M160/M201).**

Users cannot make informed approval decisions without seeing actual code changes. Before presenting
the AskUserQuestion approval prompt, use the `cat:render-diff` skill to display the diff:

```bash
# Show commit summary
git log ${BASE_BRANCH}..HEAD --oneline

# Render diff using the render-diff skill (4-column table format)
git diff ${BASE_BRANCH}..HEAD | "${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py"
```

See [render-diff SKILL.md](../skills/render-diff/SKILL.md) for format details and features.

**SELF-CHECK before showing approval gate (M201/M211):**
- [ ] Did I run render-diff.py (NOT plain `git diff`)?
- [ ] Did I present the VERBATIM output (not reformatted, summarized, or extracted)?
- [ ] Does the output I showed have box characters (╭╮╰╯│)?
- [ ] Is the output I showed in 4-column format (Old | Symbol | New | Content)?

If any answer is NO, re-run using the render-diff skill command above.

**CRITICAL (M211): Present render-diff output VERBATIM.**
Copy-paste the exact Bash tool output into your response. Do NOT:
- Extract portions into code blocks
- Reformat into standard diff format
- Summarize or paraphrase the changes
- Create your own diff representation

The user must see the ACTUAL render-diff output, not your interpretation of it.

**CRITICAL (M231): Handle large diffs by showing ALL content.**
When diff output is truncated or saved to a file due to size:
1. Read the ENTIRE saved file using the Read tool (use offset/limit if needed for multiple reads)
2. Present ALL file contents to the user, not just excerpts
3. If output exceeds response limits, split across multiple messages
4. NEVER summarize with "remaining diff shows..." - show the actual content
5. Users MUST see every changed line to make informed approval decisions

**Use CHECKPOINT_TASK_COMPLETE box from OUTPUT TEMPLATE WORK BOXES (M257):**
1. Locate the "--- CHECKPOINT_TASK_COMPLETE ---" section in context
2. Use that EXACT box structure - do NOT manually type box characters
3. Replace ONLY the placeholder text (e.g., {task-name} → actual name)
4. Verify: Box must have exactly 10 lines including header

Anti-pattern M089: Don't show subagent branch in approval.

Anti-pattern: Reading only first 200 lines of a 115KB diff and summarizing the rest as "standard
implementation". This defeats the purpose of showing the diff.

**Anti-pattern (M160):** Presenting approval gate with only file change summary (names, line counts)
without showing the actual diff content. Users reject approval because they cannot evaluate changes.

**Anti-pattern (M170/M171/M201):** Using plain `git diff` or ad-hoc formats instead of render-diff skill.
The render-diff skill provides 4-column table with box characters - plain diffs are NOT acceptable.

**Anti-pattern (M172):** Referencing PLAN.md instead of SKILL.md for skill usage.
- **PLAN.md** describes *what to build* (task planning document)
- **SKILL.md** describes *how to use it* (authoritative usage documentation)
- Always invoke skills via their scripts/commands, not by reading PLAN.md

Use AskUserQuestion with options:
- header: "Next Step"
- question: "What would you like to do?"
- options:
  - "✓ Approve and merge" - Merge to base branch, continue to next task
  - "✏️ Request changes" - Need modifications before proceeding
  - "✗ Abort" - Discard work entirely

**CRITICAL (M248): Distinguish task version from base branch in approval messages.**

- **Task version**: Extracted from task ID (e.g., `2.1-batch-finalization-subagent` → v2.1)
- **Base branch**: The git branch being merged into (e.g., `v2.0`)

When presenting approval, say "merge v{major}.{minor} task to {base-branch}" NOT "merge to v{base-branch}"
which conflates the branch name with the task's version.

**MANDATORY: Include subtask context for decomposed tasks (M251).**

When presenting approval for a subtask (task created by decomposition), provide context:

1. **Identify as subtask**: "This is subtask 1 of 6 from decomposed task `centralize-version-path-resolution`"
2. **List remaining subtasks**: Show pending subtasks so user understands full scope
3. **Note dependencies**: Indicate which subtasks are now unblocked

Example approval context for decomposed tasks:

```
This completes subtask 1 of 6 from decomposed task `parent-task-name`.

Remaining subtasks (Phase 2 - can run in parallel):
- update-add-command-paths (pending)
- update-remove-command-paths (pending)
- update-work-command-paths (pending)
- update-research-skills-paths (pending)
- update-python-handler-paths (pending)
```

**Why this matters:** Without subtask context, users may think the work is complete when it's only
one piece of a larger decomposition. This leads to confusion about scope and progress.

**If "Request changes":**

Capture user feedback and spawn implementation subagent to address concerns.

**MANDATORY: Main agent does NOT implement feedback directly (M063).**

The main agent is an orchestrator. All code changes - including feedback fixes - MUST be delegated
to a subagent with fresh context.

**Step 1: Capture User Feedback**

Use AskUserQuestion to collect specific feedback:
- header: "Feedback"
- question: "What changes would you like made?"
- freeform: true (allow detailed text input)

Wait for user to provide specific feedback about what needs to change.

**Step 2: Gather Context for Subagent**

```bash
# Get current diff for context
BASE_BRANCH=$(git config --get "branch.$(git rev-parse --abbrev-ref HEAD).cat-base" 2>/dev/null || echo "main")
git diff ${BASE_BRANCH}..HEAD > /tmp/current-implementation.diff

# Get task PLAN.md path
TASK_PLAN=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${TASK_NAME}/PLAN.md"

# Get worktree path (already created in create_worktree step)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/${TASK_BRANCH}"
```

**Step 3: Spawn Feedback Implementation Subagent**

Invoke `/cat:spawn-subagent` with feedback context:

```
Task tool invocation:
  description: "Address user feedback for ${TASK_NAME}"
  subagent_type: "general-purpose"
  prompt: |
    ADDRESS USER FEEDBACK

    WORKING DIRECTORY: ${WORKTREE_PATH}

    USER FEEDBACK:
    ${user_feedback_text}

    EXISTING IMPLEMENTATION:
    Review the current implementation diff below and apply the requested changes.

    CURRENT DIFF:
    ${contents of /tmp/current-implementation.diff}

    TASK PLAN REFERENCE:
    ${TASK_PLAN contents}

    VERIFICATION:
    1. Run build/tests as appropriate for project type
    2. All must pass

    FAIL-FAST CONDITIONS:
    - If build fails after changes, report BLOCKED with error
    - If feedback is unclear, report BLOCKED requesting clarification
    - Do NOT attempt workarounds - report and stop

    COMMIT:
    After verification passes, commit with message:
    "feature: address review feedback - {brief summary of changes}"
```

**Step 4: Collect Results**

Invoke `/cat:collect-results` to gather:
- Token usage from subagent
- Commits made
- Files changed
- Any discovered issues

Display subagent execution report to user (MANDATORY per token reporting requirements).

**Step 5: Merge Feedback Changes to Task Branch**

Invoke `/cat:merge-subagent` to:
- Merge subagent branch into task branch
- Clean up subagent worktree
- Update tracking state

**Step 6: RE-PRESENT Approval Gate**

**MANDATORY: Loop back to approval gate with updated changes.**

After merging feedback changes, the approval gate MUST be re-presented. This ensures:
- User sees updated diff reflecting their requested changes
- User explicitly approves final implementation
- No changes merge without explicit approval

Use the **CHECKPOINT_FEEDBACK_APPLIED** box from PRE-COMPUTED WORK BOXES.
Replace placeholders with actual values.

Then re-present approval options via AskUserQuestion:
- header: "Next Step"
- question: "Feedback has been applied. What would you like to do?"
- options:
  - "✓ Approve and merge" - Merge to main, continue to next task
  - "🔍 Review changes first" - I'll examine the updated diff
  - "✏️ Request more changes" - Need additional modifications
  - "✗ Abort" - Discard all work

**Loop Continuation:**

If user selects "Request more changes", repeat Steps 1-6 with fresh feedback.

**Maximum Iterations Safety:**

Track feedback iterations. If iterations exceed 5:

```
⚠️ FEEDBACK ITERATION LIMIT

5 feedback iterations completed without approval.

Options:
1. Continue with current implementation
2. Abort and start fresh
3. Override limit and continue iterations
```

Use AskUserQuestion to capture decision.

**If "Abort":**
Clean up worktree and branch, mark task as pending.

</step>

<step name="squash_commits">

**Apply Squash Preference from PROJECT.md:**

```bash
# Read squash policy from PROJECT.md
SQUASH_POLICY=$(grep -A10 "### Squash Policy" .claude/cat/PROJECT.md 2>/dev/null | grep "Strategy:" | sed 's/.*Strategy:\s*//' | head -1)

# Also check cat-config.json for programmatic access
if [[ -z "$SQUASH_POLICY" ]]; then
  SQUASH_POLICY=$(jq -r '.gitWorkflow.squashPolicy // "by-type"' .claude/cat/cat-config.json 2>/dev/null)
fi

echo "Squash policy: $SQUASH_POLICY"

case "$SQUASH_POLICY" in
  "by-type"|"by type"|"Squash by type"|"Group commits by type prefix")
    echo "Applying squash-by-type policy..."
    # Continue with default category-based squashing below
    ;;
  "single"|"Single commit"|"Squash all commits into one")
    echo "Applying single-commit policy..."
    # Squash all into one commit instead of by category
    SQUASH_ALL=true
    ;;
  "keep-all"|"keep all"|"Keep all commits")
    echo "Skipping squash per PROJECT.md policy"
    # Skip this step entirely
    SKIP_SQUASH=true
    ;;
  *)
    echo "Using default squash-by-type policy"
    ;;
esac
```

**If SKIP_SQUASH is true:** Skip to next step (merge).

**If SQUASH_ALL is true:**
```bash
# Squash all commits into single commit
COMMIT_COUNT=$(git rev-list --count "${BASE_BRANCH}..HEAD")
if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  FIRST_MSG=$(git log --format="%s" "${BASE_BRANCH}..HEAD" | tail -1)
  git reset --soft "$BASE_BRANCH"
  git commit -m "$FIRST_MSG"
fi
```

**Otherwise (default by-type squashing):**

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

<step name="finalization">
**Spawn Finalization subagent to complete post-approval work.**

This step batches merge, cleanup, state updates, and changelog updates into a single subagent,
hiding tool calls from the user. See concepts/work.md § Subagent Batching Standards.

**Spawn Finalization subagent:**

```
Task tool invocation:
  description: "Finalize task {task-name}"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Complete post-approval finalization for task.

    CONTEXT:
    - Task: {task-name}
    - Task branch: {task-branch}
    - Worktree path: {worktree-path}
    - Base branch: {base-branch}
    - Completion workflow: {completionWorkflow from config}
    - Merge style: {mergeStyle from config}
    - Task ID: {TASK_ID}
    - Session ID: {SESSION_ID}

    STEP 1: MERGE
    Return to main workspace and merge task branch.

    cd /workspace
    CURRENT_BRANCH=$(git branch --show-current)

    If completionWorkflow is "merge":
      If mergeStyle is "fast-forward":
        git merge --ff-only {task-branch}
      If mergeStyle is "merge-commit":
        git merge --no-ff {task-branch}
      If mergeStyle is "squash":
        git merge --squash {task-branch} && git commit -m "{commit-message}"

      If merge fails: Return {"status": "MERGE_CONFLICT", "error": "..."}

    If completionWorkflow is "pr":
      git push -u origin {task-branch}
      If GitHub: gh pr create --base {base} --head {task-branch} --title "..." --body "..."
      Return {"status": "PR_CREATED", "url": "..."}

    STEP 2: CLEANUP
    Remove worktree: git worktree remove "{worktree-path}" --force

    If completionWorkflow is "merge":
      Delete branch: git branch -d "{task-branch}"

    Release lock:
      "${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" release "${CLAUDE_PROJECT_DIR}" "{TASK_ID}" "{SESSION_ID}"

    STEP 3: UPDATE PARENT STATE
    Task STATE.md already committed with implementation.
    Update parent STATE.md files (minor/major progress rollup).

    git add .claude/cat/issues/v*/STATE.md .claude/cat/issues/v*/v*.*/STATE.md
    git commit -m "config: update progress for v{major}.{minor}"

    STEP 4: UPDATE CHANGELOGS
    Update minor CHANGELOG.md: Add task to Tasks Completed table.
    Update major CHANGELOG.md if minor version completes.

    RETURN FORMAT:
    {
      "status": "SUCCESS" | "MERGE_CONFLICT" | "PR_CREATED" | "FAILED",
      "merged": true/false,
      "branch": "{base-branch}",
      "pr_url": "..." (if PR workflow),
      "error": "..." (if failed)
    }

    FAIL-FAST:
    - If merge fails with conflict, return MERGE_CONFLICT immediately
    - If any git operation fails unexpectedly, return FAILED with error
    - Always release lock before returning, even on failure
```

**Handle subagent result:**

**If result.status is "SUCCESS":**
Proceed to next_task step.

**If result.status is "PR_CREATED":**
Display PR URL: `PR created: {result.pr_url}`
Proceed to next_task step.

**If result.status is "MERGE_CONFLICT":**
Use AskUserQuestion: "Force merge commit" | "Rebase first" | "Abort"
- If "Force merge commit": Re-run finalization with merge-commit style
- If "Rebase first": Instruct user to rebase manually, then retry
- If "Abort": Release lock, exit workflow

**If result.status is "FAILED":**
Display error: `Finalization failed: {result.error}`
Use AskUserQuestion: "Retry" | "Abort"
- If "Retry": Re-spawn finalization subagent
- If "Abort": Release lock (if not already released), exit workflow
</step>

<step name="next_task">

**MANDATORY: Provide next steps to user (M120).**

After cleanup, ALWAYS show user their available options. Never end with just "Task Complete" -
user needs guidance on what to do next.

**Version Boundary Detection:**

Track completed task version (supports MAJOR, MAJOR.MINOR, or MAJOR.MINOR.PATCH schemes):
```bash
COMPLETED_MAJOR="{major from completed task}"
COMPLETED_MINOR="{minor from completed task, empty if major-only scheme}"
COMPLETED_PATCH="{patch from completed task, empty if no patch level}"
```

After find-task.sh returns next task, extract its version:
```bash
NEXT_MAJOR=$(echo "$NEXT_TASK_RESULT" | jq -r '.major')
NEXT_MINOR=$(echo "$NEXT_TASK_RESULT" | jq -r '.minor // empty')
NEXT_PATCH=$(echo "$NEXT_TASK_RESULT" | jq -r '.patch // empty')
```

**Boundary detection adapts to versioning scheme:**

| Scheme | Boundary Crossed When |
|--------|----------------------|
| MAJOR only | `COMPLETED_MAJOR != NEXT_MAJOR` |
| MAJOR.MINOR | `COMPLETED_MAJOR != NEXT_MAJOR OR COMPLETED_MINOR != NEXT_MINOR` |
| MAJOR.MINOR.PATCH | `COMPLETED_MAJOR != NEXT_MAJOR OR COMPLETED_MINOR != NEXT_MINOR OR COMPLETED_PATCH != NEXT_PATCH` |

**If BOUNDARY_CROSSED is true:**
1. Use **VERSION_BOUNDARY_GATE** box from OUTPUT TEMPLATE WORK BOXES
2. Use AskUserQuestion:
   - header: "Version Boundary"
   - question: "All tasks in v{completed-version} complete. Continue to v{next-version}?"
   - options:
     - "Continue to next version" - Proceed with auto-continue
     - "Exit to publish first" - Exit work loop for user to publish
     - "Stop" - Exit work loop immediately

**If user selects "Continue to next version":**
Proceed with existing auto-continue logic.

**If user selects "Exit to publish first" or "Stop":**
Release lock, exit workflow gracefully.

**If BOUNDARY_CROSSED is false (same version):**
Proceed with existing auto-continue logic unchanged.

**Auto-continue behavior conditionals:**

**MANDATORY: Try to acquire lock before offering next task.**

For each candidate task:
1. Check status is `pending` or `in-progress`
2. Check all dependencies are `completed`
3. **Try to acquire lock** - skip if locked by another session

```bash
NEXT_TASK_ID="${MAJOR}.${MINOR}-${NEXT_TASK_NAME}"

# Try to acquire lock for next task
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" acquire "${CLAUDE_PROJECT_DIR}" "$NEXT_TASK_ID" "${CLAUDE_SESSION_ID}")

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

Use the **TASK_COMPLETE_WITH_NEXT_TASK** box from PRE-COMPUTED WORK BOXES.
Replace placeholders with actual values:
- `{task-name}` → completed task name
- `{next-task-name}` → next task name
- `{goal from PLAN.md}` → goal line from next task's PLAN.md

**Brief pause for user intervention:**

After displaying the message, pause briefly (3 seconds conceptually) to allow user to type:
- **"stop"** or **"pause"** → Complete current display, do NOT start next task
- **"abort"** or **"cancel"** → Stop immediately, release locks, clean up

If no input received, **loop back to find_task step** with the next task, continuing execution.

**Note:** In practice, this is a conceptual pause. The agent displays the message and checks if the user
has sent a follow-up message before proceeding. If user says "stop", the agent acknowledges and ends
the work session gracefully.

**If scope complete (no more tasks within scope):**

Use the **SCOPE_COMPLETE** box from PRE-COMPUTED WORK BOXES.
Replace placeholders based on scope type.

**If trust == low and next task found:**

Release the lock (user will re-acquire when they invoke the command):

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" release "${CLAUDE_PROJECT_DIR}" "$NEXT_TASK_ID" "${CLAUDE_SESSION_ID}"
```

Use the **TASK_COMPLETE_LOW_TRUST** box from PRE-COMPUTED WORK BOXES.
Replace placeholders with actual values.

If no more tasks in the current minor version (all completed, blocked, or locked):

**→ Load version-completion.md workflow for full handling.**

See `concepts/version-completion.md` for:
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
Use `/cat:add` to add more tasks or versions.

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

**Correct workflow pattern:**

```
1. Main agent spawns EXPLORATION subagent: "Find all X and report findings"
2. Exploration subagent returns: "Found X at locations A, B, C with patterns..."

PLANNING:
3. Main agent spawns PLANNING subagent: "Produce detailed implementation plan"
4. Planning subagent returns comprehensive PLAN.md with:
   - Risk assessment
   - Files to modify
   - Execution steps
   - Acceptance criteria

IMPLEMENTATION:
5. Main agent spawns IMPLEMENTATION subagent: "Execute this spec mechanically"
6. Implementation subagent returns: "Completed. Commits: ..."
7. Main agent presents approval gate to user
```

**Config settings drive behavior directly:**
- Trust: Controls approval gates and auto-selection thresholds
- Verify: Controls pre-commit verification scope
- Curiosity: Controls issue discovery breadth
- Patience: Controls when discovered issues are addressed

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

See `concepts/duplicate-task.md` for full handling including:
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
- [ ] **Pre-spawn validation: estimate < hard limit (A018)**
- [ ] **If oversized: auto-decomposition triggered**
- [ ] **If decomposed: parallel execution plan generated**
- [ ] Worktree(s) created with correct branch(es)
- [ ] PLAN.md executed successfully via subagent(s)
- [ ] **Token metrics collected and reported to user**
- [ ] **Aggregate token report generated (multi-subagent tasks)**
- [ ] **Context limit violations flagged and learn-from-mistakes triggered**
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
