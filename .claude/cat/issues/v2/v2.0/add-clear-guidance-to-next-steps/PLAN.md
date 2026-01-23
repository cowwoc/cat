# Plan: add-clear-guidance-to-next-steps

## Problem
Workflows that provide "Next Steps" guidance (e.g., /cat:status, /cat:add, /cat:work completion)
recommend running `/cat:work <taskId>` but don't mention that users should `/clear` first.
This leads to context accumulation issues where the accumulated conversation history degrades
task execution quality.

## Satisfies
None - infrastructure bugfix

## Reproduction
1. Run `/cat:status` or complete a task with `/cat:work`
2. Follow the "Next Steps" guidance to run another `/cat:work` command
3. Context from previous operations accumulates, degrading quality

## Expected vs Actual
- **Expected:** Next Steps guidance should recommend `/clear` before `/cat:work`
- **Actual:** Next Steps only shows `/cat:work {version}-<task-name>` without clearing

## Root Cause
The workflow templates and handlers don't include `/clear` guidance in their Next Steps output.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - purely additive documentation change
- **Mitigation:** Visual inspection of updated output

## Files to Modify
- plugin/hooks/skill_handlers/status_handler.py - Update NEXT STEPS table
- plugin/commands/work.md - Update next_task step completion output

## Test Cases
- [ ] /cat:status shows `/clear` before `/cat:work` in Next Steps
- [ ] Task completion in /cat:work shows `/clear` guidance

## Execution Steps
1. **Step 1:** Update status_handler.py
   - Files: plugin/hooks/skill_handlers/status_handler.py
   - Find the NEXT STEPS table output and add `/clear` guidance
   - Verify: Run /cat:status and check Next Steps includes clear recommendation

2. **Step 2:** Update work.md next_task step
   - Files: plugin/commands/work.md
   - Update completion boxes to include `/clear` guidance before work command
   - Verify: Complete a task and check Next Steps includes clear recommendation
