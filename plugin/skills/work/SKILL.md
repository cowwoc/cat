---
description: Work on or resume issues - use when user says "work on", "resume", "continue", or "pick up" a task
argument-hint: "[version | taskId | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" work "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
