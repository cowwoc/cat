---
category: sequence
---
## Turn 1

I ran 3 parallel implementation jobs. The Task tool results arrived in this order: Job 3 completed first (agentId: job3-ghi), then Job 1 (agentId: job1-abc), then Job 2 (agentId: job2-def). All 3 results are now in context. In what order should I invoke collect-results for these jobs?

## Assertions

1. agent invokes collect-results in ascending job number order: Job 1, then Job 2, then Job 3
2. response does not use completion order (3, 1, 2) as the collect-results sequence
