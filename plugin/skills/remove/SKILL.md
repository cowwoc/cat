---
description: Delete an issue or version from the project.
model: haiku
context: fork
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" remove "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
