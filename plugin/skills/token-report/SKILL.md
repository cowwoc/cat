---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
model: haiku
context: fork
allowed-tools:
  - Skill
user-invocable: false
---

Without any preamble, invoke the echo skill with this exact content:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-token-report.sh --session-id "${CLAUDE_SESSION_ID}"`

Then after the echo completes, output:

**FAIL-FAST:** If you do NOT see a report above, preprocessing FAILED. STOP.
