# Plan: implicit-diff-review

## Goal
Update the approval_gate workflow to always show a diff before asking the user to approve/merge.
Remove the "Review changes first" option since diff display is now implicit.

## Satisfies
- None (workflow improvement based on user feedback)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - improves transparency
- **Mitigation:** N/A

## Files to Modify
- plugin/commands/work.md - Update approval_gate step to show diff first, remove review option

## Acceptance Criteria
- [ ] Diff is always displayed before approval gate AskUserQuestion
- [ ] "Review changes first" option removed from approval choices
- [ ] Remaining options: Approve and merge, Request changes, Abort

## Execution Steps
1. **Step 1:** Update approval_gate step in work.md
   - Show `git diff {base}..HEAD` before AskUserQuestion
   - Remove "Review changes first" option
   - Keep: Approve, Request changes, Abort
