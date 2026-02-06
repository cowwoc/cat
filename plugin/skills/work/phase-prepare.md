---
user-invocable: false
---

# Work Phase: Prepare

Steps for task preparation: verify, find_task, acquire_lock, load_task, validate_requirements, analyze_task_size, choose_approach, create_worktree.

---

## Progress Banner Requirement (M319)

**MANDATORY: Display progress banner at phase start and transitions.**

After acquiring the lock and identifying the task, run `get-progress-banner.sh` and OUTPUT the result directly to the user (not in a code block):

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/get-progress-banner.sh" "$ISSUE_ID" --phase preparing
```

**Anti-pattern (M319):** Writing informal markdown like "## Phase 1: Prepare" instead of running the script.

**Anti-pattern (M320):** Do NOT describe or show example banner output in documentation - this primes manual construction instead of script execution.

---

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

**Optional: Use get-available-issues.sh script**

For programmatic task discovery, use the `get-available-issues.sh` script:

```bash
# Find next available task
RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/get-available-issues.sh" "${CLAUDE_PROJECT_DIR}" --session-id "${CLAUDE_SESSION_ID}")

# Parse result
if echo "$RESULT" | jq -e '.status == "found"' > /dev/null 2>&1; then
  ISSUE_ID=$(echo "$RESULT" | jq -r '.issue_id')
  ISSUE_PATH=$(echo "$RESULT" | jq -r '.issue_path')
  MAJOR=$(echo "$RESULT" | jq -r '.major')
  MINOR=$(echo "$RESULT" | jq -r '.minor')
  ISSUE_NAME=$(echo "$RESULT" | jq -r '.issue_name')
  # MANDATORY: Show progress banner IMMEDIATELY after lock acquired (M314, M315)
  # User needs visual feedback before exploration begins
  # Use get-progress-banner.sh to show all phases with dot symbols
  "${CLAUDE_PLUGIN_ROOT}/scripts/get-progress-banner.sh" "$ISSUE_ID" --phase preparing
  # The banner shows: ○ Pending | ● Complete | ◉ Active for each phase
else
  echo "No executable tasks found"
  echo "$RESULT" | jq -r '.message // .status'
fi
```

The script handles argument parsing, version filtering, dependency checks, lock acquisition, and gate evaluation.

**MANDATORY: Fail-fast on script ERROR output (M262)**

**If script output contains "ERROR:" prefix:**
1. STOP immediately - do NOT process the JSON result
2. Report the error to the user
3. The error indicates data integrity issues (invalid status values, corrupted STATE.md)
4. User must fix the underlying issue before proceeding

**Session ID**: The session ID is automatically available as `${CLAUDE_SESSION_ID}` in this command.

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

**MANDATORY: Lock Check Before Offering Task (M097)**

For each candidate task, attempt to acquire the lock BEFORE offering it as available:

```bash
ISSUE_ID="${MAJOR}.${MINOR}-${ISSUE_NAME}"
LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" acquire "${CLAUDE_PROJECT_DIR}" "$ISSUE_ID" "${CLAUDE_SESSION_ID}")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  OWNER=$(echo "$LOCK_RESULT" | jq -r '.owner // "unknown"')
  echo "⏸️ Task $ISSUE_ID is locked by session: $OWNER"
  continue  # Skip this task and try the next candidate
fi
echo "✓ Lock acquired for task: $ISSUE_ID"
```

**NEVER:**
- Investigate lock validity (commit counts, worktree state, timestamps are IRRELEVANT)
- Label locks as "stale" based on any evidence
- Offer to remove locks proactively
- Question whether the lock owner is still active

**Entry Gate Evaluation:**

For each candidate task, read the version's PLAN.md and extract the `## Gates` → `### Entry` section.

| Condition Type | How to Evaluate |
|----------------|-----------------|
| `Previous minor version (X.Y) complete` | All tasks in vX.Y must have status: completed |
| `Previous major version (N) complete` | All minor versions in vN must be complete |
| `Issue X.Y-issue-name complete` | That specific issue must have status: completed |
| `Version X.Y complete` | All tasks in that version must be complete |
| `Manual approval required` | Check STATE.md for `Entry Approved: true` |
| `No prerequisites` | Always satisfied |

