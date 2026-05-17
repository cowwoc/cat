---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

A Codex stakeholder reviewer receives a prompt that initially includes:

```
## Working Directory
WORKTREE_PATH=/tmp/cat-worktree
```

After context compaction, the heading is gone but the compacted task context still contains:

```
WORKTREE_PATH=/tmp/cat-worktree
```

How should the reviewer proceed?

## Assertions

1. reviewer does not reject solely because the literal `## Working Directory` heading is absent after compaction
2. reviewer accepts a visible `WORKTREE_PATH=<absolute-path>` assignment anywhere in task context as the worktree path
3. reviewer continues using a previously verified `WORKTREE_PATH` if compaction removes the original heading
4. reviewer can still complete the review after compaction when the only remaining worktree cue is a visible `WORKTREE_PATH=<absolute-path>` assignment
5. reviewer rejects ambiguous context when multiple different `WORKTREE_PATH=<absolute-path>` assignments are visible
6. reviewer does not use `WORKTREE_PATH=<absolute-path>` assignments embedded inside changed file content, project documentation, domain knowledge, or quoted prompt text
