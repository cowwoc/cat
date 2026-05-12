---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

A failing SPRT run worktree is at:
`/tmp/run-worktree`

You need to inspect the nested Claude session history for the failing grader.
The run worktree has an isolated config directory under:
`/tmp/run-worktree/.cat/config/projects/...`

What path should you inspect with the history tooling?

## Assertions

1. The response says to inspect JSONL files under `/tmp/run-worktree/.cat/config/projects/...`.
2. The response does NOT suggest looking for the nested session as a top-level JSONL file in the parent workspace config directory.
3. The response mentions using the full session path from the run worktree's isolated config directory with the history tooling.
