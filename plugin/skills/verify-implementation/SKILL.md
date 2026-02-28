---
description: After implementation - verify all PLAN.md acceptance criteria were met
model: sonnet
user-invocable: false
allowed-tools:
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" verify-implementation "${CLAUDE_PROJECT_DIR}" "$0" "$(pwd)"`
