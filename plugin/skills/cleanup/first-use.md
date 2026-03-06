<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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

- Before removing a worktree, ensure your shell is NOT inside it (cd out first if needed)
  *(Enforced by hook M342 - removal blocked if cwd is inside target)*
- ALWAYS check for uncommitted changes before removing worktrees
- ALWAYS ask user before removing anything with uncommitted work
- ALWAYS remove worktree BEFORE deleting its branch
- NEVER force-delete branches that might have unmerged commits

### STATE.md Reset Safety

**When resetting stuck `in-progress` issues, verify implementation status first:**

Before changing an issue from `in-progress` to `open`, check git history:

```bash
ISSUE_NAME="issue-name-here"
git log --oneline --grep="$ISSUE_NAME" -5
git log --oneline -- ".claude/cat/issues/*/v*/$ISSUE_NAME/" -5
```

| Git History Shows | Correct Action |
|-------------------|----------------|
| Commits implementing the issue | Mark as `completed` with commit reference |
| No relevant commits | Mark as `open` (truly abandoned) |
| Partial commits | Check commit content, may be partial completion |

**Why this matters:** An issue may show `in-progress` with 0% because STATE.md wasn't updated after
work was completed on the target branch. Resetting to `open` causes duplicate work.

---

## Procedure

### Step 1: Survey Current State

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" cleanup`

---

### Step 2: Identify Abandoned Artifacts

Analyze survey results to classify artifacts:

**Stale in-progress issues:**
- STATE.md shows `in-progress` but no worktree, lock, or branch exists
- Verify via git history before resetting (see Safety Rules above)
- Reset confirmed stale issues to `open` status

**Abandoned worktree indicators:**
- Lock file references session that is no longer active
- Worktree directory exists but has no recent activity
- No corresponding lock exists (orphaned)

**Stale lock indicators:**
- Lock age exceeds 4 hours (14400 seconds) — do not label a worktree or lock as "abandoned" unless idle time meets this threshold
- No heartbeat updates (if heartbeat tracking enabled)

For each lock, check status:
```bash
issue_id="<from-survey>"
"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" check "$issue_id"
```

The output shows: `{"locked":true,"session_id":"...","age_seconds":...,"worktree":"..."}`

Classify each artifact as **stale** (age ≥ 4 hours / 14400 seconds) or **recent** (age < 4 hours):

Present classification:

```
## Abandoned Artifacts

### Stale (≥ 4 hours) — safe to remove by default
- <artifact>: session <first-8-chars-of-session-id>, age <Xh Ym> — <reason>

### Recent (< 4 hours) — requires explicit confirmation to remove
- <artifact>: session <first-8-chars-of-session-id>, age <Xm Ys> — <reason for caution>

### Safe to Keep
- <artifact>: <reason>
```

---

### Step 3: Check for Uncommitted Work

**CRITICAL: Before removing a worktree, ensure your shell is NOT inside it.** If you are inside the worktree, `cd "${CLAUDE_PROJECT_DIR}"` first.

For each worktree identified as abandoned:

```bash
WORKTREE_PATH="<path-from-survey>"
cd "$WORKTREE_PATH"
git status --porcelain
```

If output is non-empty, there is uncommitted work.

**Before assessing risk, check if the source branch is already merged:**

```bash
BRANCH_NAME="<branch-from-survey>"
cd "$WORKTREE_PATH"
# Check if branch HEAD is reachable from any other branch (no naming convention assumptions)
BRANCH_HEAD=$(git rev-parse "$BRANCH_NAME")
CONTAINING=$(git branch --contains "$BRANCH_HEAD" | grep -v "^[* ]*${BRANCH_NAME}$" | head -1)
if [[ -n "$CONTAINING" ]]; then
  MERGED="yes"
else
  MERGED="no"
fi
```

| Merge Status | Risk Assessment |
|---|---|
| Branch commits reachable from another branch | Low risk — commits are safe; only uncommitted modifications would be lost |
| Branch commits NOT reachable from any other branch | High risk — discarding may lose the only copy of work in progress |

Present findings:

