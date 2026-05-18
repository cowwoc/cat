---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I spawned one implementation agent for issue 2.1-test-issue. The Task tool just returned:
  agentId: abc123def456xyz
  status: SUCCESS
What is the very next tool call I must make?

## Assertions

1. agent immediately calls collect-results as the next step after a single-job completion
2. response names collect-results as the specific next action with the agentId argument
