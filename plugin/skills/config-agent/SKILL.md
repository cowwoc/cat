---
description: >
  Use when user wants to customize CAT settings, change configuration, or set up preferences - interactive wizard.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: haiku
effort: low
context: fork
allowed-tools:
  - Bash
  - Read
  - Write
  - Edit
  - AskUserQuestion
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" config-agent "$0"`
