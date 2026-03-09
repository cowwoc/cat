---
description: After implementation - verify all PLAN.md acceptance criteria were met
model: sonnet
user-invocable: false
allowed-tools:
  - Skill
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" verify-implementation-agent "$0" "$(pwd)"`
