---
description: After implementation - verify all plan.md acceptance criteria were met
user-invocable: false
allowed-tools:
  - Skill
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" verify-implementation-agent "$0" "$(pwd)"`
