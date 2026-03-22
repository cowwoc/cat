# Automate git-filter-repo Upstream Release Detection

## Goal
Add automation to monitor the upstream git-filter-repo repository for new releases and automatically
trigger the binary rebuild workflow in `.github/workflows/build-git-filter-repo.yml`, eliminating
the need for manual `workflow_dispatch` to pick up new versions.

## Background
The current `build-git-filter-repo.yml` workflow only runs on manual `workflow_dispatch` trigger.
When git-filter-repo releases a new version upstream, maintainers must manually detect the release
and trigger the workflow. This creates a maintenance burden and risks running outdated binaries.

## Changes Required

1. Add a scheduled or repository-dispatch trigger to `build-git-filter-repo.yml` that checks the
   upstream newren/git-filter-repo repository for new releases (e.g., via GitHub API or git tag
   comparison).
2. Add a separate "check-upstream" job or a reusable workflow that:
   - Fetches the latest release tag from `https://github.com/newren/git-filter-repo/releases/latest`
   - Compares it to the last-built version (stored as a tag or file in this repository)
   - Triggers the build workflow only when a new version is available
3. Update `plugin/scripts/download-git-filter-repo.sh` or associated documentation to reflect
   the automated update mechanism.

## Post-conditions

- [ ] A scheduled/automated trigger exists to detect new upstream git-filter-repo releases
- [ ] The build workflow runs automatically when a new upstream release is detected
- [ ] No manual `workflow_dispatch` required for routine version updates
- [ ] The mechanism is documented in `build-git-filter-repo.yml` comments
- [ ] No regressions introduced to existing manual trigger support
