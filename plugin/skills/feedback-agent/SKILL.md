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
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" feedback-agent "$ARGUMENTS"`
