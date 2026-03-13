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

Before changing an issue from `in-progress` to any other status (`open`, `pending`, etc.), check git history:

```bash
ISSUE_NAME="issue-name-here"
git log --oneline --grep="$ISSUE_NAME" -5
git log --oneline -- ".cat/issues/*/v*/$ISSUE_NAME/" -5
```

| Git History Shows | Correct Action |
|-------------------|----------------|
| Commits implementing the issue | Mark as `completed` with commit reference |
| No relevant commits | Mark as `pending` (truly abandoned) |
| Partial commits | Check commit content, may be partial completion |

**Why this matters:** An issue may show `in-progress` with 0% because STATE.md wasn't updated after
work was completed on the target branch. Resetting to `pending` causes duplicate work.

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
- Branch age exceeds 4 hours — do not label a worktree or lock as "abandoned" unless idle time meets this threshold
- No heartbeat updates (if heartbeat tracking enabled)

**Corrupt issue directories:**
- Issue directories that contain STATE.md but no PLAN.md
- Listed in the survey output under "⚠ Corrupt Issue Directories"
- These cannot be executed without recovery (delete or recreate PLAN.md)

For each lock, check status and record the full `session_id` (complete UUID, not truncated) for use in Steps 4 and 5:
```bash
issue_id="<from-survey>"
"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" check "$issue_id"
```

Derive age from the branch's last commit time (not the lock file). If the branch does not exist, treat the artifact
as stale (safe to remove):
```bash
branch="<issue-id>"
if ! git rev-parse --verify "$branch" >/dev/null 2>&1; then
  echo "WARNING: Branch '$branch' does not exist. Treating artifact as stale."
  commit_timestamp=0
else
  commit_timestamp=$(git log -1 --format=%ct "$branch")
fi
```

Compute age as `now - commit_timestamp`. Classify each artifact based on branch age:
- **Stale** (age ≥ 4 hours): safe to remove with the default option
- **Recent** (age < 4 hours): requires explicit user confirmation via the secondary option

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

**CRITICAL: Before removing a worktree, ensure your shell is NOT inside it.** If you are inside the worktree, `cd /workspace` first.

For each worktree identified as abandoned (return to `/workspace` after each inspection to avoid
being inside the worktree during later removal):

```bash
WORKTREE_PATH="<path-from-survey>"
cd "$WORKTREE_PATH"
git status --porcelain
cd /workspace
```

If output is non-empty, there is uncommitted work.

**Before assessing risk, check if the source branch is already merged:**

```bash
BRANCH_NAME="<branch-from-survey>"
cd "$WORKTREE_PATH"
# Derive the issue version and name from the branch.
# Version is the leading digits-and-dots segment (e.g., "2.1" from "2.1-fix-bug",
# "2.1.1" from "2.1.1-fix-bug"). Use regex to extract version prefix reliably.
if [[ "$BRANCH_NAME" =~ ^([0-9]+(\.[0-9]+)*)-(.+)$ ]]; then
  ISSUE_VERSION="${BASH_REMATCH[1]}"
  ISSUE_NAME="${BASH_REMATCH[3]}"
else
  echo "SKIP: Branch '$BRANCH_NAME' does not match expected format '<version>-<issue-name>'. Excluding from cleanup."
  MERGED="SKIP"
  cd /workspace
  continue  # Skip to next worktree — do not include in plan or cleanup
fi
# Use exact version directory match (trailing /) to avoid 2.1 matching 2.10
STATE_FILE=$(find "$WORKTREE_PATH/.cat/issues/" -path "*/${ISSUE_VERSION}/${ISSUE_NAME}/STATE.md" 2>/dev/null | head -1)
TARGET_BRANCH=""
if [[ -n "$STATE_FILE" ]]; then
  TARGET_BRANCH=$(grep "Target Branch:" "$STATE_FILE" | sed 's/.*\*\*Target Branch:\*\*\s*//')
fi
if [[ -z "$TARGET_BRANCH" ]]; then
  echo "Warning: Could not determine target branch from STATE.md — skipping merge check"
  MERGED="unknown"
else
  MERGED=$(git branch --merged "$TARGET_BRANCH" | grep -q "^[* ]*${BRANCH_NAME}$" && echo "yes" || echo "no")
fi
```

