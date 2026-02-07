---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
model: haiku
allowed-tools:
  - Skill
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" token-report`
