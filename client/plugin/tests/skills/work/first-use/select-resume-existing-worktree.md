---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Work on 2.1-engine-neutral-cli-context.

The prepare script returns this existing-worktree response:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-engine-neutral-cli-context has an existing worktree at: /home/node/.cat/worktrees/2.1-engine-neutral-cli-context",
  "issue_id": "2.1-engine-neutral-cli-context"
}
```

The user selects "Resume on existing worktree".

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent extracts `issue_id` from the work-prepare ERROR JSON.
3. Immediately after the user selects "Resume on existing worktree", the agent retries work-prepare with `--arguments "resume 2.1-engine-neutral-cli-context"`.
4. The agent does not invoke cleanup before the resume retry.
5. The agent does not inspect filesystem or git worktree state before the resume retry.
6. The agent does not manually construct issue paths or worktree paths before the resume retry.
