---
description: Research implementation approaches, best practices, and technical information.
model: sonnet
argument-hint: "<research-type> <topic>"
allowed-tools:
  - Task
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" research "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
