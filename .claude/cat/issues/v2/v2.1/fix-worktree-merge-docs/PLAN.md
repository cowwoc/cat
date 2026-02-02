# Plan: fix-worktree-merge-docs

## Problem
When merging a task branch back to the base branch, the agent attempts `git checkout <base-branch>` which is blocked by M205 hook when working from a worktree. The merge-and-cleanup.md concept document and work-merge skill don't document the correct pattern for this scenario.

## Satisfies
None - infrastructure/reliability improvement

## Root Cause
The merge-and-cleanup.md assumes `git checkout main` is possible, but M205 blocks all checkouts in the main worktree to prevent accidental branch switches.

## Expected vs Actual
- **Expected:** Agent knows to run `git merge` from main workspace without checkout
- **Actual:** Agent tries checkout, gets blocked, wastes 3+ turns finding workaround

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - documentation only
- **Mitigation:** Review updated docs

## Files to Modify
- plugin/concepts/merge-and-cleanup.md - Add "Merging When Base Is Checked Out" section
- plugin/skills/work-merge/SKILL.md - Reference the pattern (if not already)

## Acceptance Criteria
- [ ] Bug no longer reproducible (agent follows correct pattern)
- [ ] Documentation clearly explains the scenario
- [ ] No regressions

## Execution Steps
1. **Step 1:** Add section to merge-and-cleanup.md
   - Title: "Merging When Base Branch Is Checked Out in Main Workspace"
   - Explain: Don't checkout, just merge directly
   - Pattern: `git stash && git merge --ff-only <task-branch> && git stash drop`
   - Verify: Read updated file

2. **Step 2:** Check if work-merge skill references merge-and-cleanup.md
   - If not, add reference
   - Verify: grep for @merge-and-cleanup or similar
