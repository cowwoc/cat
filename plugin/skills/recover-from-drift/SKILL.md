---
description: Use when actions keep failing or progress has stalled - detects goal drift by comparing actions against PLAN.md
model: haiku
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" recover-from-drift "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
