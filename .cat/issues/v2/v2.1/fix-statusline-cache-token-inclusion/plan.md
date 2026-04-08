# Plan: fix-statusline-cache-token-inclusion

## Problem

Statusline always shows 0% context usage because `context_window.current_usage.input_tokens`
only contains the non-cached portion of input tokens (~3 tokens when prompt caching is active).
The cached token fields (`cache_read_input_tokens`, `cache_creation_input_tokens`) are ignored,
so the effective context usage is never computed correctly.

## Parent Requirements

None

## Reproduction Code

```
# Captured from live session:
context_window.current_usage = {
  "input_tokens": 3,
  "cache_read_input_tokens": 99144,
  "cache_creation_input_tokens": 1157
}
# Code reads only input_tokens (3), giving effectiveUsed = 3 - 34500 = clamped to 0 → 0%
```

## Expected vs Actual

- **Expected:** Statusline shows ~39% context usage (summing all three token fields,
  then applying overhead scaling)
- **Actual:** Statusline shows 0% because only non-cached `input_tokens` (3) is read

## Root Cause

`AbstractClaudeStatusline.parseStatuslineJson()` reads only `current_usage.input_tokens`
and assigns it to `parsedUsedTokens`. The fields `cache_read_input_tokens` and
`cache_creation_input_tokens` are present in the JSON but never read. When prompt caching
is active (the common case), `input_tokens` is nearly always less than `OVERHEAD_TOKENS`
(34,500), so `scaleContextPercent` clamps the result to 0.

Note: `context_window.used_percentage` is intentionally NOT used because it does not
account for the autocompaction buffer (the `OVERHEAD_TOKENS` subtraction in
`scaleContextPercent` represents this buffer).

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Existing tests that use `input_tokens` alone in JSON will need
  updating to reflect the three-field sum
- **Mitigation:** TDD — write failing tests first against real Claude Code JSON structure,
  then implement

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeStatusline.java` - sum
  `input_tokens + cache_read_input_tokens + cache_creation_input_tokens` for `parsedUsedTokens`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` - update
  Javadoc input field list to document all three token fields
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java` - update
  existing tests to use the three-field structure; add tests for cache token inclusion

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Write failing tests

Add tests to `StatuslineCommandTest.java` that:
- Send JSON with `cache_read_input_tokens` and `cache_creation_input_tokens` populated
  (matching real Claude Code output) and verify a non-zero context percentage
- Verify that `input_tokens` alone (old behavior) would fail to produce the correct result

### Job 2: Fix AbstractClaudeStatusline

In `parseStatuslineJson()`, after reading `input_tokens`, also read
`cache_read_input_tokens` and `cache_creation_input_tokens` from `current_usage` (both
optional, default 0 if absent), and sum all three into `parsedUsedTokens`.

### Job 3: Update Javadoc

Update the class-level Javadoc in `StatuslineCommand.java` to list all three
`current_usage` sub-fields (`input_tokens`, `cache_read_input_tokens`,
`cache_creation_input_tokens`).

### Job 4: Run full build

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass.

## Post-conditions

- [ ] All tests pass (`mvn -f client/pom.xml verify -e` exits 0)
- [ ] New tests cover the three-field sum scenario with realistic token values
- [ ] With `cache_read_input_tokens: 99144`, `cache_creation_input_tokens: 1157`,
  `input_tokens: 3`, `context_window_size: 200000`, the computed percentage is > 0%
- [ ] E2E: Live statusline shows a non-zero context percentage matching the session's
  actual context usage
