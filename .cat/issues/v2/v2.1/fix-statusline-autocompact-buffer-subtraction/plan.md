# Plan: fix-statusline-autocompact-buffer-subtraction

## Problem
`scaleContextPercent()` in `StatuslineCommand.java` subtracts `OVERHEAD_TOKENS` (34,500) from
`usedTokens` in the numerator. `OVERHEAD_TOKENS` includes `CONVERSATION_HISTORY_TOKENS` (21,000),
the autocompact buffer. The autocompact buffer is *reserved* space not counted in `usedTokens`, so
subtracting it from the numerator deflates the displayed percentage. With 37,500 tokens used on a
200k context window, the statusline shows 1% instead of the correct ~14.5%.

This is a regression introduced by `fix-statusline-context-percent-calculation` (closed), which
specified the incorrect formula in its plan.

## Parent Requirements
None

## Reproduction Code
```
scaleContextPercent(37_500, 200_000)
// Current:  (37500 - 34500) * 100 / (200000 - 34500) =  3000 / 165500 = 1%
// Correct:  (37500 - 13500) * 100 / (200000 - 34500) = 24000 / 165500 = 14%
```

## Expected vs Actual
- **Expected:** ~14% (user-controlled tokens / usable context)
- **Actual:** 1% (autocompact buffer incorrectly subtracted from numerator)

## Root Cause
`OVERHEAD_TOKENS = SYSTEM_PROMPT_TOKENS + TOOL_DEFINITIONS_TOKENS + CONVERSATION_HISTORY_TOKENS`
is subtracted from both numerator and denominator. The autocompact buffer
(`CONVERSATION_HISTORY_TOKENS = 21,000`) is reserved capacity that Claude's JSON does not include
in `usedTokens`, so it must only reduce the denominator (usable context), not the numerator
(effective used tokens).

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Displayed percentage changes (this is the fix, not a regression)
- **Mitigation:** Unit tests cover boundary cases before and after the fix

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — split
  `OVERHEAD_TOKENS` into two constants; update `scaleContextPercent()` to only subtract
  `SYSTEM_PROMPT_TOKENS + TOOL_DEFINITIONS_TOKENS` from the numerator
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java` — add
  regression test for the 37,500 / 200,000 case and update boundary expectations

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Fix scaleContextPercent numerator
- In `StatuslineCommand.java`, replace the single `OVERHEAD_TOKENS` constant with two constants:
  - `FIXED_OVERHEAD = SYSTEM_PROMPT_TOKENS + TOOL_DEFINITIONS_TOKENS` (6,400 + 7,100 = 13,500)
  - Keep `CONVERSATION_HISTORY_TOKENS = 21_000` separate
- Update `scaleContextPercent()`:
  - `int usableContext = totalContext - FIXED_OVERHEAD - CONVERSATION_HISTORY_TOKENS;`
  - `int effectiveUsed = usedTokens - FIXED_OVERHEAD;`
  - Keep `return Math.min(100, Math.max(0, effectiveUsed * 100 / usableContext));`
- Remove or update the `OVERHEAD_TOKENS` constant accordingly
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`

### Job 2: Add/update unit tests
- Add regression test: `scaleContextPercent(37_500, 200_000)` returns 14 (not 1)
- Verify boundary: `scaleContextPercent(13_500, 200_000)` returns 0 (at fixed overhead, none of
  user content used)
- Verify boundary: `scaleContextPercent(200_000, 200_000)` returns 100
- Verify clamp: `scaleContextPercent(0, 200_000)` returns 0
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java`

## Post-conditions
- [ ] `scaleContextPercent(37_500, 200_000)` returns 14
- [ ] `scaleContextPercent(13_500, 200_000)` returns 0
- [ ] `scaleContextPercent(200_000, 200_000)` returns 100
- [ ] `scaleContextPercent(0, 200_000)` returns 0
- [ ] All unit tests pass (`mvn -f client/pom.xml verify -e`)
- [ ] E2E: Statusline displays ~14% context usage in a session with ~37.5k tokens used
