---
category: REQUIREMENT
---
## Turn 1

I need to continue the cat:instruction-builder-agent workflow. An existing skill called instruction-builder-agent
has just been revised. The previous SPRT run succeeded (all test cases accepted, stored in test-results.json).
The skill instruction file has been updated. I now have the same test cases ready to re-run and want to
proceed to Step 6 (SPRT Test Execution) to verify the revised skill.

## Assertions

1. The Skill tool was invoked
2. The agent uses detect-changes to identify which test cases need re-running rather than running full SPRT on
   all test cases unconditionally
3. The agent does not re-run SPRT on test cases that carry forward unchanged from prior results
