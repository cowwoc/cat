---
description: Set up or customize the Claude Code statusline to show CAT project context.
model: haiku
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" statusline "${CLAUDE_SESSION_ID}"`
