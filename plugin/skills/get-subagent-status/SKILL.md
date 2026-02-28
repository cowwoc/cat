---
description: >
  Check status and progress of RUNNING SUBAGENTS specifically (not current session).
  Trigger words: "check subagents", "subagent status", "subagents using", "running subagents".
  Shows subagent token/context usage. For current session tokens, use /cat:token-report instead.
model: haiku
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" get-subagent-status "${CLAUDE_PROJECT_DIR}" "$0"`
