---
description: Detect and recover from goal drift when actions repeatedly fail or progress stalls.
model: haiku
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" recover-from-drift "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
