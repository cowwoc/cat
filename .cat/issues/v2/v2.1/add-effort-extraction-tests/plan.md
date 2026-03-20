## Type

feature

## Goal

Add Bats tests for the EFFORT value extraction from config in `plugin/skills/work-implement-agent/first-use.md` around line 171, where the config parsing uses `grep`/`sed` patterns that are not tested. Tests must verify correct extraction for all valid values and proper error handling for invalid or missing values.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains EFFORT extraction from config using grep/sed at approximately line 171
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify EFFORT=low is correctly extracted from config
- Bats tests verify EFFORT=medium is correctly extracted from config
- Bats tests verify EFFORT=high is correctly extracted from config
- Bats tests verify appropriate error is raised when effort key is missing from config
- Bats tests verify appropriate error is raised when effort value is invalid
- All new Bats tests pass
- No regressions in existing work-implement-agent behavior
- E2E verification: confirm plan-builder is invoked with correct effort level based on config
