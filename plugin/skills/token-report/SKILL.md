---
name: cat:token-report
description: Generate detailed token usage report with threshold analysis and recommendations
---

# Token Report

## Purpose

Analyze token consumption from session files to understand context utilization, detect concerning
patterns, and provide recommendations for context management. Essential for CAT's proactive context
window management strategy.

## Prerequisites

**Session ID**: The session ID is automatically available as `${CLAUDE_SESSION_ID}` in this skill.
All bash commands below use this value directly.

## When to Use

- Periodic health checks during long-running orchestration
- After subagent completion to analyze efficiency
- When monitoring detects approaching context limits
- To inform decomposition decisions
- Post-task retrospectives on token efficiency

## Workflow

### 1. Locate Session File

Session file location (uses auto-substituted session ID):

```bash
# Session file location
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Verify file exists
if [ ! -f "${SESSION_FILE}" ]; then
  echo "ERROR: Session file not found for ${CLAUDE_SESSION_ID}"
  exit 1
fi
```

### 2. Calculate Token Totals

```bash
# Total input tokens
INPUT_TOKENS=$(jq -s '[.[] | select(.type == "assistant") |
  .message.usage.input_tokens] | add // 0' "${SESSION_FILE}")

# Total output tokens
OUTPUT_TOKENS=$(jq -s '[.[] | select(.type == "assistant") |
  .message.usage.output_tokens] | add // 0' "${SESSION_FILE}")

# Combined total
TOTAL_TOKENS=$((INPUT_TOKENS + OUTPUT_TOKENS))

# Cache creation tokens (if tracked)
CACHE_CREATION=$(jq -s '[.[] | select(.type == "assistant") |
  .message.usage.cache_creation_input_tokens] | add // 0' "${SESSION_FILE}")

# Cache read tokens (if tracked)
CACHE_READ=$(jq -s '[.[] | select(.type == "assistant") |
  .message.usage.cache_read_input_tokens] | add // 0' "${SESSION_FILE}")
```

### 3. Count Compaction Events

```bash
# Compaction events indicate context window resets
COMPACTION_COUNT=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")

# Get timestamps of compactions
COMPACTION_TIMES=$(jq -s '[.[] | select(.type == "summary") | .timestamp]' "${SESSION_FILE}")
```

### 4. Compare Against Threshold

**Critical Threshold: 40% of contextLimit = 80,000 tokens**

```bash
CONTEXT_LIMIT=200000  # Standard Claude context window
THRESHOLD_PERCENT=40
THRESHOLD_TOKENS=$((CONTEXT_LIMIT * THRESHOLD_PERCENT / 100))  # 80,000

# Calculate percentage used
PERCENT_USED=$((TOTAL_TOKENS * 100 / CONTEXT_LIMIT))

# Determine status
if [ "${TOTAL_TOKENS}" -ge "${THRESHOLD_TOKENS}" ]; then
  STATUS="WARNING"
elif [ "${TOTAL_TOKENS}" -ge $((THRESHOLD_TOKENS * 75 / 100)) ]; then
  STATUS="CAUTION"  # 30% (75% of 40%)
else
  STATUS="HEALTHY"
fi
```

### 5. Calculate Efficiency Metrics

```bash
# Messages in session
MESSAGE_COUNT=$(jq -s '[.[] | select(.type == "assistant")] | length' "${SESSION_FILE}")

# Tool calls
TOOL_CALLS=$(jq -s '[.[] | select(.type == "tool_use")] | length' "${SESSION_FILE}")

# Average tokens per message
AVG_TOKENS_PER_MSG=$((TOTAL_TOKENS / MESSAGE_COUNT))

# Input/Output ratio
IO_RATIO=$(echo "scale=2; ${INPUT_TOKENS} / ${OUTPUT_TOKENS}" | bc 2>/dev/null || echo "N/A")
```

### 6. Generate Report

```yaml
token_report:
  session_id: abc123
  generated_at: 2026-01-10T15:45:00Z

  token_usage:
    input_tokens: 45000
    output_tokens: 20000
    total_tokens: 65000
    cache_creation_tokens: 5000
    cache_read_tokens: 12000

  context_analysis:
    context_limit: 200000
    tokens_used: 65000
    percent_used: 32.5%
    threshold: 80000 (40%)
    headroom: 15000
    status: HEALTHY

  compaction:
    events: 0
    timestamps: []

  efficiency:
    messages: 45
    tool_calls: 120
    avg_tokens_per_message: 1444
    input_output_ratio: 2.25

  recommendations: []
```

