---
paths: ["*"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Batch Write

When 2 or more file writes/edits are independent, issue all corresponding Write/Edit tool calls in a single response.

Two writes are independent if neither file's content depends on the other write result. Multiple operations on the same
file are not independent.

Requirements:
- Determine the full set of independent write targets before issuing write calls.
- Read each existing file before editing it.
- Batch all independent Write/Edit tool calls in one response.
- Retry only failed writes; do not re-issue successful ones.

This is guidance only for write batching. It does not bypass project rules, hooks, or worktree restrictions.
