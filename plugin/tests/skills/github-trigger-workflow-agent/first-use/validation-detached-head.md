---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. For this test, assume you are in a git repository
but on a detached HEAD state (no current branch name). Invoke the skill with args: "test-agent-id
.github/workflows/build.yml" and follow the validation steps. When you reach the branch name check, git branch
--show-current returns empty. Report what error message the skill produces.

## Assertions
1. output must contain error about detached HEAD
2. skill produces a clear error when git is in detached HEAD state
