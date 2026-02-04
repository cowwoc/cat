---
description: Preparation phase for /cat:work - finds task, acquires lock, creates worktree
user-invocable: false
---

# Work Phase: Prepare

Subagent skill for the preparation phase of `/cat:work`. Handles task discovery, lock acquisition,
validation, and worktree creation.

## Input

The main agent provides:

```json
{
  "session_id": "uuid",
  "project_dir": "/workspace",
  "arguments": "optional task filter",
  "trust_level": "low|medium|high"
}
```

## Output Contract

Return JSON on success:

```json
{
  "status": "READY|NO_TASKS|LOCKED|BLOCKED|ERROR",
  "issue_id": "2.1-issue-name",
  "major": "2",
  "minor": "1",
  "issue_name": "issue-name",
  "issue_path": "/workspace/.claude/cat/issues/v2/v2.1/issue-name",
  "worktree_path": "/workspace/.worktrees/2.1-issue-name",
  "branch": "2.1-issue-name",
  "base_branch": "v2.1",
  "estimated_tokens": 45000,
  "percent_of_threshold": 56,
  "goal": "Brief goal from PLAN.md",
  "approach_selected": "A|B|auto",
  "lock_acquired": true
}
```

Return JSON on failure:

```json
{
  "status": "NO_TASKS|LOCKED|BLOCKED|ERROR",
  "message": "Human-readable explanation",
  "suggestion": "Next action to take"
}
```

## Critical Constraints

### Use Existing Scripts (M364)

**MANDATORY: Use `get-available-issues.sh` for task discovery. NEVER reimplement its logic.**

The discovery script handles:
- Version traversal and task ordering
- Dependency checking
- Lock acquisition
- Decomposed parent detection

If the script doesn't support a needed feature (like filtering by name):
1. Call the script to get the available task
2. Check if result matches filter criteria in memory
3. If not, return appropriate status (e.g., NO_TASKS with message about filter)

**NEVER** write custom Python/bash to traverse issues directories and check STATE.md files.
That logic already exists in the script and is tested.

### No Temporary State Mutations (M365)

**MANDATORY: NEVER modify STATE.md files temporarily to influence discovery.**

Temporary mutations that rely on cleanup code are unsafe because:
- User interruptions (Ctrl+C, exit code 137) are normal operations
- Any approach that corrupts data on interruption is unacceptable
- Cleanup code may never execute

**FORBIDDEN patterns:**
- Marking tasks "completed" temporarily to hide them from discovery
- Creating backup files that must be restored
- Any mutation that requires rollback

**ALLOWED patterns:**
- Read STATE.md, filter results in memory
- Call discovery script multiple times if needed
- Return filtered status in output JSON

## Process

### Step 1: Verify Planning Structure

```bash
[ ! -d .claude/cat ] && echo '{"status":"ERROR","message":"No .claude/cat/ directory"}' && exit 1
[ ! -f .claude/cat/cat-config.json ] && echo '{"status":"ERROR","message":"No cat-config.json"}' && exit 1
```

### Step 2: Find Available Task

Use the discovery script:

```bash
RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/get-available-issues.sh" --session-id "${SESSION_ID}")
```

Parse the result and handle statuses:
- `found` - Check filter (if any), then continue to worktree creation
- `no_tasks` - Return NO_TASKS status
- `locked` - Return LOCKED status with owner info

**Filtering (M364):** If arguments include a filter (e.g., "skip compression tasks"):
1. Parse the discovered task name from result
2. Check if task matches filter criteria (in memory, NOT by modifying files)
3. If task should be skipped: Return `{"status": "NO_TASKS", "message": "Available task filtered out", "filtered_task": "issue-name", "filter": "skip compression tasks"}`

The orchestrator can then decide to retry without filter or inform user.

### Step 3: Analyze Task Size

Read PLAN.md and estimate context requirements:

| Factor | Weight |
|--------|--------|
| Files to create | 5K tokens each |
| Files to modify | 3K tokens each |
| Test files | 4K tokens each |
| Steps in PLAN.md | 2K tokens each |
| Exploration needed | +10K if uncertain |

Compare against hard limit (160K tokens = 80% of 200K).

If exceeds hard limit: Return status `OVERSIZED` with decomposition recommendation.

### Step 4: Create Worktree

