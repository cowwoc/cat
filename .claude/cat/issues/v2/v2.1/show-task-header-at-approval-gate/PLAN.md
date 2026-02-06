# Plan: show-task-header-at-approval-gate

## Problem
When /cat:work reaches the approval gate to display the diff and ask for user approval,
the progress header banner is not shown. Users lose context about which task they are
reviewing, especially after long execution phases.

## Satisfies
None - infrastructure bugfix

## Reproduction
1. Run `/cat:work` on any task
2. Let execution complete and reach approval gate
3. Observe that no task header banner is displayed before the diff

## Expected vs Actual
- **Expected:** Progress header banner (e.g., `🐱 > 2.0-task-name` with `◉ Merging` state) displayed before diff and
  approval gate
- **Actual:** Diff and approval gate shown without task context header

## Root Cause
The approval_gate step in work.md does not include instructions to display the progress header
banner before presenting the diff.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - purely additive display change
- **Mitigation:** Visual inspection of updated output

## Files to Modify
- plugin/commands/work.md - Update approval_gate step to show header banner

## Test Cases
- [ ] Progress header banner with `◉ Merging` state shown before diff at approval gate
- [ ] Task ID clearly visible so user knows which task they are approving

## Execution Steps
1. **Step 1:** Update work.md approval_gate step
   - Files: plugin/commands/work.md
   - Add instruction to display progress header banner before diff
   - Banner should show task ID and Merging phase active
   - Verify: Review work.md for updated approval_gate section
