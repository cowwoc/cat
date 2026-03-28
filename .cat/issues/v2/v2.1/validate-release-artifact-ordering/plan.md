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

Currently the workflow creates individual per-platform `.sha256` files but does not produce a combined
`SHA256SUMS.txt` manifest. This issue adds that combined manifest with deterministic ordering.

## Changes Required

1. In the `release` job, add a "Create combined SHA256 manifest" step (after "Read SHA256 checksums")
   that builds `artifacts/SHA256SUMS.txt` with entries sorted alphabetically by platform name.
2. Add a "Verify manifest ordering" step after creation as a post-condition CI check.
3. Add `artifacts/SHA256SUMS.txt` to the files list in the "Create release" step.
4. Document the expected manifest ordering in a comment in the new step.

## Research Findings

Each per-platform `.sha256` file contains the bare binary name `git-filter-repo` (not the platform
name), because the checksum is generated in the `build` job before the binary is renamed. The combined
manifest must therefore use the platform-qualified name. The correct approach is:
- Read the hash from each `.sha256` file using `awk '{print $1}'`
- Pair each hash with its platform-qualified binary name (e.g., `git-filter-repo-linux-x64`)
- Sort alphabetically by filename column to produce deterministic order:
  `linux-aarch64`, `linux-x64`, `macos-aarch64`, `macos-x64`

## Post-conditions

- [ ] SHA256 manifest entries are always written in the same deterministic order
- [ ] The ordering logic is documented in the workflow file
- [ ] Two consecutive runs with the same inputs produce identical manifest files
- [ ] No regressions introduced to the build or download flow

## Jobs

### Job 1
- Modify `.github/workflows/build-git-filter-repo.yml` in the `release` job:
  1. After the "Read SHA256 checksums" step (line ~195), add a "Create combined SHA256 manifest" step:
     ```yaml
     - name: Create combined SHA256 manifest
       run: |
         set -euo pipefail
         # Combine all platform SHA256 checksums into a single manifest file.
         # Entries are sorted alphabetically by platform filename for deterministic ordering
         # regardless of which matrix jobs complete first.
         # Expected sorted order: linux-aarch64, linux-x64, macos-aarch64, macos-x64
         {
           printf '%s  %s\n' "$(awk '{print $1}' artifacts/git-filter-repo-linux-aarch64.sha256)" \
             "git-filter-repo-linux-aarch64"
           printf '%s  %s\n' "$(awk '{print $1}' artifacts/git-filter-repo-linux-x64.sha256)" \
             "git-filter-repo-linux-x64"
           printf '%s  %s\n' "$(awk '{print $1}' artifacts/git-filter-repo-macos-aarch64.sha256)" \
             "git-filter-repo-macos-aarch64"
           printf '%s  %s\n' "$(awk '{print $1}' artifacts/git-filter-repo-macos-x64.sha256)" \
             "git-filter-repo-macos-x64"
         } > artifacts/SHA256SUMS.txt
         cat artifacts/SHA256SUMS.txt
     ```
  2. After that, add a "Verify manifest ordering" step:
     ```yaml
     - name: Verify manifest ordering
       run: |
         set -euo pipefail
         # Verify that SHA256SUMS.txt entries are in sorted order (second column = filename).
         EXPECTED=$(sort -k2 artifacts/SHA256SUMS.txt)
         ACTUAL=$(cat artifacts/SHA256SUMS.txt)
         if [[ "$EXPECTED" != "$ACTUAL" ]]; then
           echo "ERROR: SHA256SUMS.txt entries are not in sorted order." >&2
           echo "Expected:" >&2
           echo "$EXPECTED" >&2
           echo "Actual:" >&2
           echo "$ACTUAL" >&2
           exit 1
         fi
         echo "OK: SHA256SUMS.txt entries are in sorted order."
     ```
  3. In the "Create release" step, add `artifacts/SHA256SUMS.txt` to the `files:` list (after the
     existing `.sha256` entries).
- Update index.json: set status to `closed`, progress to 100%.

## Success Criteria

- `artifacts/SHA256SUMS.txt` is created with exactly 4 entries (one per platform)
- Entries are sorted alphabetically: `linux-aarch64`, `linux-x64`, `macos-aarch64`, `macos-x64`
- "Verify manifest ordering" step passes (no exit code 1)
- `SHA256SUMS.txt` is attached to the GitHub release alongside the individual `.sha256` files
- `git diff` of the workflow shows only the three additions described above
