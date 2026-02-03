---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
user-invocable: false
---

# Token Report

## MANDATORY OUTPUT REQUIREMENT (M341)

**YOUR ONLY JOB**: Copy-paste the report below VERBATIM. Do NOT summarize, interpret, or reformat.

---

## Pre-rendered Token Report (COPY THIS EXACTLY)

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-token-report.sh --session-id "${CLAUDE_SESSION_ID}"`

**NEVER summarize the report. NEVER describe it. COPY IT.**

---

## Reference (do not output this section)

The preprocessing script has already:
- Gathered subagent token data from session files
- Formatted token counts (68.4k, 1.5M format)
- Calculated context percentages with health indicators
- Formatted durations (1m 7s format)
- Built the aligned table with correct column widths

---

## Output Structure Reference

> **NOTE:** This section describes WHAT the output contains, not HOW to render it.
> The handler produces all rendering. You only need to copy-paste.

The table contains these columns:
- **Type**: Subagent type (truncated if long)
- **Description**: Issue description (truncated if long)
- **Tokens**: Formatted count with k/M suffix
- **Context**: Percentage with health indicator (warning/critical markers appear inline)
- **Duration**: Formatted time

Health indicators appear within the Context column to show status at a glance.

---

## Verification

- [ ] Pre-rendered report found in context
- [ ] Table copied exactly (no modifications)
- [ ] No additional computation performed

---

## Anti-Patterns

### Never attempt manual table construction

If pre-rendered content is missing, FAIL. Do not try to:
- Extract data manually with jq
- Build table rows by hand
- Guess at column widths

### Never modify alignment

Copy the table exactly as provided. The script calculated precise padding.

---

## Related Skills

- `cat:monitor-subagents` - Uses token data for health checks
- `cat:decompose-issue` - Triggered when context reaches critical levels
- `cat:learn` - Uses token data for context-related analysis
