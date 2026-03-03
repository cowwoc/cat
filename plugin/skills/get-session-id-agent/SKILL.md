---
description: Use when you need the current session ID - access via ${CLAUDE_SESSION_ID} variable
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" get-session-id-agent "$0"`
