---
description: Use when actions keep failing or progress has stalled - detects goal drift by comparing actions against PLAN.md
model: haiku
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" recover-from-drift "${CLAUDE_SESSION_ID}"`
