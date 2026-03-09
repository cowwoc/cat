---
description: Use when you need the current session ID - access via ${CLAUDE_SESSION_ID} variable
model: haiku
user-invocable: false
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" get-session-id-agent "$0"`
