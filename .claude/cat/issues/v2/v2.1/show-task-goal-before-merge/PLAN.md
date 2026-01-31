# Plan: show-task-goal-before-merge

## Goal
Before asking the user for merge approval, display the task's goal/description from PLAN.md so users 
remember what the task is about before making an approval decision.

## Satisfies
None - UX improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - additive change to approval gate
- **Mitigation:** Existing tests verify approval flow works

## Files to Modify
- `plugin/skills/work/SKILL.md` or `plugin/skills/work-with-task/SKILL.md` - Add goal display before approval question

## Acceptance Criteria
- [ ] Goal/description from PLAN.md shown before merge approval question
- [ ] Tests written and passing
- [ ] No regressions to existing functionality

## Execution Steps
1. **Step 1:** Identify where merge approval gate is triggered
   - Find the AskUserQuestion call for merge approval
   - Determine how to access task's PLAN.md goal

2. **Step 2:** Add goal display before approval
   - Read goal from PLAN.md
   - Display it in a clear format before the approval question

3. **Step 3:** Run tests
   - Verify: python3 /workspace/run_tests.py