| Merge Status | Risk Assessment |
|---|---|
| Branch is merged into target | Low risk — branch commits are safe on target; only uncommitted modifications would be lost |
| Branch is NOT merged | High risk — discarding uncommitted work may also represent the only copy of work in progress |
| Unknown (no Target Branch in STATE.md) | Treat as high risk — cannot confirm merge status |
| SKIP (branch name format unrecognized) | Excluded from cleanup — not shown in plan box or cleanup options. Requires manual review |

Present findings:

```
## Uncommitted Work Check

### <worktree-path>
Status: CLEAN | HAS UNCOMMITTED WORK
Branch merge status: MERGED into <target-branch> | NOT MERGED

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

**After inspecting each worktree, return to the main workspace before proceeding:**

```bash
cd /workspace
```

**BLOCKING: Do NOT proceed with removal until user confirms action for each worktree with uncommitted work.**

---

### Step 4: Get User Confirmation

Generate the cleanup plan box by invoking the handler with your analysis. **Exclude any worktree that was
SKIP-flagged in Step 3** (branch name did not match the expected `<version>-<issue-name>` format). Only worktrees
that passed branch-name parsing in Step 3 may appear in `worktrees_to_remove` and `branches_to_remove` below.

For each lock, include `issue_id`, `session` (first 8 chars of session ID, for display only), and `age_seconds`
(derived from the branch's last commit time via `git log -1 --format=%ct <branch>`). For each worktree, include
`age_seconds` (same source). **Note:** The `session` field in the JSON below is truncated for display purposes
only. You must retain the full session UUID (recorded in Step 2) separately for use in Step 5 re-validation:

```bash
echo '{
  "handler": "cleanup",
  "context": {
    "phase": "plan",
    "locks_to_remove": [
      {"issue_id": "2.1-issue-name", "session": "eb68bb02", "age_seconds": 326}
    ],
    "worktrees_to_remove": [
      {"path": "${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name",
       "branch": "2.1-issue-name", "age_seconds": 326}
    ],
    "branches_to_remove": ["2.1-issue-name"],
    "stale_remotes": []
  }
}' | "${CLAUDE_PLUGIN_ROOT}/client/bin/get-cleanup-output" --phase plan
```

Replace the example values with actual items identified in Step 2.
The `stale_remotes` field is reserved for future use — always pass an empty array and do not act on it.
The resulting box will be output verbatim. It displays session ID and age per lock, and classifies
each artifact as **stale** (age ≥ 4 hours) or **recent** (age < 4 hours).

Then use AskUserQuestion with these options (in this order):

1. **Remove stale artifacts only (≥4 hours)** *(default)* — remove only locks and worktrees with age ≥ 4 hours,
   plus all orphaned branches regardless of age
2. **Remove all artifacts (including recent)** — remove all identified artifacts regardless of age
3. **Delete all corrupt issue directories** — delete all directories listed under "⚠ Corrupt Issue Directories"
4. **Abort** — stop without removing anything

If the user selects option 1, skip any locks and worktrees classified as "recent" in the plan box.
If the user selects option 2, remove all items in the plan box.
If the user selects option 3, for each corrupt issue directory:
1. Display the contents of its STATE.md to the user (it may contain useful state data such as target branch or progress)
2. Ask the user to confirm deletion of that specific directory after reviewing its contents
3. Delete the directory using `/cat:safe-rm-agent`
4. Stage and commit the deletion. Validate the path is non-empty before staging:
   ```bash
   DELETED_DIR="<deleted-directory-path>"
   ISSUE_NAME="<issue-name>"
   # Validate ISSUE_NAME contains only alphanumeric characters, hyphens, and dots
   if [[ ! "$ISSUE_NAME" =~ ^[a-zA-Z0-9._-]+$ ]]; then
     echo "ERROR: Issue name contains unexpected characters: '$ISSUE_NAME'. Skipping commit." >&2
   elif [[ -z "$DELETED_DIR" ]]; then
     echo "ERROR: Deleted directory path is empty. Skipping commit." >&2
   else
     git add -- "$DELETED_DIR" && git commit -m "planning: remove corrupt issue directory $ISSUE_NAME"
   fi
   ```

After completing the selected option (1, 2, or 3), if other artifact categories remain (e.g., corrupt directories
exist after removing stale artifacts, or abandoned worktrees exist after deleting corrupt directories), re-prompt
the user with the remaining applicable options.

**BLOCKING: Do NOT execute cleanup without explicit user confirmation.**

---

### Step 5: Execute Cleanup

Execute in strict order. Errors should propagate - do not suppress with `|| true`.

**Order matters:**
1. Per-issue cleanup: for each issue, re-validate lock, remove worktree, then release lock (in that order)
2. Orphaned worktrees (no associated lock): re-validate then remove
3. Orphaned branches (after worktrees released them)
4. Context files last

**CRITICAL ordering within each issue:** Remove the worktree BEFORE releasing the lock. If the lock is released
first, a new session can acquire the lock and start using the worktree in the window between lock release and
worktree removal. Process each issue atomically: re-validate, remove worktree, then release lock.

**Per-issue cleanup (worktree + lock):**

For each issue that has both a worktree and a lock to remove, process them as an atomic unit:

```bash
WORKTREE_PATH="<from-plan>"
issue_id="<issue-id-for-this-worktree>"
EXPECTED_SESSION="<full-session-id-from-step-2>"

