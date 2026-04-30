---
description: >
  Research how to implement something, look up information, investigate approaches, or find best practices.
  Trigger words: "research", "look up", "investigate", "best practices", "find out how", "how to implement".
  Use before planning an issue when technical investigation is needed.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
argument-hint: "<cat_agent_id> <research-type> <topic>"
allowed-tools:
  - Task
  - AskUserQuestion
user-invocable: false
effort: high
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" research-agent "$ARGUMENTS"`
