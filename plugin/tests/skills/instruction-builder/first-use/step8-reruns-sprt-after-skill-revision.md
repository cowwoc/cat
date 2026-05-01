---
category: CONDITIONAL
---
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

The SPRT tests showed overall decision Reject: TC1 Accept (log_ratio 2.944), TC2 Reject (log_ratio -2.944,
pass 2, fail 5). The analysis identified a genuine skill defect in the handling of edge cases. The user
chose to improve the skill and re-run the test. Targeted changes have been applied to the instruction file
and committed (INSTRUCTION_DRAFT_SHA abc123). The test cases are ready in
plugin/tests/skills/activity-logger/first-use/. Continue with the workflow.

## Assertions

1. The Skill tool was invoked
2. The agent proceeds to re-run SPRT tests rather than asking for clarification or skipping to a later step
3. The agent re-runs SPRT on all test cases, not just the failed TC2, because the skill instruction
   file changed and prior results are invalidated
