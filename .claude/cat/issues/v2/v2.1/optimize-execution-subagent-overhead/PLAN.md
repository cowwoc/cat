# Plan: optimize-execution-subagent-overhead

## Goal

Update `/cat:optimize-execution` skill to produce per-delegation trade-off analysis when subagent delegation is
detected. The analysis must compare cumulative input tokens for the delegated phase before (inline on main agent) vs
after (subagent), using **empirical token measurements from JSONL transcripts** — not theoretical estimates.

## Satisfies

None (infrastructure improvement)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None - documentation-only change to an existing skill
- **Mitigation:** N/A

## Files to Modify

- `plugin/skills/optimize-execution/first-use.md` - Add subagent delegation trade-off analysis section

## Pre-conditions

- [ ] All dependent issues are closed

## Background: Empirical Token Measurement from JSONL

Claude Code session transcripts (JSONL files at `~/.config/claude/projects/<project>/<session-id>.jsonl`) record
per-turn token usage in the `message.usage` object of every `type: "assistant"` entry:

```json
"usage": {
  "input_tokens": 3,
  "cache_creation_input_tokens": 8219,
  "cache_read_input_tokens": 9539,
  "output_tokens": 3
}
```

**Critical insight:** The `input_tokens` field reports only **uncached new tokens**, not the total context. The actual
context size at any turn is:

```
total_context = input_tokens + cache_read_input_tokens + cache_creation_input_tokens
```

This total grows monotonically across turns as conversation history accumulates (each API call re-sends the entire
conversation).

**Distinguishing main vs subagent turns:** Subagent entries have a `parentToolUseID` field; main agent entries do not.
Deduplication by `message.id` is required because streaming produces multiple JSONL entries per logical turn.

**Cache economics per turn:**

| Category | API Field | Cost Multiplier | What It Represents |
|----------|-----------|----------------|--------------------|
| Uncached | `input_tokens` | 1x base rate | New tokens not in any cache |
| Cache write (5min) | `cache_creation.ephemeral_5m_input_tokens` | 1.25x | Written to 5-minute cache |
| Cache write (1hr) | `cache_creation.ephemeral_1h_input_tokens` | 2x | Written to 1-hour cache |
| Cache read | `cache_read_input_tokens` | 0.1x (90% discount) | Read from existing cache |

**Subagent cache sharing:** Empirical data shows subagents benefit from the parent's prompt cache. A subagent's first
turn typically shows high `cache_read_input_tokens` (shared system prompt, tool definitions, CLAUDE.md) with
`cache_creation_input_tokens` only for subagent-unique content (task prompt, agent instructions, injected skill
listings).

## Execution Steps

1. **Add Subagent Delegation Analysis section** to `plugin/skills/optimize-execution/first-use.md`
   - Files: `plugin/skills/optimize-execution/first-use.md`
   - Add a new optimization pattern category `delegation` alongside existing patterns (batch, cache, parallel, pipeline,
     script_extraction)
   - The section must instruct the optimizer to **extract empirical measurements from JSONL** rather than using
     theoretical estimates

   **Measurement methodology (from JSONL):**

   For each delegation detected in the session:

   a. **Identify the delegation point:** Find the Task tool_use in the main agent's JSONL that spawned the subagent.
      Record the main agent's context at that turn:
      - `C_main = input_tokens + cache_read + cache_creation` from the assistant turn immediately before the Task call

   b. **Measure main agent cost of delegation:** Count main agent turns consumed by the delegation (typically 2: the
      Task call turn + the turn processing the result). Sum their total context:
      - `main_delegation_cost = sum(total_context for each main agent turn involved in delegation)`

   c. **Measure subagent cost:** From the subagent's JSONL entries (identified by `parentToolUseID`), extract per-turn
      context for every assistant turn:
      - `subagent_cost = sum(total_context for each subagent assistant turn)`
      - Also record: `N_sub_turns` (subagent turn count), `C_sub_first` (first turn total context), `C_sub_last`
        (last turn total context)

   d. **Estimate inline alternative:** If the same work had been done inline on the main agent, each turn would have
      used the main agent's context (which would have grown with each turn's output). Estimate:
      - `inline_cost = N_sub_turns * C_main` (conservative: assumes main context stays flat)
      - This is a lower bound; in practice, main context would grow with tool results, making inline even more
        expensive

   e. **Compute cost-weighted totals:** Apply cache pricing multipliers to get cost-equivalent tokens:
      - `cost_weighted = (input_tokens * 1.0) + (cache_read * 0.1) + (cache_write_5m * 1.25) + (cache_write_1h * 2.0)`
      - Compute for both the inline estimate and the actual delegation

   **Analysis output per delegation:**

   ```
   Phase: [phase name, e.g., "squash"]
   Main context at delegation: [C_main] tokens
   Subagent turns: [N_sub_turns]
   Subagent first turn context: [C_sub_first] (cache_read: X, cache_create: Y, new: Z)
   Subagent last turn context: [C_sub_last]

   Raw token comparison:
     Inline estimate:    [inline_cost] cumulative tokens
     Actual delegation:  [main_delegation_cost + subagent_cost] cumulative tokens
     Delta:              [difference] tokens ([percentage]%)

   Cost-weighted comparison (cache pricing applied):
     Inline estimate:    [inline_cost_weighted] cost-equivalent tokens
     Actual delegation:  [delegation_cost_weighted] cost-equivalent tokens
     Delta:              [difference] cost-equivalent tokens ([percentage]%)

   Cache efficiency: [cache_read / total_context]% of subagent tokens were cache hits
   ```

