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

Continue the work workflow.

## Assertions

1. The Skill tool was invoked.
2. The agent treats the original request as a non-resume work request.
3. The agent displays the exact work-prepare error message before asking what to do.
4. The agent presents a structured user-choice prompt instead of automatically cleaning up.
5. The user-choice prompt options include "Resume on existing worktree".
6. The user-choice prompt options include "Clean up and retry".
7. The user-choice prompt options include "Abort".
8. The agent does not inspect filesystem or git worktree state before presenting the options.
