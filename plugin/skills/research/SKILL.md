---
description: >
  Research how to implement something, look up information, investigate approaches, or find best practices.
  Trigger words: "research", "look up", "investigate", "best practices", "find out how", "how to implement".
  Use before planning an issue when technical investigation is needed.
argument-hint: "<research-type> <topic>"
allowed-tools:
  - Task
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" research "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
