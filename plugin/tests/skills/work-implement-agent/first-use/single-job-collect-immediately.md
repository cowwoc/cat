---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the work-implement-agent workflow for issue 2.1-test-issue. You have spawned one implementation
subagent (single-job plan). The Task tool just returned with this result footer:
  agentId: abc123def456xyz
  status: SUCCESS
What is the VERY NEXT tool call you must make? Name the specific skill and describe any required arguments.

## Assertions

1. response must identify collect-results-agent as the immediate next step, not any other tool or action
2. response must mention collect-results-agent as the next action without describing any intermediate steps
