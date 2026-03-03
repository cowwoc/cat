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

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" feedback "${CLAUDE_SESSION_ID}" $ARGUMENTS`