```bash
ISSUE_BRANCH="${MAJOR}.${MINOR}-${ISSUE_NAME}"
BASE_BRANCH=$(git branch --show-current)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/${ISSUE_BRANCH}"

git worktree add -b "${ISSUE_BRANCH}" "${WORKTREE_PATH}" HEAD
echo "${BASE_BRANCH}" > "$(git rev-parse --git-common-dir)/worktrees/${ISSUE_BRANCH}/cat-base"
```

### Step 5: Verify Worktree Branch (M351)

**MANDATORY: Verify the worktree is on the correct branch before proceeding.**

```bash
cd "${WORKTREE_PATH}"
ACTUAL_BRANCH=$(git branch --show-current)

if [[ "$ACTUAL_BRANCH" != "$ISSUE_BRANCH" ]]; then
  echo "ERROR: Worktree created on wrong branch. Expected: $ISSUE_BRANCH, Got: $ACTUAL_BRANCH"
  # Clean up the broken worktree
  cd "${CLAUDE_PROJECT_DIR}"
  git worktree remove "${WORKTREE_PATH}" --force 2>/dev/null
  exit 1
fi
```

**Why this matters (M351):** Without verification, a worktree may be created but remain on the base
branch, causing commits to go to the wrong branch and bypass the review/merge workflow.

### Step 6: Check for Existing Work (M362, M363, M394)

**MANDATORY: Use the check-existing-work.sh script to detect existing commits.**

This check is deterministic (no LLM decision-making required) so it's implemented as a script
with full test coverage (M363).

```bash
EXISTING_WORK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/check-existing-work.sh" \
  --worktree "${WORKTREE_PATH}" \
  --base-branch "${BASE_BRANCH}")
```

Parse the JSON result and include in output:

```bash
HAS_EXISTING_WORK=$(echo "$EXISTING_WORK_RESULT" | jq -r '.has_existing_work')
EXISTING_COMMITS=$(echo "$EXISTING_WORK_RESULT" | jq -r '.existing_commits')
COMMIT_SUMMARY=$(echo "$EXISTING_WORK_RESULT" | jq -r '.commit_summary')
```

**Why this matters (M362):** Without checking for existing commits, the orchestrator spawns
an execution subagent for work that's already done. When `has_existing_work: true`, the main
agent should skip execution phase and proceed directly to review/merge.

**Why a script (M363):** This check is entirely deterministic - it just runs git commands and
returns JSON. Using a script instead of inline LLM instructions ensures:
- Test coverage for the detection logic
- Consistent behavior across invocations
- No risk of LLM misimplementing the check

### Step 6b: Check for Work Merged to Base (M394)

**MANDATORY: Check if task was already implemented on base branch.**

The check-existing-work.sh script only detects commits on the task branch. If work was
implemented directly on base (bypassing the task workflow), STATE.md won't reflect completion.

```bash
# Search for commits on base that mention this task name
TASK_COMMITS=$(git -C "${CLAUDE_PROJECT_DIR}" log --oneline --grep="${ISSUE_NAME}" "${BASE_BRANCH}" -5 2>/dev/null)

if [[ -n "$TASK_COMMITS" ]]; then
  # Found suspicious commits - return for user verification
  echo "WARNING: Found commits on base branch mentioning '${ISSUE_NAME}':"
  echo "$TASK_COMMITS"
  # Include in output JSON: "potentially_complete": true, "suspicious_commits": "..."
fi
```

**Why this matters (M394):** Work may be implemented directly on the base branch without using
the task workflow (e.g., previous session implemented work but didn't update STATE.md, or
manual work outside `/cat:work`). When suspicious commits are found:

1. Include `"potentially_complete": true` in output JSON
2. Include the suspicious commit hashes and messages
3. The orchestrator should prompt user to verify before proceeding

**This prevents duplicate work** when STATE.md shows `in-progress` but the actual implementation
already exists on the base branch.

### Step 7: Update STATE.md

Set task status to `in-progress`:

```yaml
- **Status:** in-progress
- **Progress:** 0%
- **Last Updated:** {date}
```

### Step 8: Return Result

Output the JSON result with all required fields.

## Fail-Fast Conditions

- Planning structure missing: Return ERROR immediately
- Script returns error: Return ERROR with message
- Lock unavailable: Return LOCKED, do NOT investigate
- Task exceeds hard limit: Return OVERSIZED
- **Worktree on wrong branch (M351):** Clean up and return ERROR

## Context Loaded

This skill loads:
- version-paths.md (path resolution)
- agent-architecture.md (context limits only)

Main agent does NOT need to load these - subagent handles internally.
