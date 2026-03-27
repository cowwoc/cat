# Plan

## Goal

Fix NullPointerException in StatuslineCommand by moving closed field from all 7 concrete subclasses to AbstractJvmScope - eliminates initialization order bug and removes duplicate boilerplate

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: StatuslineCommand no longer throws NullPointerException when invoked with valid JSON input
- [ ] Regression test added: Test that exercises the statusline initialization path and verifies no NPE occurs
- [ ] No new issues: All existing tests continue to pass
- [ ] E2E verification: Run statusline-command binary with sample JSON input and verify it produces formatted output without errors
- [ ] The `closed` field is declared in AbstractJvmScope, not in any concrete subclass
- [ ] None of the 7 concrete subclasses contain their own `closed` field, `isClosed()` method, or `close()` method
- [ ] AbstractJvmScope's `closed` field is initialized at field declaration so it is available before any subclass constructor runs
