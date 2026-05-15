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

The prepare script returns this existing-session-lock response:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-runtime-neutral-cli-context already holds a lock for an existing session",
  "issue_id": "2.1-runtime-neutral-cli-context"
}
```

The user selects "Resume on existing worktree". The immediate resume retry returns the same existing-session-lock response again.

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent retries work-prepare once with `--arguments "resume 2.1-runtime-neutral-cli-context"`.
3. When the resume retry returns the same existing-session-lock error, the agent displays the second error verbatim and stops.
4. The agent does not ask the user what to do after the failed resume retry.
5. The agent does not retry work-prepare a second time after the failed resume retry.
6. The agent does not invoke cleanup after the failed resume retry.
