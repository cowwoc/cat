# Workflow: Token Warning and Compaction Handling

## When to Load

Load this workflow when:
- Subagent reports **compaction events > 0**
- Token usage **exceeds targetContextUsage threshold**

## Compaction Event Warning (Critical)

**If compaction events > 0:**

Context compaction means the subagent's context window was exhausted and summarized
during execution. This indicates potential quality degradation.

### Display Warning

```
⚠️ CONTEXT COMPACTION DETECTED

The subagent experienced {N} compaction event(s). This indicates:
- Context window was exhausted during execution
- Quality may have degraded as context was summarized
- Task may be too large for single-subagent execution

RECOMMENDATION: Decompose remaining work into smaller tasks.
```

### User Decision

Use AskUserQuestion:
- header: "Token Warning"
- question: "Task triggered context compaction. Decomposition is strongly recommended:"
- options:
  - "Decompose" - Split into smaller tasks via /cat:decompose-task (Recommended)
  - "Continue anyway" - Accept potential quality impact
  - "Abort" - Stop and review work quality

**If "Decompose":**
Invoke `/cat:decompose-task` to split remaining similar work.

**If "Continue anyway":**
Proceed but note in STATE.md that compaction occurred.

**If "Abort":**
Rollback changes and mark task for manual review.

---

## High Token Usage Warning (Informational)

**If tokens >= targetContextUsage threshold but no compaction:**

```
📊 HIGH TOKEN USAGE: {N} tokens ({percentage}% of context)

The subagent used significant context (threshold: {targetContextUsage}%).
Consider decomposing similar tasks in the future.
```

No action required - this is informational for future planning.

---

## Token Metrics Reporting (Mandatory)

**ALWAYS report token metrics after subagent completion:**

```
## Subagent Execution Report

**Task:** {task-name}
**Status:** {success|partial|failed}

**Token Usage:**
- Total tokens: {N} ({percentage}% of context)
- Input tokens: {input_N}
- Output tokens: {output_N}
- Compaction events: {N}

**Work Summary:**
- Commits: {N}
- Files changed: {N}
- Lines: +{added} / -{removed}
```

---

## Token Estimate Variance Check (M099)

After collecting actual token usage, compare against estimate from task analysis:

```bash
# Calculate variance
VARIANCE_THRESHOLD=125  # 25% higher = 125% of estimate
ACTUAL_PERCENT=$((ACTUAL_TOKENS * 100 / ESTIMATED_TOKENS))

if [ "${ACTUAL_PERCENT}" -ge "${VARIANCE_THRESHOLD}" ]; then
  echo "⚠️ TOKEN ESTIMATE VARIANCE DETECTED"
  # Trigger learn-from-mistakes
fi
```

**If actual >= estimate × 1.25:**
Invoke `/cat:learn-from-mistakes` with:
- Description: "Token estimate underestimated actual usage by {variance}%"
- Estimated vs actual tokens
- Task details

---

## When NOT to Load

- Normal execution without compaction
- Token usage below threshold
- Task not yet started
