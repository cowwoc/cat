<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Workflow: Token Warning and Compaction Handling

> See `plugin/concepts/work-decomposition.md` for the full execution hierarchy and sub-issue decomposition context.

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
- Remaining jobs should be split before spawning

RECOMMENDATION: Split remaining jobs in plan.md into smaller jobs before continuing.
```

### User Decision

Use AskUserQuestion:
- header: "Token Warning"
- question: "Issue triggered context compaction. Splitting remaining jobs is strongly recommended:"
- options:
  - "Split jobs" - Halve each remaining job in plan.md before continuing (Recommended)
  - "Continue anyway" - Accept potential quality impact
  - "Abort" - Stop and review work quality

**If "Split jobs":**
For each remaining `### Job N` in plan.md that has not yet been spawned, split it by moving the
second half of its bullet items into a new job inserted immediately after it. Renumber all subsequent
jobs to maintain a gapless sequence. Then continue spawning from the first unsplit job.

**If "Continue anyway":**
Proceed but note in index.json that compaction occurred.

**If "Abort":**
Rollback changes and mark issue for manual review.

---

## High Token Usage Warning (Proactive Job Split)

**If `percent_of_context > 40` but no compaction:**

```
📊 HIGH TOKEN USAGE: {N} tokens ({percentage}% of context)

The subagent used significant context (threshold: 40%).
Remaining jobs will be split before spawning to stay under the 40% budget.
```

**Action required:** For each remaining `### Job N` in plan.md that has not yet been spawned,
split it by moving the second half of its bullet items into a new job inserted immediately after it.
Renumber all subsequent jobs to maintain a gapless sequence. Then continue spawning from the first
unsplit job.

This split happens automatically — no user prompt is needed.

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
Invoke `/cat:learn-agent` with:
- Description: "Token estimate underestimated actual usage by {variance}%"
- Estimated vs actual tokens
- Issue details

---

## When NOT to Load

- Normal execution without compaction
- Token usage below threshold
- Issue not yet started