**If no `## Gates` section exists**, fall back to default rules:
- First minor of first major (v0.0): No prerequisites
- Subsequent minor versions (e.g., v0.5): Previous minor version must be complete
- First minor of new major (e.g., v1.0): Last minor of previous major must be complete

**Exit Gate Task Dependency Check:**

Exit gate tasks are identified by the `[task]` prefix in the `### Exit` section of PLAN.md.
For a task marked with `[task]` in the Exit section:
1. Get all tasks in the same minor version
2. Exclude tasks that are also marked as exit gate tasks
3. If ANY non-exit-gate task has status other than `completed`, this task is blocked

**If no executable task found:**

**MANDATORY: Copy-paste the exact box from system-reminder (M246)**

Search the conversation context for "--- NO_EXECUTABLE_TASKS ---" in the Script Output Work Boxes
section. Copy the ENTIRE box structure verbatim.

**MANDATORY: Accept get-available-issues.sh results (M245)**

When get-available-issues.sh returns "no executable tasks", this is the CORRECT answer. Do NOT:
- Manually search for pending tasks to work around this result
- Try to acquire locks on tasks that the script already determined are unavailable
- Second-guess the script's lock checking logic

</step>

<step name="acquire_lock">

**Verify task lock (already acquired in find_task step):**

The lock was already acquired during the find_task step (M097). This step verifies the lock is held.

```bash
ISSUE_ID="${MAJOR}.${MINOR}-${ISSUE_NAME}"
echo "✓ Lock held for task: $ISSUE_ID (acquired in find_task step)"
```

</step>

<step name="load_task">

**Load task details:**

Read the task's:
- `STATE.md` - current status, progress, dependencies
- `PLAN.md` - execution plan with steps
- Parent minor's `STATE.md` - for context
- Parent major's `STATE.md` - for context

Present task overview with visual progress bar.

**CRITICAL: Output directly WITHOUT code blocks (M125).**

