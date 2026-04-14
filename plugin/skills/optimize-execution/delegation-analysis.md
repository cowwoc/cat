# Delegation Analysis Reference

This document provides supplementary reference material for the `optimize-execution` skill's delegation
analysis steps (Steps 3–4). It contains the full worked example and manual JSONL extraction fallback,
which are useful for debugging or building intuition — but are not part of the normal execution path.

## Manual Extraction (advanced / debugging)

If session-analyzer is unavailable or you need to inspect raw data, extract directly from JSONL:

```bash
# Locate the session JSONL file
SESSION_FILE="${CLAUDE_PROJECT_DIR}/.cat/sessions/${CLAUDE_SESSION_ID}.jsonl"

# Extract main agent assistant turns (no parentToolUseID), deduplicated by message ID
# Each line has input_tokens, cache_read_input_tokens, cache_creation_input_tokens
# Note: sort -t'"' -k4,4 -u deduplicates by the 4th double-quoted field (message uuid).
# This assumes standard Claude JSONL field order: {"type":"...", "uuid":"..."}
# If field order changes, this deduplication will silently fail — prefer session-analyzer.
grep '"type":"assistant"' "$SESSION_FILE" | grep -v '"parentToolUseID"' | \
  sort -t'"' -k4,4 -u

# Extract subagent assistant turns (has parentToolUseID), deduplicated by message ID
grep '"type":"assistant"' "$SESSION_FILE" | grep '"parentToolUseID"' | \
  sort -t'"' -k4,4 -u

# Per turn: total_context = input_tokens + cache_read_input_tokens + cache_creation_input_tokens
```

## Worked Example: Subagent Delegation Trade-off Analysis

The following uses empirical token measurements extracted from a real session JSONL transcript.

**Session context:** Main agent delegated codebase exploration to a Haiku subagent for research.

```
Phase: explore (haiku subagent for codebase research)
Main context at delegation: 40,396 tokens
Subagent turns: 15
Subagent first turn: 17,761 (cache_read: 9,539, cache_create: 8,219, new: 3)
Subagent last turn: 62,294

Raw token comparison:
  Inline estimate:    15 * 40,396 = 605,940 cumulative tokens  [conservative lower bound: actual inline cost
                      would be higher as main context grows with each turn's output]
  Actual delegation:  80,792 (main: 2 turns at ~40,396 each) + 503,859 (subagent: 15 turns) = 584,651
  Delta:              -21,289 tokens (-3.5%)
  Note: inline_cost is a conservative lower bound — if delegation is cost-effective against this estimate,
        actual savings vs. true inline execution are likely even better.

Cost-weighted comparison:
  Subagent cache hit rate: 54-99% per turn (high due to shared prompt cache)
  Cost-weighted inline:     605,940 * ~0.65 effective rate = ~393,861 cost-equivalent tokens
  Cost-weighted delegation: main (80,792 at main rates) + subagent (503,859 at subagent rates with high cache hits)
                            = ~52,515 + ~87,673 = ~140,188 cost-equivalent tokens
  Cost-weighted delta:      -253,673 cost-equivalent tokens (-64.4%)
```

**Key insight from empirical data:** The raw token savings from delegation are modest (~3-5%) because
subagent context grows across turns. The **cost-weighted savings are dramatically larger** (60-70%) because
subagents achieve very high cache hit rates — the shared system prompt, tool definitions, and CLAUDE.md
are all cache reads at 0.1x pricing. A 78% cache hit rate on the subagent effectively prices those tokens
at 10 cents on the dollar, making delegation highly cost-efficient even when raw token counts are similar.

**When delegation helps most:**
- Subagent work has many turns (more turns = more cache reuse at 0.1x)
- Main agent context is large (high C_main = high inline cost per turn)
- Subagent work is cache-friendly (repeated reads of shared context)

**When delegation may not help:**
- Few subagent turns (overhead of spawning exceeds savings)
- Subagent work requires many unique new tokens (low cache hit rate)
- Main agent context is small (low C_main = low inline cost baseline)
