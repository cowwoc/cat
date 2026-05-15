---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Work on 2.1-runtime-neutral-cli-context.

The prepare script returns this existing-worktree response:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-runtime-neutral-cli-context has an existing worktree at: /home/node/.cat/worktrees/2.1-runtime-neutral-cli-context",
  "issue_id": "2.1-runtime-neutral-cli-context"
}
```

The user selects "Clean up and retry". Cleanup completes, then the immediate retry returns the same existing-worktree error again.

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent invokes cleanup after the user selects "Clean up and retry".
3. Immediately after cleanup returns, the agent retries work-prepare with the original arguments, not with a resume prefix.
4. The agent does not invoke any other skill or investigation workflow between cleanup returning and the retry.
5. When the retry returns the same existing-worktree error, the agent displays the error verbatim and stops.
6. The agent does not loop back to the user-choice prompt after the second existing-worktree error.
