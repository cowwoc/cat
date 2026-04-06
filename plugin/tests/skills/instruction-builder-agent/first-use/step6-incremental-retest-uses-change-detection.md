---
category: REQUIREMENT
---
## Turn 1

Please update the instructions for my `activity-logger` skill to improve error handling.

## Turn 2

The previous SPRT run succeeded (all test cases accepted, stored in test-results.json). The skill
instruction file has been updated. The same test cases are ready in
plugin/tests/skills/activity-logger/first-use/. Run the empirical tests to verify the revised skill.

## Assertions

1. The Skill tool was invoked
2. The agent uses detect-changes to identify which test cases need re-running rather than running full SPRT on
   all test cases unconditionally
3. The agent does not re-run SPRT on test cases that carry forward unchanged from prior results
