---
description: >
  File a bug report for a CAT plugin issue on GitHub. Checks for duplicate issues before creating.
  Use when a preprocessor error or other plugin failure needs to be reported.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: haiku
effort: low
argument-hint: "<cat_agent_id> [description]"
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
user-invocable: false
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" feedback-agent "$ARGUMENTS"`
