---
description: Research implementation approaches, best practices, and technical information.
model: sonnet
argument-hint: "<research-type> <topic>"
allowed-tools:
  - Task
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" research "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
