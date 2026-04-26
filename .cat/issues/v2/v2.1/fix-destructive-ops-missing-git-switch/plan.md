# Plan

## Goal

`DestructiveOps.java` (the UserPromptSubmit hook) detects `"git checkout"` in user prompts and injects a DESTRUCTIVE OPERATION DETECTED reminder, but the equivalent modern command `git switch` is not in the keyword list. Add `"git switch"` alongside `"git checkout"` so both trigger the verification reminder.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: `"git switch"` triggers the same DESTRUCTIVE OPERATION DETECTED warning as `"git checkout"`
- [ ] Regression test added to `DestructiveOps`-related test class
- [ ] No regressions in existing tests
- [ ] E2E verification passes
