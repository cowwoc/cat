---
description: Clean up abandoned worktrees, lock files, and orphaned branches after a session crash or stale lock.
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" cleanup "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
