---
category: reliability
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Simulate a scenario where the skill successfully
reaches Step 4 (displays gh run list output), but then the session is interrupted before Step 5 cleanup can run.
The temporary push trigger remains in the workflow file and the temporary commit remains in git. Read the
'Interrupt Recovery' section at the end of the skill instructions. Describe the exact steps an agent should take
to clean up this orphaned state.

## Assertions

1. interrupt recovery must instruct detecting orphaned push trigger with grep, manual cleanup of trigger from
workflow file, and committing and pushing the cleanup
2. recovery steps include git add, git commit, and git push to finalize cleanup of orphaned state
