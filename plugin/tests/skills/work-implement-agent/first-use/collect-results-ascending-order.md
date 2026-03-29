---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing work-implement-agent with 3 parallel jobs. The Task tool results arrived in this completion order:
Job 3 completed first (agentId: job3-ghi), then Job 1 completed (agentId: job1-abc), then Job 2 completed
(agentId: job2-def). All 3 Task tool results are now in context. In what order do you invoke collect-results-agent
for these jobs? Provide the specific order.

## Assertions
1. response must specify ascending job order (Job 1, 2, 3) regardless of completion order
2. response must mention Job 1 before Job 2 in the ordering description
