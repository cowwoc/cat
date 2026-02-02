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
  "task_id": "2.1-task-name",
  "major": "2",
  "minor": "1",
  "task_name": "task-name",
  "task_path": "/workspace/.claude/cat/issues/v2/v2.1/task-name",
  "worktree_path": "/workspace/.worktrees/2.1-task-name",
  "branch": "2.1-task-name",
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
- `found` - Continue to worktree creation
- `no_tasks` - Return NO_TASKS status
- `locked` - Return LOCKED status with owner info

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
TASK_BRANCH="${MAJOR}.${MINOR}-${TASK_NAME}"
BASE_BRANCH=$(git branch --show-current)
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.worktrees/${TASK_BRANCH}"

git worktree add -b "${TASK_BRANCH}" "${WORKTREE_PATH}" HEAD
echo "${BASE_BRANCH}" > "$(git rev-parse --git-common-dir)/worktrees/${TASK_BRANCH}/cat-base"
```

### Step 5: Verify Worktree Branch (M351)

**MANDATORY: Verify the worktree is on the correct branch before proceeding.**

```bash
cd "${WORKTREE_PATH}"
ACTUAL_BRANCH=$(git branch --show-current)

if [[ "$ACTUAL_BRANCH" != "$TASK_BRANCH" ]]; then
  echo "ERROR: Worktree created on wrong branch. Expected: $TASK_BRANCH, Got: $ACTUAL_BRANCH"
  # Clean up the broken worktree
  cd "${CLAUDE_PROJECT_DIR}"
  git worktree remove "${WORKTREE_PATH}" --force 2>/dev/null
  exit 1
fi
```

**Why this matters (M351):** Without verification, a worktree may be created but remain on the base
branch, causing commits to go to the wrong branch and bypass the review/merge workflow.

### Step 6: Check for Existing Work (M362)

**MANDATORY: Check if task branch already has commits before returning READY.**

```bash
cd "${WORKTREE_PATH}"
BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-base" 2>/dev/null || echo "${BASE_BRANCH}")
EXISTING_COMMITS=$(git log --oneline "${BASE_BRANCH}..HEAD" 2>/dev/null | wc -l)

if [[ "$EXISTING_COMMITS" -gt 0 ]]; then
  # Task has existing work - include in output for main agent to handle
  COMMIT_LIST=$(git log --oneline "${BASE_BRANCH}..HEAD")
  HAS_EXISTING_WORK=true
else
  HAS_EXISTING_WORK=false
fi
```

**Include in JSON output:**

```json
{
  "has_existing_work": true,
  "existing_commits": 3,
  "commit_summary": "eedb1c45 config: add conservative extraction..."
}
```

**Why this matters (M362):** Without checking for existing commits, the orchestrator spawns
an execution subagent for work that's already done. When `has_existing_work: true`, the main
agent should skip execution phase and proceed directly to review/merge.

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
