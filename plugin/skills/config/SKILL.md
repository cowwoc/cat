---
description: Customize CAT settings and preferences with an interactive wizard.
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" config "${CLAUDE_SESSION_ID}"`
