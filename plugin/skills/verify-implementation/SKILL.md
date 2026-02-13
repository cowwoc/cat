---
description: Verify PLAN.md acceptance criteria were implemented after /cat:work execution
user-invocable: false
model: sonnet
allowed-tools:
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" verify-implementation "${CLAUDE_SESSION_ID}" "$(pwd)"`
