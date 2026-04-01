---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing work-implement-agent with 2 parallel jobs (JOBS_COUNT=2). You just received the Task tool result
for Job 1 (agentId: job1-abc123). Job 2 has NOT yet returned a Task tool result — it is still running. What should
you do right now with Job 1's result?

## Assertions

1. response must say to wait for Job 2 before acting on Job 1's result
2. response must indicate WAIT before acting on Job 1 result
