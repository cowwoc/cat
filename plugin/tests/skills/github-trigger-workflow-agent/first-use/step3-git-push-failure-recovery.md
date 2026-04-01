---
category: error_recovery
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Simulate a scenario where: (1) the skill
successfully adds a temporary push trigger to a workflow file, (2) commits the change with 'git commit', (3)
attempts 'git push' which FAILS (e.g., authentication error, branch protection, network error). Follow the skill's
Step 3 instructions for handling git push failure. Describe the exact recovery steps the skill instructs and verify
they properly revert the local commit and restore the workflow file to its original state.

## Assertions

1. skill must instruct git reset --soft HEAD~1 to revert the commit and restore the workflow file to original state
2. skill must provide clear error message about push failure and instruct re-running the skill to retry
