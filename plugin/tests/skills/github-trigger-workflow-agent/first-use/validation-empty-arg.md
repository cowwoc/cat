---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Invoke it with only a cat_agent_id argument and no
workflow_file (i.e., pass args: "test-agent-id" with nothing after). Report the exact error message the skill
produces.

## Assertions

1. output must contain the required error about missing workflow_file argument
2. skill produces a clear error when required workflow_file argument is omitted
