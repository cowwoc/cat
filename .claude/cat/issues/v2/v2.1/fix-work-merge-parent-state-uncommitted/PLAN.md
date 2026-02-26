# Plan: fix-work-merge-parent-state-uncommitted

## Problem

Two git-* skills contain file-specific workarounds instead of structural prevention:

1. **work-merge** Steps 6-7 modify files (`STATE.md`, `CHANGELOG.md`) in the main workspace **after** the worktree has
   been merged and removed (Step 5). These changes are never committed, leaving dirty files that can be swept into
   unrelated future commits.

2. **git-squash** has a STATE.md-specific "Automatic STATE.md Preservation" section (lines 309-334) that saves/restores
   `STATE.md` around squash operations. This is redundant — the squash tool already verifies content correctness via
   `backup_verified: true` and `git diff "$BACKUP"`. The STATE.md-specific check protects only one file when the
   general verification should protect all files.

Both are detection-after-the-fact patterns when the problems can be prevented by construction.

## Satisfies

None (infrastructure bugfix)

## Root Cause

**work-merge:** Steps 6 (auto-complete parent) and 7 (update changelog) run after Step 5 (merge + cleanup), meaning
the worktree is gone and file modifications land in the main workspace with no commit. Fix: move all file modifications
to before the merge step so they're committed in the worktree.

**git-squash:** The STATE.md preservation section was added as a symptom-specific workaround. The general squash
verification (`git diff "$BACKUP"` must be empty) already covers all files. If STATE.md is reverted, the diff check
catches it — no file-specific logic needed.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal — work-merge reordering preserves the same logic, git-squash still has general
  verification
- **Mitigation:** Verify worktree is clean before merge; verify git-squash diff check covers STATE.md

## Files to Modify

- `plugin/skills/work-merge/first-use.md` — reorder Steps 6-7 to before Step 5
- `plugin/skills/git-squash/first-use.md` — remove "Automatic STATE.md Preservation" section

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** In `work-merge/first-use.md`, move Steps 6 (auto-complete decomposed parent) and 7 (update changelog)
   to before Step 5 (merge and cleanup). Renumber all steps sequentially. The moved logic must operate inside the
   worktree (`$WORKTREE_PATH`) so changes are committed as part of the implementation commit before merge.
   - Files: `plugin/skills/work-merge/first-use.md`

2. **Step 2:** In `git-squash/first-use.md`, remove the "Automatic STATE.md Preservation" section (lines 309-334).
   The general verification (`git diff "$BACKUP"` must be empty) already protects all files including STATE.md.
   - Files: `plugin/skills/git-squash/first-use.md`

3. **Step 3:** Verify both files have correct sequential step numbering with no gaps.

## Post-conditions

- [ ] `work-merge/first-use.md`: all file modifications (parent STATE.md, changelog) happen before the merge step
- [ ] `work-merge/first-use.md`: step numbering is sequential with no gaps
- [ ] `git-squash/first-use.md`: no STATE.md-specific preservation section exists
- [ ] `git-squash/first-use.md`: general verification (`git diff "$BACKUP"`) is still present and unchanged
- [ ] No file-specific workarounds remain in either skill — all protection is through structural ordering or general
  verification
