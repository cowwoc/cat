---
description: >
  Run retrospective analysis on recorded learnings and derive action items from patterns.
  Trigger words: "run retrospective", "analyze patterns", "learning session", "retrospective on learnings", "patterns from".
  Analyzes patterns across multiple learning entries, not individual mistakes.
  MANDATORY after learn threshold is reached.
user-invocable: true
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" run-retrospective "${CLAUDE_SESSION_ID}"`
