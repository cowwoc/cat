---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. For this test, assume the working directory is NOT
a git repository (as if running outside any git repo). Invoke the skill with args: "test-agent-id
.github/workflows/build.yml" and follow the validation steps. When you reach the git rev-parse check, it will
fail. Report what error message the skill produces.

## Assertions

1. output must contain error about not being in a git repository
2. skill validates git repository context before executing steps
