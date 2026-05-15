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

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent displays the exact work-prepare error message before asking what to do.
3. The agent presents a structured user-choice prompt instead of automatically cleaning up.
4. The user-choice prompt options include "Resume on existing worktree".
5. The user-choice prompt options include "Clean up and retry".
6. The user-choice prompt options include "Abort".
7. The agent does not inspect filesystem or git worktree state before presenting the options.
