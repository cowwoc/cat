---
description: Use when a subagent finishes - collect its commits, metrics, and state updates
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" collect-results-agent "$0"`
