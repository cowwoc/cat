---
description: Initialize a new project or add CAT to an existing project.
model: sonnet
allowed-tools: [Read, Write, Bash, Glob, Grep, AskUserQuestion]
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" init "${CLAUDE_SESSION_ID}"`
