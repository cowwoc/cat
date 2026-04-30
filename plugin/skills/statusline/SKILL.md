---
description: Set up or customize the Claude Code statusline to show CAT project context.
model: haiku
effort: low
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" statusline "${CLAUDE_SESSION_ID}"`
