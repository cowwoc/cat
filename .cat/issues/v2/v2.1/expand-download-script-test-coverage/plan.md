# Expand Test Coverage for download-git-filter-repo.sh Edge Cases

## Goal
Add Bats test coverage for network failure scenarios in `plugin/scripts/download-git-filter-repo.sh`,
including curl timeout, partial download detection, and ensuring the script does not leave corrupt
partial files on failure.

## Background
The current test suite in `tests/download-git-filter-repo.bats` covers validation logic and basic
error cases but does not test network-level failures. A curl timeout or interrupted download could
leave a partial file that is then mistakenly treated as a valid cached binary on the next run.

## Changes Required

1. Add Bats test: curl exits with non-zero status (simulates network failure) — expects the script
   to fail with an error message and NOT leave a partial file at the target path.
2. Add Bats test: curl writes an incomplete file (simulated by writing fewer bytes than expected) —
   expects SHA256 verification to fail and the partial file to be removed.
3. Add Bats test: download succeeds but SHA256 mismatch on the downloaded binary — expects the
   script to fail and clean up the downloaded file.
4. Each test must use mktemp isolation and PATH-based stubs consistent with existing test style.

## Post-conditions

- [ ] At least 3 new Bats tests covering network failure and partial download scenarios
- [ ] The script removes partial/corrupt files on failure (or tests verify existing behavior)
- [ ] All new and existing Bats tests pass
- [ ] No regressions introduced
