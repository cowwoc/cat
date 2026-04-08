# Plan: fix-statusline-used-percentage-fallback

## Problem
`StatuslineCommand` reads `context_window.used_tokens` from the JSON Claude Code sends, but Claude Code
actually sends `context_window.used_percentage`. This causes the statusline to always display 0% context
usage.

## Parent Requirements
None

## Reproduction Code
```
# Run cat:statusline — context usage shows 0% even when context is partially consumed
```

## Expected vs Actual
- **Expected:** Statusline displays the actual context usage percentage
- **Actual:** Statusline displays 0% context usage because `used_tokens` is always 0 (field not present)

## Root Cause
`StatuslineCommand.execute()` reads `context_window.used_tokens` and passes it to
`scaleContextPercent(usedTokens, totalContext)`. Claude Code sends `context_window.used_percentage`
instead, so `used_tokens` is always 0.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Tests that use `used_percentage` must be updated to verify pass-through behavior
- **Mitigation:** Both fields handled with explicit branching; existing overhead-based calculation
  preserved when `used_tokens > 0`

## Files to Modify
- `client/src/main/java/.../ClaudeStatusline.java` - Add `getUsedPercentage()` to interface
- `client/src/main/java/.../AbstractClaudeStatusline.java` - Add `getUsedPercentage()` implementation
- `client/src/main/java/.../StatuslineCommand.java` - Branch on which field is present:
  when `used_tokens > 0` use existing `scaleContextPercent(usedTokens, totalContext)`;
  when only `used_percentage` available, display it directly without overhead calculation
- Relevant test files - Update tests that use `used_percentage` to verify correct pass-through behavior

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Add `getUsedPercentage()` to the `ClaudeStatusline` interface
  - Files: `client/src/main/java/.../ClaudeStatusline.java`
- Add `getUsedPercentage()` implementation to `AbstractClaudeStatusline`
  - Files: `client/src/main/java/.../AbstractClaudeStatusline.java`
- Update `StatuslineCommand.execute()` to branch on which field is present:
  when `used_tokens > 0`, apply existing `scaleContextPercent(usedTokens, totalContext)`;
  when only `used_percentage` is available, display it directly (no overhead calculation)
  - Files: `client/src/main/java/.../StatuslineCommand.java`
- Update tests that use `used_percentage` to verify correct pass-through behavior
  - Files: relevant test files

## Post-conditions
- [ ] Statusline displays correct context usage percentage when Claude Code sends `used_percentage`
- [ ] Statusline still applies overhead-based scaling when `used_tokens > 0`
- [ ] `getUsedPercentage()` added to `ClaudeStatusline` interface and `AbstractClaudeStatusline`
- [ ] All existing tests pass with no regressions
- [ ] E2E: Running the statusline command with a response containing only `used_percentage` displays
  that percentage directly
