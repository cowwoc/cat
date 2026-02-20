---
description: Internal orchestration (invoked by /cat:work) - runs implement, confirm, review, and merge phases
user-invocable: false
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" work-with-issue "${CLAUDE_SESSION_ID}"`