### 7. Generate Recommendations

Based on analysis, provide actionable guidance:

```yaml
recommendations:
  # If approaching threshold
  - type: CONTEXT_WARNING
    condition: "tokens_used > 75% of threshold"
    action: "Consider decomposing remaining work into new subagent"

  # If compaction occurred
  - type: COMPACTION_DETECTED
    condition: "compaction_events > 0"
    action: "Context was compacted. Review for lost context. Consider smaller task scope."

  # If inefficient token usage
  - type: HIGH_INPUT_RATIO
    condition: "input_output_ratio > 5.0"
    action: "High re-reading of context. Consider more targeted file reads."

  # If many messages without progress
  - type: LOW_EFFICIENCY
    condition: "messages > 50 && commits == 0"
    action: "Many messages without commits. May indicate confusion or scope issues."
```

## Examples

### Healthy Session Report

```yaml
token_report:
  session_id: parser-sub-a1b2c3d4
  status: HEALTHY

  summary:
    total_tokens: 45000
    percent_used: 22.5%
    compactions: 0
    efficiency_score: 0.89

  interpretation: |
    Session is well within context limits.
    Good efficiency with 3 commits per 15K tokens.
    No intervention needed.
```

### Warning Session Report

```yaml
token_report:
  session_id: formatter-sub-b2c3d4e5
  status: WARNING

  summary:
    total_tokens: 85000
    percent_used: 42.5%
    compactions: 1
    efficiency_score: 0.32

  interpretation: |
    Session has exceeded 40% threshold.
    One compaction event indicates context pressure.
    Efficiency declining - likely re-reading same context.

  recommendations:
    - "Collect current results immediately"
    - "Decompose remaining work into fresh subagent"
    - "Review task scope for future similar work"
```

### Aggregate Report (Multiple Subagents)

```yaml
aggregate_token_report:
  generated_at: 2026-01-10T16:00:00Z

  subagents:
    - id: a1b2c3d4
      total: 65000
      status: HEALTHY
    - id: b2c3d4e5
      total: 85000
      status: WARNING
    - id: c3d4e5f6
      total: 35000
      status: HEALTHY

  aggregate:
    total_tokens: 185000
    average_per_subagent: 61667
    highest_usage: b2c3d4e5 (85000)
    total_compactions: 1

  fleet_health: CAUTION
  recommendation: "One subagent approaching limits. Monitor closely."
```

## Anti-Patterns

### Always flag compaction events as concerns

```yaml
# ❌ Treating compaction as normal
compaction_events: 3
status: HEALTHY  # Wrong!

# ✅ Compaction always indicates concern
compaction_events: 3
status: WARNING
recommendation: "Multiple compactions indicate task is too large"
```

### Always provide contextual interpretation for numbers

```yaml
# ❌ Numbers without meaning
tokens: 65000

# ✅ Contextual interpretation
tokens: 65000
percent_of_limit: 32.5%
status: HEALTHY
headroom_remaining: 135000
```

### Warn proactively at 40% threshold

```bash
# ❌ Warning only at limit
if [ "${PERCENT}" -ge 100 ]; then
  echo "WARNING"
fi

# ✅ Proactive warning at 40%
if [ "${PERCENT}" -ge 40 ]; then
  echo "WARNING - approaching context limit"
fi
```

### Analyze individually before aggregating

```yaml
# ❌ Only aggregate view
total_fleet_tokens: 185000
status: OK  # Misses individual issues

# ✅ Individual + aggregate
subagent_a: 65000 (HEALTHY)
subagent_b: 85000 (WARNING)  # This needs attention!
subagent_c: 35000 (HEALTHY)
aggregate: 185000
```

## Related Skills

- `cat:monitor-subagents` - Uses token reports for health checks
- `cat:decompose-task` - Triggered by token warnings
- `cat:collect-results` - Includes token metrics in collection
- `cat:learn-from-mistakes` - Uses token data for context-related analysis
