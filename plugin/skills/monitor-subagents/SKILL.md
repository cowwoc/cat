---
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

## Progress Output (MANDATORY)

**Check for SCRIPT OUTPUT MONITOR SUBAGENTS in context.**

If found: Output the JSON EXACTLY as provided. Do NOT invoke scripts or Bash commands.

If NOT found: **FAIL immediately.**

```
ERROR: SCRIPT OUTPUT MONITOR SUBAGENTS not found.
Handler monitor_subagents_handler.py should have provided this via additionalContext.
Check that hooks are properly loaded.
```

Output the error and STOP. Do NOT manually construct monitoring output or invoke scripts.

## Output Format

The handler provides JSON in this format:

```json
{
  "subagents": [
    {"id": "a1b2c3d4", "issue": "1.2-parser", "status": "running", "tokens": 45000, "compactions": 0},
    {"id": "b2c3d4e5", "issue": "1.3-formatter", "status": "warning", "tokens": 85000, "compactions": 1}
  ],
  "summary": {"total": 2, "running": 1, "complete": 0, "warning": 1}
}
```

**Status values:**
- `running` - Active, tokens below threshold
- `warning` - Tokens >= 80K (40% of context), consider intervention
- `complete` - Completion marker (`.completion.json`) found in worktree

## Interpret Results

**If total = 0:**
Report "No active subagents found."

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

## Token Metric Source

The `tokens` field in monitor output comes from:
1. `.completion.json` if subagent has completed (preferred)
2. Session file parsing for running subagents

For accurate token metrics on completed subagents, use `/cat:token-report` which extracts
`totalTokens` from Task tool completions.

## Anti-Patterns

### Never invoke Bash for monitoring

```bash
# ❌ Invoking shell script (exposes tool calls to user)
${CLAUDE_PLUGIN_ROOT}/scripts/monitor-subagents.sh

# ✅ Use script output handler output
# Check for SCRIPT OUTPUT MONITOR SUBAGENTS in context
```

### Treat compaction as intervention signal

```bash
# ❌ Ignoring compaction
if compactions > 0: "continuing anyway..."

# ✅ Treat compaction as intervention signal
if compactions > 0: initiate result collection
```

### Verify session activity (worktree presence alone is insufficient)

```bash
# ❌ Assuming worktree = active subagent
ACTIVE_COUNT=$(git worktree list | grep -c "-sub-")

# ✅ Check handler output for actual status
# Handler verifies completion markers and session files
```

## Related Skills

- `cat:spawn-subagent` - Launch new subagents
- `cat:collect-results` - Gather completed subagent work
- `cat:token-report` - Detailed token analysis
- `cat:parallel-execute` - Orchestrate multiple subagents
