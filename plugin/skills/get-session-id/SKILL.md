---
description: Use when you need the current session ID - access via ${CLAUDE_SESSION_ID} variable
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" get-session-id "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
