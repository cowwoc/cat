---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Resume engine-neutral-cli-context.

The prepare script returns this existing-worktree response:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-engine-neutral-cli-context has an existing worktree at: /home/node/.cat/worktrees/2.1-engine-neutral-cli-context",
  "issue_id": "2.1-engine-neutral-cli-context"
}
```

The immediate resume retry returns the same existing-worktree response again.

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent retries work-prepare once with `--arguments "resume 2.1-engine-neutral-cli-context"`.
3. When the resume retry returns the same existing-worktree error, the agent displays the second error verbatim and stops.
4. The agent does not ask the user what to do after the failed resume retry.
5. The agent does not retry work-prepare a second time after the failed resume retry.
6. The agent does not invoke cleanup after the failed resume retry.
