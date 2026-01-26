---
name: cat:token-report
description: Generate detailed token usage report with threshold analysis and recommendations
---

# Token Report

## Purpose

Display a compact token usage report showing per-subagent breakdown with context utilization, health status, and duration. Essential for understanding session resource consumption at a glance.

## When to Use

- Quick health check during any session
- Periodic monitoring during long-running orchestration
- After subagent completion to check overall consumption
- Before deciding whether to decompose remaining work
- Post-task retrospectives on efficiency

## Step 1: Check for Pre-Computed Results (MANDATORY)

**CRITICAL**: This skill requires hook-based pre-computation. Check context for:

```
OUTPUT TEMPLATE TOKEN REPORT:
```

### If OUTPUT TEMPLATE TOKEN REPORT is found:

Output the table EXACTLY as provided. Do NOT modify alignment or recalculate values.

**Example output template:**
```
╭───────────────────┬────────────────────────────────┬──────────┬──────────────────┬────────────╮
│ Type              │ Description                    │ Tokens   │ Context          │ Duration   │
├───────────────────┼────────────────────────────────┼──────────┼──────────────────┼────────────┤
│ Explore           │ Explore codebase               │ 68.4k    │ 34%              │ 1m 7s      │
│ general-purpose   │ Implement fix                  │ 90.0k    │ 45% ⚠️            │ 43s        │
│ general-purpose   │ Refactor module                │ 170.0k   │ 85% 🚨            │ 3m 12s     │
├───────────────────┼────────────────────────────────┼──────────┼──────────────────┼────────────┤
│                   │ TOTAL                          │ 328.4k   │ -                │ 5m 2s      │
╰───────────────────┴────────────────────────────────┴──────────┴──────────────────┴────────────╯
```

### If OUTPUT TEMPLATE TOKEN REPORT is NOT found:

**FAIL immediately** with this message:

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

## Table Format Reference

The output template table uses these specifications:

**Column widths (fixed):**
| Column | Width | Content |
|--------|-------|---------|
| Type | 17 | Subagent type (truncated with ...) |
| Description | 30 | Task description (truncated with ...) |
| Tokens | 8 | Formatted count (68.4k, 1.5M) |
| Context | 16 | Percentage with emoji indicator |
| Duration | 10 | Formatted time (1m 7s) |

**Context indicators (INSIDE column):**
| Context % | Display | Meaning |
|-----------|---------|---------|
| < 40% | "34%" | Healthy - plenty of headroom |
| >= 40% and < 80% | "45% ⚠️" | Warning - above soft target |
| >= 80% | "85% 🚨" | Critical - approaching limit |

**Box characters:**
- Top: `╭─┬─╮`
- Divider: `├─┼─┤`
- Bottom: `╰─┴─╯`
- Sides: `│`

## Verification Checklist

Before outputting the table, verify:

- [ ] Output template results found in context
- [ ] Table copied exactly (no modifications)
- [ ] All box characters preserved
- [ ] Emoji indicators inside Context column
- [ ] No additional computation performed

## Anti-Patterns

### Never attempt manual table construction

```bash
# BAD - Manual jq extraction and formatting
jq -s '...' "$SESSION_FILE"
# Then manually building table rows

# GOOD - Use template results only
# Output exactly what the hook provided
```

### Never modify output template alignment

```
# BAD - "Fixing" spacing or alignment
│ Type           │  # Wrong - modified padding

# GOOD - Copy exactly as provided
│ Type              │  # Correct - preserved padding
```

## Related Skills

- `cat:monitor-subagents` - Uses token data for health checks
- `cat:decompose-task` - Triggered when context reaches critical levels
- `cat:learn-from-mistakes` - Uses token data for context-related analysis
