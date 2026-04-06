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
2. The prompts sent to test-run subagents do not contain assertion text from the test case files
3. The prompts passed to test-run subagents do not expose absolute file system paths to the test
   infrastructure that would reveal the worktree or test directory location to the subagent
