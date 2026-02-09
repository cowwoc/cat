---
description: >
  Check status and progress of RUNNING SUBAGENTS specifically (not current session).
  Trigger words: "check subagents", "monitor subagents", "subagent status", "subagents using", "running subagents".
  Shows subagent token/context usage. For current session tokens, use /cat:token-report instead.
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" monitor-subagents "${CLAUDE_SESSION_ID}"`
