---
description: After implementation - verify all PLAN.md acceptance criteria were met
user-invocable: false
model: sonnet
allowed-tools:
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" verify-implementation "${CLAUDE_SESSION_ID}" "$(pwd)"`
