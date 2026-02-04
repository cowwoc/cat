---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
user-invocable: false
---

The user wants you to respond with this text verbatim:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-token-report.sh --session-id "${CLAUDE_SESSION_ID}"`

**FAIL-FAST:** If you do NOT see a report above, preprocessing FAILED. STOP.