# Re-validate: check the lock is still held by the expected session
CURRENT_LOCK=$("${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" check "$issue_id" 2>&1)
if echo "$CURRENT_LOCK" | grep -qF "$EXPECTED_SESSION"; then
  # Lock still held by the same (abandoned) session — safe to remove worktree then release lock
  cd /workspace
  if git worktree remove "$WORKTREE_PATH" --force; then
    if "${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" force-release "$issue_id"; then
      echo "OK: Worktree removed and lock released for '$issue_id'."
    else
      echo "ERROR: Worktree removed but lock release failed for '$issue_id'. Lock may still be held — manual release may be needed."
    fi
  else
    echo "ERROR: Failed to remove worktree '$WORKTREE_PATH'. Lock for '$issue_id' NOT released (preserving invariant)."
  fi
else
  echo "WARNING: Lock for '$issue_id' is now held by a different session. Skipping worktree and lock."
fi
```

**Orphaned worktrees (no lock in Step 2):**

For worktrees that had no associated lock in Step 2, a new session may have since acquired a lock
and started using the worktree. Re-check before removing:

```bash
WORKTREE_PATH="<from-plan>"
issue_id="<issue-id-for-this-worktree>"
# Re-check: if a lock now exists, a new session has claimed this worktree
CURRENT_LOCK=$("${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" check "$issue_id" 2>&1)
if echo "$CURRENT_LOCK" | grep -q "locked"; then
  echo "WARNING: Worktree for '$issue_id' is now locked by a new session. Skipping removal."
else
  cd /workspace
  git worktree remove "$WORKTREE_PATH" --force
fi
```

**Locks without worktrees:**

For locks that have no associated worktree (lock-only cleanup), re-validate and release:

```bash
issue_id="<from-plan>"
EXPECTED_SESSION="<full-session-id-from-step-2>"
# Re-check current lock owner (use -F for literal string match, not regex substring)
CURRENT_LOCK=$("${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" check "$issue_id" 2>&1)
if echo "$CURRENT_LOCK" | grep -qF "$EXPECTED_SESSION"; then
  "${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" force-release "$issue_id"
else
  echo "WARNING: Lock for '$issue_id' is now held by a different session. Skipping."
fi
```

**Remove orphaned branches (with merge safety check):**
```bash
BRANCH_NAME="<from-plan>"
# Try safe delete first — capture stderr to distinguish error types
DELETE_OUTPUT=$(git branch -d "$BRANCH_NAME" 2>&1)
DELETE_EXIT=$?
if [[ $DELETE_EXIT -ne 0 ]]; then
  if echo "$DELETE_OUTPUT" | grep -q "not fully merged"; then
    echo "WARNING: Branch '$BRANCH_NAME' has unmerged commits. Skipping — use 'git branch -D' manually after review."
  else
    echo "ERROR: Failed to delete branch '$BRANCH_NAME': $DELETE_OUTPUT"
  fi
fi
```

**NEVER use `git branch -D` (force delete) in automated cleanup.** Always use `git branch -d` (safe delete).
If a branch has unmerged commits, report it to the user and skip it rather than force-deleting.

**Reset stale in-progress issues and commit the change:**
```bash
STATE_FILE="<path-to-STATE.md>"
sed -i 's/\*\*Status:\*\* in-progress/**Status:** open/' "$STATE_FILE"
git add "$STATE_FILE"
git commit -m "planning: reset stale issue to open status"
```

**Remove context file (if applicable):**
```bash
rm "${CLAUDE_PROJECT_DIR}/.cat-execution-context"
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
git branch -a | grep -E '(release/|/worktrees/|^[[:space:]]*[0-9]+\.[0-9]+-)' || echo "None"

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
    "remaining_worktrees": ["/workspace (main)"],
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

