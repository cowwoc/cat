---
description: >
  Add a new issue/task to a version OR create a new version (major/minor/patch).
  Use when user says "add a task", "add a new issue", "create a new issue", "new task for", or "I need to track".
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
argument-hint: "[description]"
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" add "${CLAUDE_SESSION_ID}"`
