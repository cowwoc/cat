---
description: >
  Research how to implement something, look up information, investigate approaches, or find best practices.
  Trigger words: "research", "look up", "investigate", "best practices", "find out how", "how to implement".
  Use before planning an issue when technical investigation is needed.
model: sonnet
effort: medium
IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
argument-hint: "<research-type> <topic>"
allowed-tools:
  - Task
  - AskUserQuestion
user-invocable: false
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
