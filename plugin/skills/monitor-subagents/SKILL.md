---
name: cat:monitor-subagents
description: Check status of running subagents including token usage and context limits
---

# Monitor Subagents

## Purpose

Track the status of active subagents, monitor their token consumption, detect context limit
approaches, and identify compaction events. Enables the parent agent to make informed decisions
about intervention or result collection.

## When to Use

- After spawning one or more subagents
- Periodically during long-running orchestration
- When deciding whether to spawn additional subagents
- Before collecting results to verify completion
- When suspecting a subagent has stalled or hit limits

## Workflow

### 1. Run Monitoring Script (Recommended)

Use the optimized monitoring script for minimal context impact:

```bash
/workspace/cat/scripts/monitor-subagents.sh
```

**Output format:**
```json
{
  "subagents": [
    {"id": "a1b2c3d4", "task": "1.2-parser", "status": "running", "tokens": 45000, "compactions": 0},
    {"id": "b2c3d4e5", "task": "1.3-formatter", "status": "warning", "tokens": 85000, "compactions": 1}
  ],
  "summary": {"total": 2, "running": 1, "complete": 0, "warning": 1}
}
```

**Status values:**
- `running` - Active, tokens below threshold
- `warning` - Tokens >= 80K (40% of context), consider intervention
- `complete` - Completion marker (`.completion.json`) found in worktree

**Token Metric Source:**

The `tokens` field in monitor output comes from:
1. `.completion.json` if subagent has completed (preferred)
2. Session file parsing for running subagents (legacy method)

For accurate token metrics on completed subagents, use `/cat:token-report` which extracts
`totalTokens` from Task tool completions. This metric matches the CLI "Done" display and
represents actual context processed, not cumulative API response tokens.

### 2. Interpret Results

**If status = "warning":**
- Subagent approaching context limits (>= 80K tokens)
- For accurate current metrics, run `/cat:token-report`
- Consider collecting partial results with `collect-results`
- May need to decompose remaining work

**If status = "complete":**
- Subagent finished, ready for result collection
- Check `.completion.json` in worktree for details

**If compactions > 0:**
- Subagent experienced context pressure
- Quality may have degraded
- Collect results and review carefully

## Examples

### Quick Status Check

```bash
# Single command returns all subagent status
/workspace/cat/scripts/monitor-subagents.sh
```

### Check Specific Subagent

```bash
# Filter output with jq
/workspace/cat/scripts/monitor-subagents.sh | jq '.subagents[] | select(.id == "a1b2c3d4")'
```

### Count Active Subagents

```bash
# Get summary counts only
/workspace/cat/scripts/monitor-subagents.sh | jq '.summary'
```

## Anti-Patterns

### Use single script call for all metrics

```bash
# ❌ Running jq manually each poll (accumulates tool call overhead)
jq -s '[...] | add' "${SESSION_FILE}"
jq -s '[...] | length' "${SESSION_FILE}"

# ✅ Single script call returns all data
/workspace/cat/scripts/monitor-subagents.sh
```

### Use reasonable polling intervals (30-60 seconds)

```bash
# ❌ Checking every second
while true; do
  /workspace/cat/scripts/monitor-subagents.sh
  sleep 1
done

# ✅ Reasonable polling interval (30-60 seconds)
while true; do
  /workspace/cat/scripts/monitor-subagents.sh
  sleep 60
done
```

### Treat compaction as intervention signal

```bash
# ❌ Ignoring compaction
if [ "${COMPACTIONS}" -gt 0 ]; then
  echo "Compaction detected, continuing anyway..."
fi

# ✅ Treat compaction as intervention signal
if [ "${COMPACTIONS}" -gt 0 ]; then
  echo "Compaction detected - initiating result collection"
  # Trigger collect-results
fi
```

### Verify session activity (worktree presence alone is insufficient)

```bash
# ❌ Assuming worktree = active subagent
ACTIVE_COUNT=$(git worktree list | grep -c "-sub-")

# ✅ Verify session activity
for worktree in $(get_subagent_worktrees); do
  if is_session_active "${worktree}"; then
    ACTIVE_COUNT=$((ACTIVE_COUNT + 1))
  fi
done
```

## Related Skills

- `cat:spawn-subagent` - Launch new subagents
- `cat:collect-results` - Gather completed subagent work
- `cat:token-report` - Detailed token analysis
- `cat:parallel-execute` - Orchestrate multiple subagents
