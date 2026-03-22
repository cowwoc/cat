# Enforce Binary Cache Coherency in Download Script

## Goal
Add version coherency checks to `plugin/scripts/download-git-filter-repo.sh` so that when a cached
binary is found, its version is verified to match the requested `RELEASE_TAG` before using it.

## Background
The current download script checks if a binary already exists and skips the download if so, but
does not verify that the cached binary matches the requested version. If the cache contains a binary
from a different release (e.g., due to a manual copy or a partial update), the script silently uses
the wrong version. This creates subtle correctness bugs.

## Changes Required

1. When a cached binary is found in `plugin/scripts/download-git-filter-repo.sh`, verify that its
   version matches the expected `RELEASE_TAG` (e.g., by checking an embedded version string or a
   companion `.version` file).
2. If the cached version does not match, delete the stale cache and re-download.
3. Add a Bats test case that verifies: when a binary with a mismatched version is cached, the script
   re-downloads the correct version rather than using the stale one.

## Post-conditions

- [ ] The download script verifies cached binary version before use
- [ ] A mismatched cached binary triggers re-download rather than silent reuse
- [ ] A Bats test covers the stale-cache scenario
- [ ] No regressions in existing Bats tests
- [ ] No regressions in the happy-path download flow
