---
name: cat:token-report
description: Generate detailed token usage report with threshold analysis and recommendations
---

# Token Report

## Purpose

Display a compact token usage report for the current session showing per-subagent breakdown with
context utilization, health status, and duration. Essential for understanding session resource
consumption at a glance.

## Prerequisites

**Session ID**: The session ID is automatically available as `${CLAUDE_SESSION_ID}` in this skill.
All bash commands below use this value directly.

## When to Use

- Quick health check during any session
- Periodic monitoring during long-running orchestration
- After subagent completion to check overall consumption
- Before deciding whether to decompose remaining work
- Post-task retrospectives on efficiency

## Workflow

### 1. Extract Session Metrics

Extract key metrics from the session file in a single pass.

```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Verify session file exists
if [ ! -f "${SESSION_FILE}" ]; then
    echo "ERROR: Session file not found"
    exit 1
fi

# Extract subagent data in one jq pass
SUBAGENT_DATA=$(jq -s '
  # Find Task tool calls and their results
  . as $all |
  [range(length)] |
  map(
    . as $i |
    $all[$i] |
    select(.type == "assistant" and .message.content[]?.type == "tool_use" and .message.content[]?.name == "Task") |
    {
      index: $i,
      task: (.message.content[] | select(.type == "tool_use" and .name == "Task")),
      id: (.message.content[] | select(.type == "tool_use" and .name == "Task") | .id)
    }
  ) |
  map(
    . as $task |
    ($all | map(select(.type == "user" and .toolUseResult.tool_use_id == $task.id)) | first) as $result |
    {
      type: ($task.task.input.prompt | split("\n")[0] | gsub("^## "; "") | .[0:25]),
      description: (($task.task.input.description // "Subagent task") | .[0:28]),
      tokens: ($result.toolUseResult.totalTokens // 0),
      duration_ms: ($result.toolUseResult.durationMs // 0)
    }
  ) |
  map(select(.tokens > 0))
' "${SESSION_FILE}")
```

### 2. Build Table JSON and Render

Build the table data as JSON and render via the render-box service for clean output.

```bash
CONTEXT_LIMIT=200000

# Build table JSON with formatted data
TABLE_JSON=$(echo "$SUBAGENT_DATA" | jq --argjson limit "$CONTEXT_LIMIT" '
  # Format tokens (e.g., 68400 -> "68.4k")
  def format_tokens:
    if . >= 1000000 then "\(. / 1000000 | . * 10 | floor / 10)M"
    elif . >= 1000 then "\(. / 1000 | . * 10 | floor / 10)k"
    else "\(.)"
    end;

  # Format duration (ms -> "1m 7s" or "43s")
  def format_duration:
    (. / 1000 | floor) as $secs |
    if $secs >= 60 then "\($secs / 60 | floor)m \($secs % 60)s"
    else "\($secs)s"
    end;

  # Get context status with warning indicators
  def context_status:
    (. * 100 / $limit | floor) as $pct |
    if $pct >= 80 then "\($pct)% ⚠ EXCEEDED"
    elif $pct >= 40 then "\($pct)% ⚠ HIGH"
    else "\($pct)%"
    end;

  # Calculate totals
  ([.[].tokens] | add // 0) as $total_tokens |
  ([.[].duration_ms] | add // 0) as $total_duration |

  {
    headers: ["Type", "Description", "Tokens", "Context", "Duration"],
    widths: [17, 30, 8, 14, 10],
    rows: [.[] | [
      .type,
      .description,
      (.tokens | format_tokens),
      (.tokens | context_status),
      (.duration_ms | format_duration)
    ]],
    footer: ["", "TOTAL", ($total_tokens | format_tokens), "-", ($total_duration | format_duration)]
  }
')

# Render via service (clean output only)
"${CLAUDE_PLUGIN_ROOT}/scripts/lib/box.sh" table "$TABLE_JSON"
```

## Example Output

Per-subagent breakdown table:

```
╭─────────────────┬──────────────────────────────┬────────┬──────────────┬──────────╮
│ Type            │ Description                  │ Tokens │ Context      │ Duration │
├─────────────────┼──────────────────────────────┼────────┼──────────────┼──────────┤
│ Explore         │ Explore session file format  │ 69.2k  │ 34%          │ 1m 7s    │
│ Plan            │ Plan token measurement fix   │ 56.0k  │ 28%          │ 52s      │
│ general-purpose │ Implement token fix          │ 45.0k  │ 45% ⚠ HIGH   │ 43s      │
├─────────────────┼──────────────────────────────┼────────┼──────────────┼──────────┤
│                 │ TOTAL                        │ 170.2k │ -            │ 2m 42s   │
╰─────────────────┴──────────────────────────────┴────────┴──────────────┴──────────╯
```

## Status Indicator Logic

The Context column reflects context health with minimal indicators:

| Context % | Display | Meaning |
|-----------|---------|---------|
| < 40% | Just percentage (e.g., "34%") | Healthy - plenty of headroom |
| >= 40% and < 80% | "45% ⚠ HIGH" | Warning - above soft target, monitor usage |
| >= 80% | "85% ⚠ EXCEEDED" | Critical - approaching limit, consider decomposition |

Healthy status shows only the percentage - lack of warning implies success.

## Handling Missing Data

The report gracefully handles missing data:
- Missing subagent data shows empty table with headers
- Zero tokens are filtered out
- Duration shows "0s" for missing duration data

## Anti-Patterns

### Never show intermediate bash output

```bash
# BAD - Raw output clutters the display
echo "Extracting metrics..."
jq '.cachePerformance' "$SESSION_FILE"
echo "Processing..."

# GOOD - Silent extraction, show only final table
SUBAGENT_DATA=$(jq -s '...' "$SESSION_FILE")
# Then render table directly
```

### Keep output focused

The table format is designed to be concise. Avoid adding verbose explanations or
extra whitespace around the table output.

## Related Skills

- `cat:render-box` - Box rendering library for consistent formatting
- `cat:monitor-subagents` - Uses token data for health checks
- `cat:decompose-task` - Triggered when context reaches critical levels
- `cat:learn-from-mistakes` - Uses token data for context-related analysis
