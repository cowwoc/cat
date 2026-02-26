# Plan: fix-work-merge-parent-state-uncommitted

## Problem

`work-merge` Step 6 (auto-complete decomposed parent) modifies the parent `STATE.md` via `sed -i` in the main
workspace **after** the worktree has already been merged and removed (Step 5). These changes are never committed.
The modified file sits as a dirty uncommitted change in the main workspace, where it can be unintentionally swept into
an unrelated future commit.

## Satisfies

None (infrastructure bugfix)

## Reproduction Code

```
# Merge an issue that is a sub-issue of a decomposed parent where all siblings are now complete.
# After /cat:work merge phase:
git -C /workspace status --porcelain
# Shows: M .claude/cat/issues/v2/v2.1/<parent-name>/STATE.md
# The parent's STATUS was changed to "closed" by sed -i but never committed.
```

## Expected vs Actual

- **Expected:** Parent `STATE.md` auto-complete is part of the squashed commit, so after merge the workspace is clean
- **Actual:** Parent `STATE.md` is modified post-merge in the main workspace and never committed

## Root Cause

Step 6 runs **after** Step 5 (merge + cleanup), meaning the worktree is already gone and the change lands directly in
the main workspace with no commit. The fix is to move the parent STATE.md update to **before** the merge (while the
worktree is still active), so the change is included in the squashed implementation commit.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — the check logic doesn't change, only where it runs in the sequence
- **Mitigation:** Verify worktree is clean before merge after the fix

## Files to Modify

- `plugin/skills/work-merge/first-use.md` — move Step 6 (auto-complete decomposed parent) to run before Step 5
  (merge and cleanup), inside the worktree

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** In `work-merge/first-use.md`, move the "Auto-Complete Decomposed Parent" logic from Step 6 to between
   Step 2 (Update STATE.md) and Step 3 (Rebase issue branch onto base), renaming the steps so numbering stays
   sequential.
   - The logic should run inside the worktree (`$WORKTREE_PATH`) so any parent STATE.md change is staged and committed
     as part of the implementation commit (via `git -C "$WORKTREE_PATH" add` + `git -C "$WORKTREE_PATH" commit --amend
     --no-edit` on the squashed commit, or included before squash).
   - Files: `plugin/skills/work-merge/first-use.md`

2. **Step 2:** Update the step that commits STATE.md (Step 2) to also check for parent STATE.md changes and include
   them in the same commit, so no separate amend is needed.

3. **Step 3:** Verify the post-merge workspace is clean (git status --porcelain returns empty).

## Post-conditions

- [ ] `plugin/skills/work-merge/first-use.md` has the parent auto-complete step moved to before the merge step
- [ ] Parent `STATE.md` updates are committed inside the worktree as part of the implementation commit
- [ ] No uncommitted STATE.md files remain in the main workspace after a successful merge
- [ ] Step numbering in `work-merge/first-use.md` is sequential with no gaps
