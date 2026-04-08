# Plan: fix-statusline-context-percent-calculation

## Goal
Replace the fixed 83.5%-ceiling heuristic in `StatuslineCommand.scaleContextPercent()` with a
model-aware calculation that scales used tokens against the usable context window (total context
minus fixed overhead).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Call sites pass a raw percentage today; the new method needs token counts and the
  model display name. Three call sites must be updated.
- **Mitigation:** All changes are confined to `StatuslineCommand.java`.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` - replace
  `scaleContextPercent(int usedPercentage)` with a model-aware overload and update 3 call sites

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Replace scaleContextPercent with model-aware implementation
- Parse context size from model display name:
  - Display name contains `"(1M context)"` → `totalContext = 1_000_000`
  - Otherwise → `totalContext = 200_000`
- Define fixed overhead constant: `OVERHEAD_TOKENS = 6_400 + 7_100 + 21_000` (= 34,500)
- Define `usableContext = totalContext - OVERHEAD_TOKENS`
- New private method signature:
  `private static int scaleContextPercent(int usedTokens, int totalContext)`
- Implementation:
  `int effectiveUsed = usedTokens - OVERHEAD_TOKENS;`
  `return Math.min(100, Math.max(0, effectiveUsed * 100 / usableContext));`
- Update the 3 call sites (lines 154, 354, 386) to pass `usedTokens` and `totalContext` derived
  from the model display name instead of the raw percentage
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`

### Job 2: Add/update unit tests
- Add tests for 200k model: 0 tokens used → 0%, overhead exactly used → 0%, full context → 100%,
  midpoint → expected scaled value
- Add tests for 1M context model: same boundary cases at 1M scale
- Verify negative effective-used (below overhead) clamps to 0%
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/util/StatuslineCommandTest.java`
    (create if it doesn't exist)

## Post-conditions
- [ ] `scaleContextPercent` no longer uses the 83.5% ceiling heuristic
- [ ] A model display name containing `"(1M context)"` maps to a 1,000,000-token context size;
  all other names map to 200,000
- [ ] Fixed overhead is exactly 34,500 tokens (6,400 + 7,100 + 21,000)
- [ ] When used tokens ≤ overhead, the method returns 0
- [ ] When used tokens = totalContext, the method returns 100
- [ ] All unit tests pass (`mvn -f client/pom.xml verify -e`)
