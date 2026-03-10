# Plan: add-parallel-chunked-read-rule

## Current State
`plugin/rules/persisted-skill-output.md` tells agents to read large persisted files in 200-line chunks using
`offset` and `limit`, but instructs them to issue each chunk sequentially — one Read call per turn. When the
persisted-output message already states the file size (e.g., "30.3KB"), all chunk offsets can be computed upfront,
making sequential reads unnecessary overhead.

## Target State
The rule additionally specifies: when the persisted-output message includes a file size, pre-compute all chunk
offsets and issue every Read call in a single parallel message. This eliminates N-1 unnecessary network round-trips
(e.g., 4 sequential reads → 1 parallel message = ~75% wall-time reduction for reads).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — agents that already read sequentially still satisfy the rule; the new guidance
  only adds an optimization for when file size is known.
- **Mitigation:** The rule explicitly preserves sequential guidance as fallback when file size is not stated.

## Files to Modify
- `plugin/rules/persisted-skill-output.md` — add parallel read guidance to the MANDATORY section

## Approach: Append Parallel Read Rule to MANDATORY Section

The existing MANDATORY section has two numbered items. Add a third item specifying the parallel read optimization,
conditioned on file size being known from the persisted-output message.

**Exact insertion point:** After item 2 ("Execute the workflow instructions…"), before the closing line
"Do NOT treat output size as a failure signal…"

**New item to add:**

```
3. When the persisted-output message states the file size (e.g., "30.3KB"), pre-compute all chunk offsets
   upfront and issue ALL Read calls in a single parallel message — do NOT read one chunk at a time. Estimate
   lines as: `ceil(size_in_bytes / 45)`, then split into 200-line chunks starting at offset 1.
   Example for a 30.3KB file (~672 lines, 4 chunks):
   - Read offset=1 limit=200
   - Read offset=201 limit=200
   - Read offset=401 limit=200
   - Read offset=601 limit=200
   All four calls in one message. When file size is NOT stated, fall back to sequential chunk-by-chunk reading.
```

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Edit `plugin/rules/persisted-skill-output.md` to insert item 3 into the MANDATORY section as specified above
  - File: `plugin/rules/persisted-skill-output.md`
- Commit with message: `refactor: add parallel chunked read rule for persisted skill output`
- Update STATE.md: status closed, progress 100%

## Post-conditions
- [ ] `plugin/rules/persisted-skill-output.md` contains a third MANDATORY item specifying parallel reads when
  file size is known
- [ ] The rule includes a concrete line-estimation formula (`ceil(bytes / 45)`) and chunk-splitting example
- [ ] Sequential fallback guidance is explicitly stated for when file size is not provided
- [ ] E2E: Read the updated rule and confirm it would instruct an agent to issue all chunks in one message for
  the "30.3KB" example used in this session's optimize-execution analysis
