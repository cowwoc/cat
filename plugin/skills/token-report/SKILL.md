---
name: cat:token-report
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
---

# Token Report

## Purpose

Display a compact token usage report showing per-subagent breakdown with context utilization,
health status, and duration. Essential for understanding session resource consumption at a glance.

---

## Procedure

### Step 1: Require output template (MANDATORY)

**Check context for "OUTPUT TEMPLATE TOKEN REPORT".**

If found:
1. Output the table **exactly as provided** - no modifications
2. Do NOT recalculate values or adjust alignment
3. Skip to Verification

If NOT found: **FAIL immediately** with this message:

```
ERROR: Output template token report not found.

The hook precompute-token-report.sh should have provided the table data.
Do NOT attempt manual computation - the alignment requires deterministic
Python-based calculation.

Possible causes:
1. Session file not found
2. No subagent data in session
3. Hook execution failed

Try running /cat:token-report again or check session status.
```

Do NOT proceed to manual extraction or table building.

### Step 2: Output the report

Copy-paste the output template content exactly. The handler has already:
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
- **Description**: Task description (truncated if long)
- **Tokens**: Formatted count with k/M suffix
- **Context**: Percentage with health indicator (warning/critical markers appear inline)
- **Duration**: Formatted time

Health indicators appear within the Context column to show status at a glance.

---

## Verification

- [ ] Output template results found in context
- [ ] Table copied exactly (no modifications)
- [ ] No additional computation performed

---

## Anti-Patterns

### Never attempt manual table construction

If output template is missing, FAIL. Do not try to:
- Extract data manually with jq
- Build table rows by hand
- Guess at column widths

### Never modify output template alignment

Copy the table exactly as provided. The handler calculated precise padding.

---

## Related Skills

- `cat:monitor-subagents` - Uses token data for health checks
- `cat:decompose-task` - Triggered when context reaches critical levels
- `cat:learn-from-mistakes` - Uses token data for context-related analysis
