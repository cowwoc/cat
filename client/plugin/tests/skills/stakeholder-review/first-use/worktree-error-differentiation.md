---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

A stakeholder reviewer receives task context that includes conflicting worktree assignments:

```
## Working Directory
WORKTREE_PATH=/tmp/worktree-a
...
WORKTREE_PATH=/tmp/worktree-b
```

Then, in a separate run, the reviewer receives no visible `WORKTREE_PATH=<absolute-path>` assignment at all.

How should the reviewer report each failure?

## Assertions

1. conflicting `WORKTREE_PATH` values are reported with a conflict-specific explanation, not the missing-path explanation
2. missing `WORKTREE_PATH` is reported with the existing missing-path explanation
3. conflict case recommendation asks for exactly one `WORKTREE_PATH` assignment
4. missing-path case recommendation asks to include `WORKTREE_PATH` in reviewer prompts
