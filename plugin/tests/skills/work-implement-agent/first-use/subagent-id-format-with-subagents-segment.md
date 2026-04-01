---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You need to invoke collect-results-agent after a subagent completes. The session ID is 'sess-3361441d'. The Task
tool result footer shows:
  agentId: 7a8b9c0d1e2f3g4h
What exact value do you use as the first argument (cat_agent_id) when calling collect-results-agent?

## Assertions

1. response must contain the correctly formatted subagent ID with /subagents/ segment
2. response must use the exact format sess-3361441d/subagents/7a8b9c0d1e2f3g4h without omitting any component