2. **Add delegation entry to JSON output format** in the output structure section
   - Add to the `optimizations` array a new category `"delegation"` with fields:
     - `phase`: which phase was delegated (e.g., "squash")
     - `subagent_turns`: number of subagent turns
     - `main_context_at_delegation`: C_main (empirical, from JSONL)
     - `subagent_first_turn_context`: C_sub_first (empirical, from JSONL)
     - `subagent_first_turn_breakdown`: `{cache_read, cache_create, new}` (empirical)
     - `subagent_last_turn_context`: C_sub_last (empirical)
     - `inline_estimate_cumulative`: raw cumulative tokens if done inline
     - `delegation_actual_cumulative`: raw cumulative tokens (main + subagent)
     - `delta_tokens`: net change (negative = savings)
     - `delta_percent`: percentage change
     - `cost_weighted_inline`: cache-pricing-adjusted inline estimate
     - `cost_weighted_delegation`: cache-pricing-adjusted delegation actual
     - `cost_weighted_delta`: net cost-weighted change
     - `cache_hit_rate`: percentage of subagent tokens that were cache reads

3. **Add JSONL extraction instructions** to the analysis steps
   - The optimizer must extract token data from JSONL using the session-analyzer or direct JSONL parsing
   - Provide the bash pattern for extracting per-turn token usage:
     ```bash
     # Extract main agent turns (no parentToolUseID), deduplicated by message ID
     grep '"type":"assistant"' "$SESSION_FILE" | grep -v '"parentToolUseID"' | ...

     # Extract subagent turns (has parentToolUseID), deduplicated by message ID
     grep '"type":"assistant"' "$SESSION_FILE" | grep '"parentToolUseID"' | ...

     # Per turn: total_context = input_tokens + cache_read + cache_creation
     ```
   - Reference the session-analyzer tool for structured analysis:
     `"${CLAUDE_PLUGIN_ROOT}/client/bin/session-analyzer" analyze "$SESSION_ID"`

4. **Add worked example** using empirical data from a real session:
   - Show actual JSONL-extracted numbers (not theoretical estimates)
   - Example based on observed patterns:

   ```
   Phase: explore (haiku subagent for codebase research)
   Main context at delegation: 40,396 tokens
   Subagent turns: 15
   Subagent first turn: 17,761 (cache_read: 9,539, cache_create: 8,219, new: 3)
   Subagent last turn: 62,294

   Raw token comparison:
     Inline estimate:    15 * 40,396 = 605,940 cumulative tokens
     Actual delegation:  80,792 (main: 2 turns) + 503,859 (subagent: 15 turns) = 584,651
     Delta:              -21,289 tokens (-3.5%)

   Cost-weighted comparison:
     Subagent cache hit rate: 54-99% per turn (high due to shared prompt cache)
     Cost-weighted inline:     [computed with main agent cache rates]
     Cost-weighted delegation: [computed with subagent cache rates]
     Savings amplified by subagent's high cache-read ratio (0.1x pricing)
   ```

   Key insight from empirical data: The raw token savings from delegation are modest (~3-5%) because
   subagent context grows across turns. The **cost-weighted savings are much larger** because subagents
   achieve very high cache hit rates (the shared system prompt, tool definitions, and CLAUDE.md are all
   cache reads at 0.1x).

## Post-conditions

- [ ] `plugin/skills/optimize-execution/first-use.md` contains a delegation analysis section
- [ ] The section uses JSONL-based empirical measurement, not theoretical estimates
- [ ] Measurement methodology extracts `input_tokens`, `cache_read_input_tokens`, and
      `cache_creation_input_tokens` from actual session JSONL entries
- [ ] Main vs subagent turns are distinguished by presence/absence of `parentToolUseID`
- [ ] Cost-weighted analysis applies cache pricing multipliers (0.1x for reads, 1.25x/2x for writes)
- [ ] The JSON output format includes a `delegation` category with empirical fields
- [ ] A worked example with real JSONL-derived numbers is included
- [ ] No existing optimization patterns are removed or broken
