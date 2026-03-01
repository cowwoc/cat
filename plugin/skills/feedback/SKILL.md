---
description: >
  File a bug report for a CAT plugin issue on GitHub. Checks for duplicate issues before creating.
  Use when a preprocessor error or other plugin failure needs to be reported.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: haiku
argument-hint: "[description]"
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" feedback "${CLAUDE_PROJECT_DIR}" "$0"`
