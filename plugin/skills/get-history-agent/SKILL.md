---
description: Use when you need to examine past conversation, session logs, or raw chat history
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" get-history-agent "$0"`
