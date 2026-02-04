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

<!--
INTERNAL REFERENCE (NOT FOR AGENT - M402)
=========================================
The sections below are for human maintainers only.
They were REMOVED from agent-visible content because they primed
analytical/verification behavior instead of verbatim output.

## Reference
The preprocessing script has already:
- Gathered subagent token data from session files
- Formatted token counts (68.4k, 1.5M format)
- Calculated context percentages with health indicators
- Formatted durations (1m 7s format)
- Built the aligned table with correct column widths

## Output Structure
The table contains: Type, Description, Tokens, Context (with health indicator), Duration columns.

## Verification (for human review)
- Script Output report found in context
- Table copied exactly (no modifications)
- No additional computation performed

## Anti-Patterns
- Never attempt manual table construction
- Never modify alignment

## Related Skills
- cat:monitor-subagents, cat:decompose-issue, cat:learn
-->
