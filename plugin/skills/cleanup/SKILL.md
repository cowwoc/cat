---
description: Clean up abandoned worktrees, lock files, and orphaned branches after a session crash or stale lock.
model: haiku
effort: low
context: fork
allowed-tools:
  - Bash
  - Read
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" cleanup "${CLAUDE_SESSION_ID}"`
