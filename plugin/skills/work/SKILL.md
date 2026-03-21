---
description: Start working on, resume, or continue an existing issue.
model: sonnet
argument-hint: "[version | issue_id | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
