---
description: Use when user asks about progress, status, what's done, or what's next - show project issues and completion status
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" status-agent "$0"`