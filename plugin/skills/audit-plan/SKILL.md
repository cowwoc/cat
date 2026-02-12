---
description: Verify PLAN.md acceptance criteria were implemented after /cat:work execution
model: sonnet
allowed-tools:
  - Task
  - Read
  - Glob
  - Grep
  - Bash
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" audit-plan "${CLAUDE_SESSION_ID}" "$(pwd)"`
