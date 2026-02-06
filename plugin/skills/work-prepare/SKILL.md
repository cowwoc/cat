---
description: Preparation phase for /cat:work - finds task, acquires lock, creates worktree
user-invocable: false
---

# Work Phase: Prepare

**ALGORITHM DOCUMENTATION ONLY. Runtime logic implemented in `plugin/scripts/work-prepare.py`.**

This document describes the preparation phase algorithm for `/cat:work`. The actual implementation
is a deterministic Python script that performs all steps without LLM decision-making.

Historical context: This was originally a subagent skill that spawned an LLM to execute these steps.
The skill has been replaced with work-prepare.py for performance (4s vs 50s), but this documentation
is retained as algorithm reference.

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
  "worktree_path": "/workspace/.claude/cat/worktrees/2.1-issue-name",
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
  "suggestion": "Next action to take",
  "blocked_tasks": [
    {
      "issue_id": "2.1-issue-name",
      "blocked_by": ["2.1-dependency-name"],
      "reason": "Depends on 2.1-dependency-name (status: in-progress)"
    }
  ],
  "locked_tasks": [
    {
      "issue_id": "2.1-issue-name",
      "locked_by": "session-uuid"
    }
  ],
  "closed_count": 0,
  "total_count": 0
}
```

**Extended failure info (M441):** When returning NO_TASKS, include `blocked_tasks` and `locked_tasks`
arrays so the parent agent can report WHY no tasks are available. Also include `closed_count` and
`total_count` for context. Omit empty arrays.

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
- Marking tasks "closed" temporarily to hide them from discovery
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

Use the discovery script with `--exclude-pattern` when arguments contain a filter (M435):

```bash
# Convert natural language filter to glob pattern:
#   "skip compression" → "compress*"
#   "skip batch tasks" → "*-batch-*"
#   "only migration"   → exclude everything NOT matching: use no --exclude-pattern, filter result in memory

# When filter maps to an exclusion pattern:
RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/get-available-issues.sh" --session-id "${SESSION_ID}" --exclude-pattern "compress*")

# When no filter:
RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/get-available-issues.sh" --session-id "${SESSION_ID}")
```

Parse the result and handle statuses:
- `found` - Continue to worktree creation
- `not_found` - Return NO_TASKS with extended info (see below)
- `locked` - Return LOCKED status with owner info

**Extended failure info (M441):** When discovery returns `not_found`, gather diagnostic context
before returning NO_TASKS. Scan issue directories to report why no tasks are available:

```bash
# Gather diagnostic info for NO_TASKS response
BLOCKED_TASKS='[]'
LOCKED_TASKS='[]'
CLOSED_COUNT=0
TOTAL_COUNT=0

for issue_dir in .claude/cat/issues/v*/v*/*/ ; do
  [ -f "$issue_dir/STATE.md" ] || continue
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  STATUS=$(grep -oP '(?<=\*\*Status:\*\* ).*' "$issue_dir/STATE.md")
  case "$STATUS" in
    closed) CLOSED_COUNT=$((CLOSED_COUNT + 1)) ;;
    open|in-progress)
      DEPS=$(grep -oP '(?<=\*\*Dependencies:\*\* \[).*?(?=\])' "$issue_dir/STATE.md")
      ISSUE_NAME=$(basename "$issue_dir")
      if [ -n "$DEPS" ] && [ "$DEPS" != "" ]; then
        # Check each dependency's status
        UNRESOLVED_DEPS='[]'
        IFS=', ' read -ra DEP_ARRAY <<< "$DEPS"
        for dep in "${DEP_ARRAY[@]}"; do
          # Find dependency's STATE.md across ALL versions (cross-version deps are valid)
          DEP_STATE=$(find .claude/cat/issues -path "*/$dep/STATE.md" 2>/dev/null | head -1)
          if [ -n "$DEP_STATE" ] && [ -f "$DEP_STATE" ]; then
            DEP_STATUS=$(grep -oP '(?<=\*\*Status:\*\* ).*' "$DEP_STATE")
            if [ "$DEP_STATUS" != "closed" ]; then
              UNRESOLVED_DEPS=$(echo "$UNRESOLVED_DEPS" | jq --arg dep "$dep" --arg status "$DEP_STATUS" '. + [{"id": $dep, "status": $status}]')
            fi
          else
            # Dependency not found - treat as unresolved
            UNRESOLVED_DEPS=$(echo "$UNRESOLVED_DEPS" | jq --arg dep "$dep" '. + [{"id": $dep, "status": "not_found"}]')
          fi
        done
        # Only add to BLOCKED_TASKS if there are unresolved dependencies
        UNRESOLVED_COUNT=$(echo "$UNRESOLVED_DEPS" | jq 'length')
        if [ "$UNRESOLVED_COUNT" -gt 0 ]; then
          UNRESOLVED_IDS=$(echo "$UNRESOLVED_DEPS" | jq '[.[].id]')
          REASON=$(echo "$UNRESOLVED_DEPS" | jq -r 'map("\(.id) (\(.status))") | join(", ")')
          BLOCKED_TASKS=$(echo "$BLOCKED_TASKS" | jq --arg id "$ISSUE_NAME" --argjson deps "$UNRESOLVED_IDS" --arg reason "$REASON" '. + [{"issue_id": $id, "blocked_by": $deps, "reason": $reason}]')
        fi
      fi
      ;;
  esac
done
```

Include these fields in the NO_TASKS JSON response. This allows the parent agent to explain
to the user exactly why no tasks are executable.

**Filtering (M364, M435):** Use `--exclude-pattern` for exclusion filters. The script handles
glob matching natively and continues searching for the next eligible issue after excluding matches.
Only use in-memory filtering for inclusion filters (e.g., "only migration") where the script
has no native support.

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
WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.claude/cat/worktrees/${ISSUE_BRANCH}"

git worktree add -b "${ISSUE_BRANCH}" "${WORKTREE_PATH}" HEAD
echo "${BASE_BRANCH}" > "$(git rev-parse --git-common-dir)/worktrees/${ISSUE_BRANCH}/cat-base"
```

### Step 5: Verify Worktree Branch (M351)

**MANDATORY: Verify the worktree is on the correct branch before proceeding.**

```bash
ACTUAL_BRANCH=$(git -C "${WORKTREE_PATH}" branch --show-current)

if [[ "$ACTUAL_BRANCH" != "$ISSUE_BRANCH" ]]; then
  echo "ERROR: Worktree created on wrong branch. Expected: $ISSUE_BRANCH, Got: $ACTUAL_BRANCH"
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

### Step 7: Update STATE.md (M432)

**MANDATORY: Update STATE.md in the WORKTREE, not in the main workspace.**

The worktree was created in Step 4. Update the STATE.md copy inside it:

```bash
# CORRECT: Edit in worktree
STATE_FILE="${WORKTREE_PATH}/.claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${ISSUE_NAME}/STATE.md"

# WRONG: Editing in main workspace (pollutes main branch)
# STATE_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/issues/..."
```

Set task status to `in-progress`:

```yaml
- **Status:** in-progress
- **Progress:** 0%
- **Last Updated:** {date}
```

**Do NOT modify any files in `${CLAUDE_PROJECT_DIR}` directly.** All file modifications
must be in `${WORKTREE_PATH}` so they are isolated to the task branch.

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
