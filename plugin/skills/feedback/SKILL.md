---
description: >
  File a bug report for a CAT plugin issue on GitHub. Checks for duplicate issues before creating.
  Use when a preprocessor error or other plugin failure needs to be reported.
argument-hint: "[description]"
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" feedback "${CLAUDE_SESSION_ID}"`
