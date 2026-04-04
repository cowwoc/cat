---
category: CONDITIONAL
---
## Turn 1

I need to continue the cat:instruction-builder-agent workflow. Step 8 (instruction-analyzer-agent) has
completed its analysis. The SPRT results showed overall_decision Reject: TC1 Accept (log_ratio 2.944),
TC2 Reject (log_ratio -2.944, pass 2, fail 5). The analysis report identified a genuine skill defect in
the handling of edge cases. The user has chosen option 2: "improve the skill and re-run the test." You
have already applied targeted changes to the instruction file
(plugin/skills/instruction-builder-agent/first-use.md) and committed the updated version with new
INSTRUCTION_DRAFT_SHA abc123. The test cases are in
plugin/tests/skills/instruction-builder-agent/first-use/. Continue with the workflow.

## Assertions

1. The Skill tool was invoked
2. The agent proceeds to re-run SPRT tests rather than asking for clarification or skipping to a later step
3. The agent re-runs SPRT on all test cases, not just the failed TC2, because the skill instruction
   file changed and prior results are invalidated
