---
description: File a bug report for a CAT plugin issue on GitHub (checks for duplicates before creating).
model: haiku
argument-hint: "[description]"
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" feedback "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
