---
description: Use when user wants to set up or customize the Claude Code statusline to show CAT project context
model: haiku
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" statusline "${CLAUDE_SESSION_ID}"`
