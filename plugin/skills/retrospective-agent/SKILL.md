---
description: >
  Review whether preventions implemented by /cat:learn were effective or need revision.
  Analyzes patterns across multiple recorded learnings to identify systemic improvements.
  Trigger words: "run retrospective", "analyze patterns", "learning session", "retrospective on learnings", "patterns from".
  MANDATORY after learn threshold is reached.
user-invocable: false
argument-hint: "<cat_agent_id>"
effort: high
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" retrospective-agent "$0"`
