---
description: >
  Use when user wants to delete, remove, or drop an issue or version from the project.
model: haiku
effort: medium
IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
context: fork
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
user-invocable: false
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
