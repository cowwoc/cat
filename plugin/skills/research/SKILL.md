---
description: Research implementation approaches, best practices, and technical information.
model: sonnet
argument-hint: "<research-type> <topic>"
allowed-tools:
  - Task
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" research "${CLAUDE_SESSION_ID}" $ARGUMENTS`
