---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. For this test, assume gh CLI is not installed
(pretend 'gh --version' would fail with 'command not found'). Invoke the skill with args: "test-agent-id
.github/workflows/build.yml" and follow the skill's validation steps. When you reach the gh --version check,
treat it as failing. Report what error message the skill produces.

## Assertions

1. output must contain the gh CLI required error
2. skill validates gh CLI availability before executing workflow trigger steps
