## Type

feature

## Goal

Add Bats tests for the temporary file cleanup logic in `plugin/skills/add/first-use.md` around line 956, where `PLAN_TEMP_FILE` cleanup on error paths is not tested. Tests must verify the temp file is cleaned up both on success and on error paths.

## Pre-conditions

- `plugin/skills/add/first-use.md` contains temp file creation and cleanup logic for `PLAN_TEMP_FILE`
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify `PLAN_TEMP_FILE` is removed on the successful completion path
- Bats tests verify `PLAN_TEMP_FILE` is removed when an error occurs during plan creation
- Bats tests verify no temp file leaks after any code path through the issue creation workflow
- All new Bats tests pass
- No regressions in existing add-agent behavior
- E2E verification: confirm no /tmp/plan-context-*.md files remain after issue creation
