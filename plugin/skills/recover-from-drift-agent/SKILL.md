---
description: Use when actions keep failing or progress has stalled - detects goal drift by comparing actions against plan.md
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
user-invocable: false
argument-hint: "<cat_agent_id>"
effort: medium
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" recover-from-drift-agent "$0"`
