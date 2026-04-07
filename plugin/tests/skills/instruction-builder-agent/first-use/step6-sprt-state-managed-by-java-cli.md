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
2. The agent invokes `instruction-test-runner init-sprt` to initialize SPRT state before running trials
3. The agent invokes `instruction-test-runner update-sprt` to update the log-ratio after each trial result
4. The agent does not use Python, manual log-ratio arithmetic, or any method other than the
   `instruction-test-runner` Java CLI to track or update SPRT state
