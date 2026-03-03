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

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" recover-from-drift "${CLAUDE_SESSION_ID}"`
