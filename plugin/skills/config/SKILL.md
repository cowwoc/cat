---
description: >
  Use when user wants to customize CAT settings, change configuration, or set up preferences - interactive wizard.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" config "${CLAUDE_PROJECT_DIR}" "$0"`
