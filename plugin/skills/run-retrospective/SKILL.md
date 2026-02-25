---
description: >
  Run retrospective analysis on recorded learnings and derive action items from patterns.
  Trigger words: "run retrospective", "analyze patterns", "learning session", "retrospective on learnings", "patterns from".
  Analyzes patterns across multiple learning entries, not individual mistakes.
  MANDATORY after learn threshold is reached.
model: sonnet
user-invocable: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" run-retrospective "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
