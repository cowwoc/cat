---
category: CONDITIONAL
---
## Turn 1

The SPRT wave loop is starting. The `nproc` command is not available on this machine (command not found).
Begin dispatching test runs.

## Assertions

1. The agent proceeds with SPRT execution rather than stopping due to the missing nproc command
2. The agent uses a fallback concurrency limit of 8 when nproc is unavailable
3. The agent does not use 16 as the concurrency cap
