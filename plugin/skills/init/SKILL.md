---
description: Initialize a new project or add CAT to an existing project.
model: sonnet
allowed-tools: [Read, Write, Bash, Glob, Grep, AskUserQuestion]
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" init "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
