---
description: >
  Use when user wants to delete, remove, or drop an issue or version from the project.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: haiku
context: fork
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" remove-agent "$0"`
