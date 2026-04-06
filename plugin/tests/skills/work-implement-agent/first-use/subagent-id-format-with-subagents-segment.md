---
category: requirement
---
## Turn 1

I need to call collect-results-agent after a subagent completes. My session ID is 'sess-3361441d'. The Task tool result footer shows:
  agentId: 7a8b9c0d1e2f3g4h
What exact value do I use as the first argument when calling collect-results-agent?

## Assertions

1. agent combines session ID and agentId using the subagents path format
2. the constructed argument includes both the session ID and the agentId from the Task result
