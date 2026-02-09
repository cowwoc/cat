---
description: >
  Start working on, resume, or continue an existing issue or task.
  Use when user wants to TAKE ACTION on a task (not just view it).
  Trigger words: "work on", "resume", "continue working", "pick up", "keep working", "start working".
  NOT for viewing status - use /cat:status for that.
argument-hint: "[version | taskId | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" work "${CLAUDE_SESSION_ID}"`
