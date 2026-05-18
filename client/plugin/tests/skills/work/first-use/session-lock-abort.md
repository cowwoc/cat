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

The prepare script returns this existing-session-lock response:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-engine-neutral-cli-context already holds a lock for an existing session",
  "issue_id": "2.1-engine-neutral-cli-context"
}
```

The user selects "Abort".

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent stops after the user selects "Abort".
3. The agent does not invoke cleanup.
4. The agent does not retry work-prepare.
5. The agent does not inspect filesystem or git worktree state.
