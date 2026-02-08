---
description: Execute work phases with issue-aware progress banner rendering (internal skill, invoked by /cat:work)
user-invocable: false
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" work-with-issue "${CLAUDE_SESSION_ID}"`
