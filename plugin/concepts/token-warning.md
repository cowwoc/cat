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
- Issue may be too large for single-subagent execution

RECOMMENDATION: Decompose remaining work into smaller issues.
```

### User Decision

Use AskUserQuestion:
- header: "Token Warning"
- question: "Issue triggered context compaction. Decomposition is strongly recommended:"
- options:
  - "Decompose" - Split into smaller issues via /cat:decompose-issue (Recommended)
  - "Continue anyway" - Accept potential quality impact
  - "Abort" - Stop and review work quality

**If "Decompose":**
Invoke `/cat:decompose-issue` to split remaining similar work.

**If "Continue anyway":**
Proceed but note in STATE.md that compaction occurred.

**If "Abort":**
Rollback changes and mark issue for manual review.

---

## High Token Usage Warning (Informational)

**If tokens >= targetContextUsage threshold but no compaction:**

```
📊 HIGH TOKEN USAGE: {N} tokens ({percentage}% of context)

The subagent used significant context (threshold: {targetContextUsage}%).
Consider decomposing similar issues in the future.
```

No action required - this is informational for future planning.

---

## Token Metrics Reporting (Mandatory)

**ALWAYS report token metrics after subagent completion:**

```
## Subagent Execution Report

**Issue:** {issue-name}
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

**Why this metric:** The `totalTokens` from `toolUseResult` represents actual context the subagent
processed during execution. This is different from cumulative API tokens (`input_tokens +
output_tokens` from message.usage) which only shows response overhead.

---

## Token Estimate Variance Check

After collecting actual token usage, compare against estimate from issue analysis:

```bash
# Calculate variance
VARIANCE_THRESHOLD=125  # 25% higher = 125% of estimate
ACTUAL_PERCENT=$((ACTUAL_TOKENS * 100 / ESTIMATED_TOKENS))

if [ "${ACTUAL_PERCENT}" -ge "${VARIANCE_THRESHOLD}" ]; then
  echo "⚠️ TOKEN ESTIMATE VARIANCE DETECTED"
  # Trigger learn
fi
```

**If actual >= estimate × 1.25:**
Invoke `/cat:learn` with:
- Description: "Token estimate underestimated actual usage by {variance}%"
- Estimated vs actual tokens
- Issue details

---

## When NOT to Load

- Normal execution without compaction
- Token usage below threshold
- Issue not yet started
