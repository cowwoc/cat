# Validate and Enforce Release Artifact Ordering in CI Manifest

## Goal
Add explicit ordering guarantees to the SHA256 manifest generation step in
`.github/workflows/build-git-filter-repo.yml` so that the manifest file lists platform entries in
a consistent, deterministic order regardless of which matrix jobs complete first.

## Background
The `build-git-filter-repo.yml` workflow builds binaries in parallel matrix jobs and then assembles
a SHA256 manifest. Matrix job completion order is non-deterministic (depends on runner availability),
so the manifest may be generated in different orders across runs. This produces unnecessary diffs and
makes the manifest harder to review.

## Changes Required

1. In the manifest assembly step of `build-git-filter-repo.yml`, sort the platform entries before
   writing the final manifest file (e.g., using `sort` or explicit ordering).
2. Document the expected manifest ordering in a comment in the workflow file.
3. Optionally add a CI validation step that verifies the manifest is in sorted order as a
   post-condition check.

## Post-conditions

- [ ] SHA256 manifest entries are always written in the same deterministic order
- [ ] The ordering logic is documented in the workflow file
- [ ] Two consecutive runs with the same inputs produce identical manifest files
- [ ] No regressions introduced to the build or download flow
