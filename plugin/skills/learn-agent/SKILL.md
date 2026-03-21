---
description: >
  Record mistakes, document failures, and perform root cause analysis.
  Trigger words: "record this mistake", "document what went wrong", "learn from this", "document the failure".
  MANDATORY after ANY mistake to implement prevention.
  Integrates token tracking for context-related failures.
model: sonnet
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" learn-agent "$0"`
