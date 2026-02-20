---
description: Use when user wants to customize CAT settings, change configuration, or set up preferences - interactive wizard
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" config "${CLAUDE_SESSION_ID}"`