```
## Uncommitted Work Check

### <worktree-path>
Status: CLEAN | HAS UNCOMMITTED WORK
Branch merge status: MERGED (reachable from another branch) | NOT MERGED

If uncommitted:
  Modified files:
  - <file1>
  - <file2>

  Note: Branch commits are safe on <target-branch>. Only the uncommitted file modifications would be lost.
  (Include this note only when branch is merged — omit when not merged.)

  Options:
  1. Commit changes first
  2. Stash: cd <path> && git stash
  3. Discard the uncommitted modifications: cd <path> && git checkout -- .
  4. Skip this worktree
```

**BLOCKING: Do NOT proceed with removal until user confirms action for each worktree with uncommitted work.**

---

### Step 4: Get User Confirmation

Generate two cleanup plan boxes — one for stale artifacts (≥ 4 hours) and one for all artifacts — by invoking the
handler with your analysis. Use the stale-only list for the default option:

```bash
# Stale-only plan (age ≥ 4 hours / 14400 seconds)
echo '{
  "handler": "cleanup",
  "context": {
    "phase": "plan",
    "locks_to_remove": ["2.1-stale-issue-name"],
    "worktrees_to_remove": [{"path": "...", "branch": "2.1-stale-issue-name"}],
    "branches_to_remove": ["2.1-stale-issue-name"],
    "stale_remotes": []
  }
}' | "${CLAUDE_PLUGIN_ROOT}/client/bin/get-cleanup-output" --phase plan

# All-artifacts plan (stale + recent)
echo '{
  "handler": "cleanup",
  "context": {
    "phase": "plan",
    "locks_to_remove": ["2.1-stale-issue-name", "2.1-recent-issue-name"],
    "worktrees_to_remove": [
      {"path": "...", "branch": "2.1-stale-issue-name"},
      {"path": "...", "branch": "2.1-recent-issue-name"}
    ],
    "branches_to_remove": ["2.1-stale-issue-name", "2.1-recent-issue-name"],
    "stale_remotes": []
  }
}' | "${CLAUDE_PLUGIN_ROOT}/client/bin/get-cleanup-output" --phase plan
```

Replace the example values with the artifacts identified in Step 2.

Then use AskUserQuestion with these three options:

```
1. Remove stale artifacts only (≥ 4 hours) [DEFAULT]
   Locks: <list of stale lock IDs with session and age, or "none">
   Worktrees: <list of stale worktree paths, or "none">
   Branches: <list of stale branch names, or "none">

2. Remove all artifacts (including recent)
   Also removes: <list of recent artifact IDs with session and age, or "none additional">

3. Abort — stop without removing anything
```

For each artifact in options 1 and 2, include session ID (first 8 chars) and age. For example:
`2.1-fix-catid-path-resolution — 5m 26s, session eb68bb02`

**BLOCKING: Do NOT execute cleanup without explicit user confirmation.**

---

### Step 5: Execute Cleanup

Execute only the artifacts in scope based on the user's choice (stale-only or all). Errors should propagate - do not
suppress with `|| true`.

**Order matters:**
1. Stale locks first (may be blocking worktree operations)
2. Worktrees second (git won't delete branch checked out in worktree)
3. Branches third (after worktrees released them)
4. Context files last

**Remove stale locks:**
```bash
issue_id="<from-plan>"
"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" force-release "$issue_id"
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

**Reset stale in-progress issues:**
```bash
STATE_FILE="<path-to-STATE.md>"
sed -i 's/\*\*Status:\*\* in-progress/**Status:** open/' "$STATE_FILE"
```

**Remove context file (if applicable):**
```bash
rm .cat-execution-context
```

Report each action:

```
## Cleanup Progress

- [x] Reset stale issue: <issue-id> (in-progress -> open)
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
source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"
if [[ -d "${LOCKS_DIR}" ]]; then
  ls "${LOCKS_DIR}"/*.lock 2>/dev/null || echo "None"
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
    "remaining_worktrees": ["<project-dir> (main)"],
    "remaining_branches": [],
    "remaining_locks": []
  }
}' | "${CLAUDE_PLUGIN_ROOT}/client/bin/get-cleanup-output" --phase verify
```

Replace the example values with actual cleanup results.
The resulting box will be output verbatim.

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

