---
description: Interactive wizard to customize your CAT settings
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" config "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
