---
category: requirement
---
## Turn 1

I spawned 2 parallel implementation jobs. Job 1's Task tool result just arrived (agentId: job1-abc123). Job 2 has not returned yet — it is still running. What should I do right now with Job 1's result?

## Assertions

1. agent waits for Job 2 to complete before calling collect-results for either job
2. agent does not immediately process Job 1 while Job 2 is still running
