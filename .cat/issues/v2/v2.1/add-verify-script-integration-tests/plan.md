## Type

feature

## Goal

Add integration tests for `plugin/scripts/verify-defer-plan-generation.sh`. The verify script currently has only static checks and no tests, so regressions in the verification logic go undetected.

## Pre-conditions

- `plugin/scripts/verify-defer-plan-generation.sh` exists and contains verification logic
- Bats test infrastructure is available in the project

## Post-conditions

- Integration tests verify each static check in the verify script passes on valid input
- Integration tests verify each static check in the verify script fails appropriately on invalid input
- Tests run in CI
- All new tests pass
- No regressions in existing verify script behavior
- E2E verification: run the verify script against a representative issue worktree and confirm all checks pass
