---
description: Use when user wants to delete, remove, or drop an issue or version from the project
model: haiku
context: fork
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" remove "${CLAUDE_SESSION_ID}"`
