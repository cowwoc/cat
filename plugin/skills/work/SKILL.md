---
description: Start working on, resume, or continue an existing issue.
model: sonnet
argument-hint: "[version | issueId | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" work "${CLAUDE_SESSION_ID}" $ARGUMENTS`
