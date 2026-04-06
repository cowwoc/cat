---
category: sequence
---
## Turn 1

I spawned one implementation subagent for issue 2.1-test-issue. The Task tool just returned:
  agentId: abc123def456xyz
  status: SUCCESS
What is the very next tool call I must make?

## Assertions

1. agent immediately calls collect-results-agent as the next step after a single-job completion
2. response names collect-results-agent as the specific next action with the agentId argument
