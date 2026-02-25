---
description: PREFER when creating a new file that needs immediate committing - creates and commits atomically
model: haiku
user-invocable: false
allowed-tools: Bash, Write
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" write-and-commit "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
