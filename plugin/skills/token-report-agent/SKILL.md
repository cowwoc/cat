---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
model: haiku
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" token-report-agent "$0"`
