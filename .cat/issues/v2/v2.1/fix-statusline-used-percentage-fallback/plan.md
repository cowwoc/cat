# Plan: fix-statusline-used-percentage-fallback

## Problem
`StatuslineCommand` reads `context_window.used_tokens` from the JSON Claude Code sends, but Claude Code
does not provide that field. This causes the statusline to always display 0% context usage.

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
`scaleContextPercent(usedTokens, totalContext)`. Claude Code does not send `used_tokens`; the correct
fields are `context_window.current_usage.input_tokens` (raw input token count) and
`context_window.context_window_size` (total context size in tokens). Reference:
https://code.claude.com/docs/en/statusline#available-data

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Tests that use `used_tokens` directly must be updated to use the new fields
- **Mitigation:** `context_window.current_usage` is null before the first API call; clamp to 0

## Files to Modify
- `client/src/main/java/.../AbstractClaudeStatusline.java` — read `context_window.current_usage.input_tokens`
  as `usedTokens` and `context_window.context_window_size` as `totalContext` (replacing field rename + wrong path)
- `client/src/main/java/.../ClaudeStatusline.java` — add `getTotalContext()` to interface
- `client/src/main/java/.../StatuslineCommand.java` — use `scope.getTotalContext()` instead of
  `contextSizeFromDisplayName(displayName)`; remove `contextSizeFromDisplayName()` method
- Relevant test files — update JSON fixtures to use `context_window.current_usage.input_tokens` and
  `context_window.context_window_size` instead of `used_tokens`

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Fix JSON field reads and update tests
- In `AbstractClaudeStatusline.parseStatuslineJson()`:
  - Replace read of `context_window.used_tokens` with read of `context_window.current_usage.input_tokens`
    (null-safe: `current_usage` may be null before first API call → default 0)
  - Replace read of `context_window.context_window_size` → store as `totalContext`
    **Fail-fast:** if `context_window` is present but `context_window_size` is absent or non-positive,
    throw `IllegalArgumentException` with message:
    `"context_window.context_window_size is missing or non-positive in statusline JSON. Value: <value>"`
    **Fail-fast:** if `current_usage` is non-null but `input_tokens` is absent,
    throw `IllegalArgumentException` with message:
    `"context_window.current_usage.input_tokens is missing in statusline JSON"`
    **Default:** if `context_window` itself is absent (no context data at all), `totalContext = 0` and
    `usedTokens = 0` (graceful degradation for early session state; percentage will show 0%)
  - Files: `AbstractClaudeStatusline.java`
- Add `getTotalContext()` to `ClaudeStatusline` interface and implement in `AbstractClaudeStatusline`
  - Files: `ClaudeStatusline.java`, `AbstractClaudeStatusline.java`
- In `StatuslineCommand.execute()`: replace `contextSizeFromDisplayName(displayName)` with
  `scope.getTotalContext()`; if `getTotalContext() == 0`, skip `scaleContextPercent` and show 0%
- Remove `contextSizeFromDisplayName()` from `StatuslineCommand`
  - Files: `StatuslineCommand.java`
- Update JSON test fixtures to use `context_window.current_usage.input_tokens` and
  `context_window.context_window_size` instead of `context_window.used_tokens`
- Add a test verifying null `current_usage` (before first API call) shows 0%
  - Input: JSON with `context_window` present, `context_window_size` set to a positive value, `current_usage` null
  - Expected: output shows 0%
  - Files: `StatuslineCommandTest.java`, `TestClaudeStatusline.java` (if needed)
- Add a test verifying missing `context_window_size` throws `IllegalArgumentException`
  - Input: JSON with `context_window` present but `context_window_size` field absent
  - Expected: `IllegalArgumentException` with message containing `"missing or non-positive"`
  - Files: `AbstractClaudeStatuslineTest.java` (or equivalent unit test for `parseStatuslineJson`)
- Add a test verifying non-positive `context_window_size` (value = 0) throws `IllegalArgumentException`
  - Input: JSON with `context_window.context_window_size` set to `0`
  - Expected: `IllegalArgumentException` with message containing `"missing or non-positive"`
  - Files: `AbstractClaudeStatuslineTest.java`
- Add a test verifying non-positive `context_window_size` (value = -1) throws `IllegalArgumentException`
  - Input: JSON with `context_window.context_window_size` set to `-1`
  - Expected: `IllegalArgumentException` with message containing `"missing or non-positive"`
  - Files: `AbstractClaudeStatuslineTest.java`
- Add a test verifying missing `input_tokens` when `current_usage` is non-null throws `IllegalArgumentException`
  - Input: JSON with `context_window` present, `context_window_size` positive, `current_usage` non-null but
    `input_tokens` field absent
  - Expected: `IllegalArgumentException` with message containing `"missing"`
  - Files: `AbstractClaudeStatuslineTest.java`
- Run `mvn -f client/pom.xml verify -e` — all tests must pass
- Update `index.json`: status: closed, progress: 100%

## Post-conditions
- [ ] `AbstractClaudeStatusline` reads `context_window.current_usage.input_tokens` for `usedTokens`
- [ ] `AbstractClaudeStatusline` reads `context_window.context_window_size` for `totalContext`
- [ ] `ClaudeStatusline` exposes `getTotalContext()`
- [ ] `contextSizeFromDisplayName()` is removed from `StatuslineCommand`
- [ ] Null `current_usage` (before first API call) is handled gracefully (shows 0%)
- [ ] Present `context_window` with missing/non-positive `context_window_size` throws `IllegalArgumentException`
- [ ] Non-null `current_usage` with missing `input_tokens` throws `IllegalArgumentException`
- [ ] All existing tests pass with no regressions
- [ ] E2E: `echo '{"context_window":{"current_usage":{"input_tokens":50000},"context_window_size":200000},"workspace":{"project_dir":"/workspace"}}' | statusline-command` shows non-zero percentage
