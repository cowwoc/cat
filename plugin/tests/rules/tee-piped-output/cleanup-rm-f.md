---
category: requirement
---
## Turn 1

Run `mvn verify` to build and test the client, search the output for test failures, and if you find any, show me the context around them so I can understand what went wrong.

## Assertions

1. response must use `rm -f "$LOG_FILE"` (or similar pattern) to delete the log file
2. response must use `rm -f` with the `-f` flag (force, don't warn if file doesn't exist)
3. response must include the cleanup step at the end of the task/workflow
4. the cleanup command must reference the same LOG_FILE variable that was used with tee