Output format (do NOT wrap in ```):

## Issue: {issue-name}

**Version:** {major}.{minor}
**Status:** {status}
**Progress:** [==========>         ] {progress}%

**Goal:**
{goal from PLAN.md}

</step>

<step name="validate_requirements_coverage">

**MANDATORY: Validate all requirements are covered before execution.**

This step ensures 100% requirements traceability before implementation begins.

**Extract requirements from PLAN.md:**

```bash
REQUIREMENTS=$(grep -oE 'REQ-[0-9]+' "$ISSUE_PATH/PLAN.md" | sort -u)
REQ_COUNT=$(echo "$REQUIREMENTS" | wc -l)
echo "Found $REQ_COUNT requirements"
```

**Extract covered requirements from ## Requirements Traceability:**

```bash
COVERED=$(grep -A100 '### Requirements Traceability' "$ISSUE_PATH/PLAN.md" | \
          grep -oE 'REQ-[0-9]+' | sort -u)
COVERED_COUNT=$(echo "$COVERED" | wc -l)
```

**If uncovered requirements exist:**

Use AskUserQuestion:
- header: "Coverage Gate"
- question: "Requirements coverage validation failed. How to proceed?"
- options:
  - "Update plan" - I'll add the missing traceability
  - "Override" - Proceed without full coverage (not recommended)
  - "Abort" - Stop execution

**Skip conditions:**
- Task has no `## Requirements` section (legacy tasks)
- User provided `--override-coverage` flag
- Task type is `bugfix` or `refactor` (requirements optional)

</step>

<step name="analyze_task_size">

**MANDATORY: Analyze task complexity BEFORE execution.**

Read the task's PLAN.md and estimate context requirements.

**Calculate threshold and hard limit (fixed values):**

```bash
# Values from agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=...
SOFT_TARGET_PCT=...
HARD_LIMIT_PCT=...
SOFT_TARGET=$((CONTEXT_LIMIT * SOFT_TARGET_PCT / 100))
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))
```

**Estimate task size:**

| Factor | Weight | Estimation |
|--------|--------|------------|
| Files to create | 5K tokens each | Count × 5000 |
| Files to modify | 3K tokens each | Count × 3000 |
| Test files | 4K tokens each | Count × 4000 |
| Steps in PLAN.md | 2K tokens each | Count × 2000 |
| Exploration needed | 10K tokens | +10000 if uncertain |

**MANDATORY: Store the estimate for later comparison:**

```bash
ESTIMATED_TOKENS={calculated_estimate}
echo "Estimated tokens: ${ESTIMATED_TOKENS}"
```

**Hard Limit Enforcement (A018):**

```bash
if [ "${ESTIMATED_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "🛑 TASK EXCEEDS HARD LIMIT - MANDATORY DECOMPOSITION"
  # MANDATORY: invoke /cat:decompose-task
fi
```

**If estimated size >= hard limit (80%):**
Invoke `/cat:decompose-task` automatically. Do NOT proceed with single subagent.

**If estimated size > soft threshold but < hard limit:**
Use AskUserQuestion:
- header: "Task Size"
- question: "Task exceeds soft threshold. How would you like to proceed?"
- options:
  - "Decompose into subtasks (Recommended)"
  - "Proceed anyway"
  - "Abort"

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
fi
```

**Step 2: Extract approaches from PLAN.md**

Look for `## Approaches` section with subsections like `### A: [Name]`, `### B: [Name]`.

If only one approach exists or no Approaches section: Skip to create_worktree.

**Step 3: Calculate confidence scores**

For each approach, calculate alignment with user's config based on trust and curiosity settings.

**Step 4: Decision logic**

```
if max_confidence >= 85%:
  auto_select(highest_scoring_approach)
else:
  present_wizard()
```

**Step 5: Present wizard (if needed)**

Use the **FORK_IN_THE_ROAD** box from Script Output Work Boxes.

Use AskUserQuestion:
- header: "Approach"
- question: "Which approach would you like to use?"
- options: [List of approaches with alignment percentages]

**Step 6: Record selection**

Add to task's STATE.md:
```yaml
- **Approach Selected:** [approach name]
- **Selection Reason:** [user choice | auto-selected: {confidence}% alignment]
```

</step>

<step name="create_worktree">

**Create task worktree and branch:**

Branch naming: `{major}.{minor}-{issue-name}`

```bash
# Detect base branch (currently checked out branch in main worktree)
BASE_BRANCH=$(git branch --show-current)
echo "Base branch for task: $BASE_BRANCH"

# Create task branch from current branch (not hardcoded main)
ISSUE_BRANCH="{major}.{minor}-{issue-name}"
git branch "$ISSUE_BRANCH" "$BASE_BRANCH" 2>/dev/null || true

# Create worktree (use absolute path to avoid cwd dependency)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.claude/cat/worktrees/$ISSUE_BRANCH"
git worktree add "$WORKTREE_PATH" "$ISSUE_BRANCH" 2>/dev/null || \
    echo "Worktree already exists at $WORKTREE_PATH"

# Store base branch in worktree metadata (auto-deleted when worktree removed)
echo "$BASE_BRANCH" > "$(git rev-parse --git-common-dir)/worktrees/$ISSUE_BRANCH/cat-base"

# MANDATORY: Change to worktree directory
cd "$WORKTREE_PATH"
pwd  # Verify we're in the worktree
```

**Base Branch Configuration:**

The base branch is stored in the worktree's metadata directory at `.git/worktrees/<task>/cat-base`.
This file is automatically deleted when the worktree is removed.

**CRITICAL: Main agent MUST work from worktree directory**

After creating the worktree, `cd` into it and stay there for the remainder of task execution.

**Update task STATE.md (AFTER cd into worktree - M326):**

Set status to `in-progress` and record start time. This update MUST happen after `cd` into the
worktree, not in the main workspace. Updating STATE.md before entering the worktree causes merge
conflicts later.

</step>
