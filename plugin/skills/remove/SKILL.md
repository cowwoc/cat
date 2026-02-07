---
description: Remove issue or version
model: haiku
context: fork
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" remove "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
