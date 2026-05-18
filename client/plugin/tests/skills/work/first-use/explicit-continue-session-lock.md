---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Continue engine-neutral-cli-context.

The prepare script returns this existing-session-lock response:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-engine-neutral-cli-context already holds a lock for an existing session",
  "issue_id": "2.1-engine-neutral-cli-context"
}
```

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent treats the original request as explicit resume or continue intent.
3. The agent does not ask the user to confirm whether to resume.
4. The agent extracts `issue_id` from the work-prepare ERROR JSON.
5. The agent immediately retries work-prepare with `--arguments "resume 2.1-engine-neutral-cli-context"`.
6. The agent does not invoke cleanup before the resume retry.
7. The agent does not inspect filesystem or git worktree state before the resume retry.
