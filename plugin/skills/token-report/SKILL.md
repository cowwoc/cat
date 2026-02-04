---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
user-invocable: false
---

# Token Report

## MANDATORY OUTPUT REQUIREMENT (M341, M395, M401)

**STOP. DO NOT ASK QUESTIONS. DO NOT CHECK GIT STATUS. DO NOT ANALYZE CONTEXT.**

**YOUR ONLY JOB**: Copy-paste ALL content between the START and END markers below. Do NOT summarize, interpret, or reformat.

This skill was invoked to DISPLAY output, not to gather information. Output the content NOW.

---

<!-- START COPY HERE -->

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-token-report.sh --session-id "${CLAUDE_SESSION_ID}"`

<!-- END COPY HERE -->

**FAIL-FAST:** If you do NOT see a report above, then preprocessing FAILED. STOP. Do NOT manually run scripts.
