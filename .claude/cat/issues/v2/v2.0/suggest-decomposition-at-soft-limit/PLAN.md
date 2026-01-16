# Plan: suggest-decomposition-at-soft-limit

## Goal
When /cat:work estimates a task will exceed the soft token threshold (40%), suggest decomposing the task into sub-tasks before execution instead of just noting it as a warning.

## Satisfies
None - UX improvement task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Need to balance helpfulness vs interruption
- **Mitigation:** Use AskUserQuestion to let user decide, don't force decomposition

## Files to Modify
- plugin/commands/work.md - Update analyze_task_size step to suggest decomposition

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions

## Execution Steps
1. **Step 1:** Update analyze_task_size step in work.md
   - Files: plugin/commands/work.md
   - Change: When estimate > soft_threshold && estimate < hard_limit, use AskUserQuestion to suggest decomposition
   - Options: "Decompose now (Recommended)", "Proceed anyway", "Abort"
   - Verify: Read updated work.md and confirm logic is correct

2. **Step 2:** Run tests
   - Verify: `python3 /workspace/run_tests.py` passes
