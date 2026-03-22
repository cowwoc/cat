# Replace Hardcoded Owner/Repo References in CI Workflow

## Goal
Replace hardcoded `cowwoc/cat` repository references in `.github/workflows/build-git-filter-repo.yml`
with GitHub Actions context variables (`github.repository_owner` and `github.event.repository.name`)
so the workflow is portable across forks and repository renames.

## Background
The `build-git-filter-repo.yml` workflow contains hardcoded `cowwoc/cat` strings (e.g., in download
URL construction or artifact publishing steps). This prevents the workflow from working correctly in
forks or after a repository rename without manual edits.

## Changes Required

1. Identify all occurrences of `cowwoc/cat` (or `cowwoc` alone) in
   `.github/workflows/build-git-filter-repo.yml`.
2. Replace hardcoded owner with `${{ github.repository_owner }}`.
3. Replace hardcoded repo name with `${{ github.event.repository.name }}`.
4. Verify the replacement works for the artifact upload/download steps and any API calls.
5. Update `plugin/scripts/download-git-filter-repo.sh` if it also contains hardcoded owner/repo
   references used for downloading release artifacts.

## Post-conditions

- [ ] No hardcoded `cowwoc/cat` or `cowwoc` strings remain in the workflow or download script
   (except in comments where explaining the original repository)
- [ ] The workflow uses GitHub Actions context variables for owner and repo name
- [ ] The workflow is tested in a fork to confirm portability
- [ ] No regressions introduced
