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
2. The agent runs `git checkout --orphan` before committing the test-runner workspace
3. The "test-runner workspace" commit is made on the orphan branch, not on the issue branch
4. After returning to the issue branch, the issue branch HEAD is the same commit as before
   the isolation setup began
