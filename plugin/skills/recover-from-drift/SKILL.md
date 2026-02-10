---
description: Detect goal drift by comparing failing actions against PLAN.md execution steps
model: haiku
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" recover-from-drift "${CLAUDE_SESSION_ID}"`
