---
category: REQUIREMENT
---
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

The design is done and test cases are ready in plugin/tests/skills/activity-logger/first-use/.
There is no prior test history for this skill. Run the empirical tests.

## Assertions

1. The Skill tool was invoked
2. The agent runs full SPRT on all test cases without invoking detect-changes or carrying forward prior results
3. The agent does not skip or filter any test cases as not requiring testing
