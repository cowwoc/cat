---
description: >
  Run retrospective analysis on recorded learnings and derive action items from patterns.
  Trigger words: "run retrospective", "analyze patterns", "learning session", "retrospective on learnings", "patterns from".
  Analyzes patterns across multiple learning entries, not individual mistakes.
  MANDATORY after learn threshold is reached.
model: sonnet
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" retrospective-agent "$0"`
