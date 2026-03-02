---
description: Start working on, resume, or continue an existing issue.
model: sonnet
argument-hint: "[version | issueId | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" work "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
