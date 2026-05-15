---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Run a stakeholder review on this branch with requirements and legal reviewers.

## Assertions

1. every reviewer prompt contains a `## Working Directory` section
2. every reviewer prompt specifies the worktree exactly once as `WORKTREE_PATH=<absolute-path>`
3. reviewer prompts do not include `WORKTREE_PATH: <absolute-path>` or duplicate `WORKTREE_PATH` lines
4. reviewer prompts state that `WORKTREE_PATH=<absolute-path>` is the canonical form
