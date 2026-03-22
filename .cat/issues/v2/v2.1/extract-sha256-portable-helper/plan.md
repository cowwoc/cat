# Extract sha256sum_portable into Shared Helper

## Goal
Extract the `sha256sum_portable()` function from `plugin/scripts/download-git-filter-repo.sh` into
a shared helper script in `plugin/scripts/` to eliminate duplication and ensure consistent
cross-platform SHA256 computation across all scripts.

## Background
The `sha256sum_portable()` function handles platform differences between `sha256sum` (Linux) and
`shasum -a 256` (macOS). This function may need to be used in other scripts. Having it duplicated
violates DRY and creates divergence risk when the function needs updating.

## Changes Required

1. Create `plugin/scripts/sha256sum-portable.sh` containing only the `sha256sum_portable` function
   with proper license header and `set -euo pipefail`.
2. Update `plugin/scripts/download-git-filter-repo.sh` to source the shared helper instead of
   defining the function inline.
3. Verify any other scripts in `plugin/scripts/` that compute SHA256 are updated to use the shared
   helper.
4. Add tests confirming the sourced helper works correctly on the target platforms.

## Post-conditions

- [ ] `sha256sum_portable()` is defined in exactly one place: `plugin/scripts/sha256sum-portable.sh`
- [ ] `download-git-filter-repo.sh` sources the shared helper instead of defining it inline
- [ ] No other script duplicates the sha256sum_portable logic
- [ ] Existing Bats tests for `download-git-filter-repo.sh` continue to pass
- [ ] No regressions introduced
