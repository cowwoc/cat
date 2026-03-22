# Add Batch Error Scenario Tests for download-git-filter-repo.sh

## Goal
Extend `tests/download-git-filter-repo.bats` to cover scenarios where multiple SHA256 validation
errors occur simultaneously (batch failures), verifying that the script reports all errors at once
rather than stopping at the first failure.

## Background
The iteration-2 fix to `download-git-filter-repo.sh` changed SHA256 field validation to collect
all errors in `VALIDATION_ERRORS` and report them all at once instead of failing on the first
error. Tests added in the fix iteration cover single-field failures. Batch scenarios (multiple
missing/malformed fields simultaneously) are not yet covered.

## Changes Required

1. Add Bats test: all platform SHA256 fields missing simultaneously — expects error listing all
   missing platforms.
2. Add Bats test: mix of missing and malformed SHA256 fields — expects error listing both types of
   failures in a single report.
3. Add Bats test: exactly one valid field, rest invalid — verifies partial-valid input triggers
   batch error for the invalid ones.
4. Each test must use mktemp isolation and stub external commands via PATH override per the existing
   test pattern in `tests/download-git-filter-repo.bats`.

## Post-conditions

- [ ] At least 3 new Bats tests covering batch SHA256 validation error scenarios
- [ ] Tests use mktemp isolation and PATH-based stubs consistent with existing test style
- [ ] All new and existing Bats tests pass
- [ ] No regressions introduced
