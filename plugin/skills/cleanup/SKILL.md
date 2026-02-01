---
description: Use when session crashed or locks blocking - cleans abandoned worktrees, lock files, and orphaned branches
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
---

# CAT Cleanup

## Purpose

All abandoned CAT artifacts (worktrees, locks, branches) are identified and cleaned up safely.

---

## When to Use

- A previous session crashed or was cancelled
- Lock files are blocking new execution
- Orphaned worktrees are cluttering the filesystem

---

## Safety Rules

- NEVER cd into a worktree that will be deleted - use `git -C <path>` instead
  *(Enforced by hook M342 - removal blocked if cwd is inside target)*
- ALWAYS check for uncommitted changes before removing worktrees
- ALWAYS ask user before removing anything with uncommitted work
- ALWAYS remove worktree BEFORE deleting its branch
- NEVER force-delete branches that might have unmerged commits

---

## Procedure

### Step 1: Survey Current State

Run all survey commands to gather current artifact state.

**Worktrees:**
```bash
git worktree list
```

**Issue locks:**
```bash
if [[ -d .claude/cat/locks ]]; then
  "${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" list "${CLAUDE_PROJECT_DIR}" 2>/dev/null
else
  echo "[]"
fi
```

**Context files:**
```bash
ls -la .cat-execution-context 2>/dev/null || echo "No context file"
```

**CAT branches:**
```bash
git branch -a | grep -E '(release/|worktree|[0-9]+\.[0-9]+-)' || echo "No CAT branches"
```

**Remote branch staleness (1-7 days idle):**
```bash
git fetch --prune 2>/dev/null
for remote_branch in $(git branch -r 2>/dev/null | grep -E 'origin/[0-9]+\.[0-9]+-' | tr -d ' '); do
  COMMIT_DATE=$(git log -1 --format='%ct' "$remote_branch" 2>/dev/null)
  NOW=$(date +%s)
  AGE_DAYS=$(( (NOW - COMMIT_DATE) / 86400 ))
  if [ "$AGE_DAYS" -ge 1 ] && [ "$AGE_DAYS" -le 7 ]; then
    AUTHOR=$(git log -1 --format='%an' "$remote_branch" 2>/dev/null)
    RELATIVE=$(git log -1 --format='%cr' "$remote_branch" 2>/dev/null)
    echo "STALE: $remote_branch (last: $AUTHOR, $RELATIVE)"
  fi
done
```

Use the **SURVEY_DISPLAY** box from SCRIPT OUTPUT CLEANUP BOXES.
Replace placeholders with actual survey data.

---

### Step 2: Identify Abandoned Artifacts

Analyze survey results to classify artifacts:

**Abandoned worktree indicators:**
- Lock file references session that is no longer active
- Worktree directory exists but has no recent activity
- No corresponding lock exists (orphaned)

**Stale lock indicators:**
- Lock age exceeds reasonable session duration (hours old)
- No heartbeat updates (if heartbeat tracking enabled)

For each lock, check status:
```bash
task_id="<from-survey>"
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" check "${CLAUDE_PROJECT_DIR}" "$task_id"
```

The output shows: `{"locked":true,"session_id":"...","age_seconds":...,"worktree":"..."}`

Present classification:

```
## Abandoned Artifacts

### Likely Abandoned
- <artifact>: <reason>

### Possibly Active
- <artifact>: <reason for caution>

### Safe to Keep
- <artifact>: <reason>
```

---

### Step 3: Check for Uncommitted Work

**CRITICAL: Use `git -C <path>` - NEVER cd into a worktree that will be deleted.**

For each worktree identified as abandoned:

```bash
WORKTREE_PATH="<path-from-survey>"
git -C "$WORKTREE_PATH" status --porcelain
```

If output is non-empty, there is uncommitted work.

Present findings:

```
## Uncommitted Work Check

### <worktree-path>
Status: CLEAN | HAS UNCOMMITTED WORK

If uncommitted:
  Modified files:
  - <file1>
  - <file2>

  Options:
  1. Commit changes first
  2. Stash: git -C <path> stash
  3. Discard: git -C <path> checkout -- . (DESTRUCTIVE)
  4. Skip this worktree
```

**BLOCKING: Do NOT proceed with removal until user confirms action for each worktree with uncommitted work.**

---

### Step 4: Get User Confirmation

Use the **PLAN_DISPLAY** box from SCRIPT OUTPUT CLEANUP BOXES.
Replace placeholders with actual cleanup plan data.

**BLOCKING: Do NOT execute cleanup without explicit user confirmation.**

---

### Step 5: Execute Cleanup

Execute in strict order. Errors should propagate - do not suppress with `|| true`.

**Order matters:**
1. Stale locks first (may be blocking worktree operations)
2. Worktrees second (git won't delete branch checked out in worktree)
3. Branches third (after worktrees released them)
4. Context files last

**Remove stale locks:**
```bash
task_id="<from-plan>"
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" force-release "${CLAUDE_PROJECT_DIR}" "$task_id"
```

**Remove worktrees:**
```bash
WORKTREE_PATH="<from-plan>"
git worktree remove "$WORKTREE_PATH" --force
```

**Remove orphaned branches:**
```bash
BRANCH_NAME="<from-plan>"
git branch -D "$BRANCH_NAME"
```

**Remove context file (if applicable):**
```bash
rm .cat-execution-context
```

Report each action:

```
## Cleanup Progress

- [x] Removed lock: <issue-id>
- [x] Removed worktree: <path>
- [x] Removed branch: <branch>
- [x] Removed context file
```

---

### Step 6: Verify Cleanup

Run verification commands:

```bash
echo "Remaining worktrees:"
git worktree list

echo "Remaining CAT branches:"
git branch -a | grep -E '(release/|worktree|[0-9]+\.[0-9]+-)' || echo "None"

echo "Remaining locks:"
if [[ -d .claude/cat/locks ]]; then
  ls .claude/cat/locks/*.lock 2>/dev/null || echo "None"
else
  echo "None"
fi
```

Use the **VERIFY_DISPLAY** box from SCRIPT OUTPUT CLEANUP BOXES.
Replace placeholders with actual verification results.

---

## Common Scenarios

### Session crashed mid-execution

**Symptoms:** Lock file exists, worktree may have partial work

**Action:**
1. Check worktree for uncommitted changes (Step 3)
2. Offer to commit, stash, or discard
3. Remove lock and worktree after user confirms

### User cancelled and wants fresh start

**Symptoms:** Multiple stale worktrees and lock files

**Action:**
1. Survey all artifacts (Step 1)
2. Confirm cleanup of each (Step 4)
3. Remove all confirmed artifacts (Step 5)

### Lock file blocking new execution

**Symptoms:** "Issue locked by another session" error but no active session

**Action:**
1. Identify specific lock via survey
2. Confirm no active work in associated worktree
3. Force-release the specific lock

### Orphaned branches after worktree removal

**Symptoms:** Branches exist but no worktrees reference them

**Action:**
1. List branches (Step 1)
2. Confirm they have no unique unmerged commits
3. Delete branches
