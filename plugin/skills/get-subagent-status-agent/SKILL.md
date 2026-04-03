---
description: >
  Check status and progress of RUNNING SUBAGENTS specifically (not current session).
  Trigger words: "check subagents", "subagent status", "subagents using", "running subagents".
  Shows subagent token/context usage. For current session tokens, use /cat:token-report instead.
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" get-subagent-status-agent "$0"`
