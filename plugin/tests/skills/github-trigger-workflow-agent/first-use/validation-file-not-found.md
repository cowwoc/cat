---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Invoke it with args: "test-agent-id
.github/workflows/does-not-exist-xyz-abc.yml". Report the exact error message the skill produces.

## Assertions
1. output must contain 'Workflow file not found'
2. skill validates that the workflow file exists before proceeding
