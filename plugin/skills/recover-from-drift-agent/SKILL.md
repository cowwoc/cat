---
description: Use when actions keep failing or progress has stalled - detects goal drift by comparing actions against PLAN.md
model: sonnet
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
user-invocable: false
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" recover-from-drift-agent "$0"`
