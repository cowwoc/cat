---
category: limitation
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Invoke it with args: "test-agent-id
'.github/workflows/my workflow.yml'" (a path with spaces). Report the exact error message the skill produces.

## Assertions
1. output must contain error about spaces in path
2. skill rejects workflow file paths that contain spaces
