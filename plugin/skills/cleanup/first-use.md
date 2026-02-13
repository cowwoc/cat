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

### STATE.md Reset Safety (M399)

**When resetting stuck `in-progress` tasks, verify implementation status first:**

Before changing a task from `in-progress` to `pending`, check git history:

```bash
ISSUE_NAME="issue-name-here"
git log --oneline --grep="$TASK_NAME" -5
git log --oneline -- ".claude/cat/issues/*/v*/$TASK_NAME/" -5
```

| Git History Shows | Correct Action |
|-------------------|----------------|
| Commits implementing the task | Mark as `completed` with commit reference |
| No relevant commits | Mark as `pending` (truly abandoned) |
| Partial commits | Check commit content, may be partial completion |

**Why this matters:** A task may show `in-progress` with 0% because STATE.md wasn't updated after
work was completed on the base branch. Resetting to `pending` causes duplicate work.

---

## Procedure

### Step 1: Survey Current State

**Look for script output survey in SCRIPT OUTPUT SURVEY DISPLAY above.**

If found, copy and output that box EXACTLY as shown.

**If SCRIPT OUTPUT SURVEY DISPLAY not found:**
```
FAIL: SCRIPT OUTPUT SURVEY DISPLAY not found.
Handler cleanup_handler.py should have provided this via additionalContext.
Check that hooks are properly loaded.
```
Do NOT manually construct output or invoke scripts. Output the error and STOP.

If NOT found (preprocessing failed), STOP. Do NOT manually run scripts or construct boxes.

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
issue_id="<from-survey>"
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" check "${CLAUDE_PROJECT_DIR}" "$issue_id"
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

Generate the cleanup plan box by invoking the handler with your analysis:

```bash
echo '{
  "handler": "cleanup",
  "context": {
    "phase": "plan",
    "locks_to_remove": ["2.1-issue-name"],
    "worktrees_to_remove": [{"path": "/workspace/.claude/cat/worktrees/2.1-issue-name", "branch": "2.1-issue-name"}],
    "branches_to_remove": ["2.1-issue-name"],
    "stale_remotes": []
  }
}' | python3 "${CLAUDE_PLUGIN_ROOT}/hooks/invoke-handler.py"
```

Replace the example values with actual items identified in Step 2.
Copy and output the resulting box EXACTLY as shown.

Then use AskUserQuestion to confirm before proceeding.

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
issue_id="<from-plan>"
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" force-release "${CLAUDE_PROJECT_DIR}" "$issue_id"
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

Generate the verification box by invoking the handler with cleanup results:

```bash
echo '{
  "handler": "cleanup",
  "context": {
    "phase": "verify",
    "removed_counts": {"locks": 1, "worktrees": 1, "branches": 1},
    "remaining_worktrees": ["/workspace (main)"],
    "remaining_branches": [],
    "remaining_locks": []
  }
}' | python3 "${CLAUDE_PLUGIN_ROOT}/hooks/invoke-handler.py"
```

Replace the example values with actual cleanup results.
Copy and output the resulting box EXACTLY as shown.

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
