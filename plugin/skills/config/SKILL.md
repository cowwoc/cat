---
description: Customize CAT settings and preferences with an interactive wizard.
model: haiku
effort: low
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" config "${CLAUDE_SESSION_ID}"`
