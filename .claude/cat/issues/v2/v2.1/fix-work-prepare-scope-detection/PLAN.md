# Plan: Fix WorkPrepare Bare Name Scope Detection

## Goal

Ensure bare issue names (e.g., 'fix-bug') correctly detect as Scope.BARE_NAME and trigger scope resolution, while qualified names (e.g., '2.1-fix-bug') detect as Scope.ISSUE.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The scope detection logic is critical for issue resolution; incorrect detection could cause bare names to fail resolution
- **Mitigation:** Write TDD tests that verify both bare and qualified name detection

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - scope detection logic at lines 211-220
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` - add test for scope detection

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Write failing test** - Create a test that verifies bare name 'fix-bug' produces Scope.BARE_NAME
2. **Verify test fails** - Confirm the test currently fails, proving it catches the bug
3. **Fix the code** - Update WorkPrepare.java scope detection if needed
4. **Verify test passes** - Run the test to confirm the fix works
5. **Run full test suite** - Ensure no regressions

## Post-conditions

- Test for bare name scope detection passes
- Test for qualified name scope detection passes
- All existing tests still pass
