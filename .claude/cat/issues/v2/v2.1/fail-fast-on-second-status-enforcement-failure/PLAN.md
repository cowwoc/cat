# Plan: Fail-Fast on Second Status Enforcement Failure

## Problem

`EnforceStatusOutput` is a Stop hook that blocks the assistant's response when `/cat:status` was invoked but the status
box is missing from the output. When the model fails to include the box and the hook blocks, the model retries. On the
retry Claude Code sets `stop_hook_active=true` to prevent infinite loops. The current code treats `stop_hook_active`
as a pass-through signal and returns `hookOutput.empty()`, letting the non-compliant response reach the user without
the status box.

## Satisfies

None

## Reproduction Code

```
1. User invokes /cat:status
2. Haiku model produces a text summary instead of the verbatim box
3. EnforceStatusOutput Stop hook fires: statusInvoked=true, hasBoxOutput=false → returns block
4. Haiku retries but again produces a text summary without the box
5. EnforceStatusOutput fires again with stop_hook_active=true
6. Hook returns hookOutput.empty() → non-compliant response reaches user
7. User sees no status box
```

## Expected vs Actual

- **Expected:** When `stop_hook_active=true` and the box is still missing, the hook blocks with a hard failure error
  explaining that the status output enforcement failed and the user should retry `/cat:status`.
- **Actual:** Hook silently lets the non-compliant response through, user sees no status box.

## Root Cause

The re-entrancy guard (`stop_hook_active`) was implemented as an unconditional pass-through to prevent infinite loops.
It should instead re-check whether the box is present: if the box is now present, allow the response; if still absent,
fail-fast with a clear error.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — the change only affects the `stop_hook_active=true` path which previously always
  returned empty.
- **Mitigation:** Unit tests cover both the "box now present on retry" and "box still missing on retry" cases.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` - Re-check box presence when
  `stop_hook_active=true`; fail-fast if still missing
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java` - Add tests for the
  stop_hook_active behavior (both cases)

## Test Cases

- [ ] `stopHookActiveWithBoxPresent`: `stop_hook_active=true`, transcript has box → returns empty (allow through)
- [ ] `stopHookActiveWithBoxMissing`: `stop_hook_active=true`, transcript has no box → returns block with
  fail-fast error message
- [ ] `firstAttemptWithBoxMissing`: `stop_hook_active=false`, box missing → returns block (existing behavior)
- [ ] `firstAttemptWithBoxPresent`: `stop_hook_active=false`, box present → returns empty (existing behavior)

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Write failing tests first (TDD) in `EnforceStatusOutputTest.java`:
  - `stopHookActiveWithBoxPresent`: build a mock transcript with `stop_hook_active=true` and box characters in
    the assistant message → assert result is empty (pass through)
  - `stopHookActiveWithBoxMissing`: build a mock transcript with `stop_hook_active=true` and no box in the
    assistant message → assert result is block containing a fail-fast error message
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceStatusOutputTest.java`
- Run `mvn -f client/pom.xml test -Dtest=EnforceStatusOutputTest` — new tests must FAIL (red)
- Modify `EnforceStatusOutput.main()` to change the `stop_hook_active` branch:
  - Instead of unconditionally returning `hookOutput.empty()`, call
    `checkTranscriptForStatusSkill(mapper, transcriptPath)` and check the result
  - If `result.statusInvoked && !result.hasBoxOutput`: return `hookOutput.block(...)` with fail-fast error
  - Otherwise: return `hookOutput.empty()`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- Run `mvn -f client/pom.xml test` — all tests must pass (exit code 0)

## Post-conditions

- [ ] `stopHookActiveWithBoxMissing` test passes: hook blocks on second failure
- [ ] `stopHookActiveWithBoxPresent` test passes: hook allows through when box is present on retry
- [ ] All existing `EnforceStatusOutputTest.java` tests pass
- [ ] No regressions: `mvn -f client/pom.xml test` exits with code 0
