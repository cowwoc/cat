---
description: Use when actions keep failing or progress has stalled - detects goal drift by comparing actions against PLAN.md
model: haiku
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" recover-from-drift "${CLAUDE_PROJECT_DIR}" "$0"`
