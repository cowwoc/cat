# Plan

## Goal

Prefix statusline error messages with the originating class name so that when an error is displayed, the user can immediately identify which class produced the error.

## Pre-conditions

(none)

## Post-conditions

- [ ] Error message semantics preserved (same errors reported, class name prefix added)
- [ ] All statusline error messages include the originating class name as a prefix
- [ ] Tests passing, no regressions
- [ ] Code quality improved (consistent error message format across statusline classes)
- [ ] E2E verification: Trigger a statusline error condition and confirm the message includes the originating class name as a prefix
