---
description: >
  Use when user wants to set up or customize the Claude Code statusline to show CAT project context.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: haiku
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - AskUserQuestion
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" statusline "${CLAUDE_PROJECT_DIR}" "$0"`
