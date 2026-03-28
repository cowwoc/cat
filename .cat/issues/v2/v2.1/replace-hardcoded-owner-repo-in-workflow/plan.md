# Replace Hardcoded Owner/Repo References in CI Workflow

## Goal
Replace hardcoded `cowwoc/cat` repository references in `plugin/scripts/download-git-filter-repo.sh`
with values read from `plugin/.git-filter-repo-config/release.conf`, so the download script is
portable across forks and repository renames.

## Background
`plugin/scripts/download-git-filter-repo.sh` hardcodes `REPO_OWNER="cowwoc"` and `REPO_NAME="cat"`
when constructing the GitHub release download URL. Investigation confirmed `.github/workflows/build-git-filter-repo.yml`
does NOT contain hardcoded owner/repo references — it already uses GitHub Actions context variables correctly.

The fix moves the owner/repo values into `release.conf` alongside the existing `RELEASE_TAG` and
`PLATFORM_SHA256_*` fields, so forks that build their own binaries can update all release
configuration in one place.

## Research Findings
- `.github/workflows/build-git-filter-repo.yml` uses `softprops/action-gh-release` which defaults to
  the current repository — no hardcoded owner/repo present, no changes needed.
- `plugin/scripts/download-git-filter-repo.sh:174-175` has `REPO_OWNER="cowwoc"` and `REPO_NAME="cat"`.
- `plugin/.git-filter-repo-config/release.conf` already contains `RELEASE_TAG` and `PLATFORM_SHA256_*`
  — the natural place to add `REPO_OWNER` and `REPO_NAME`.

## Changes Required

### 1. `plugin/.git-filter-repo-config/release.conf`

Add these fields immediately before `RELEASE_TAG`:

```conf
REPO_OWNER="cowwoc"
REPO_NAME="cat"
```

Update the "How to update" comment block (line ~13) to add:
```
#   0. Update REPO_OWNER and REPO_NAME if building from a fork (optional for original repo).
```

### 2. `plugin/scripts/download-git-filter-repo.sh`

Remove these lines (~174-175):
```bash
REPO_OWNER="cowwoc"
REPO_NAME="cat"
```

Replace with parsing from `release.conf` (similar to the existing `RELEASE_TAG` parsing pattern):
```bash
REPO_OWNER=$(grep -E '^REPO_OWNER=' "${CONF}" | sed 's/^REPO_OWNER="\(.*\)"$/\1/' | head -1 || true)
if [[ -z "${REPO_OWNER}" ]]; then
  echo "ERROR: REPO_OWNER not found in ${CONF}" >&2
  exit 1
fi
if ! echo "${REPO_OWNER}" | grep -qE '^[a-zA-Z0-9_-]+$'; then
  echo "ERROR: REPO_OWNER in ${CONF} has unexpected format: ${REPO_OWNER}" >&2
  exit 1
fi
REPO_NAME=$(grep -E '^REPO_NAME=' "${CONF}" | sed 's/^REPO_NAME="\(.*\)"$/\1/' | head -1 || true)
if [[ -z "${REPO_NAME}" ]]; then
  echo "ERROR: REPO_NAME not found in ${CONF}" >&2
  exit 1
fi
if ! echo "${REPO_NAME}" | grep -qE '^[a-zA-Z0-9_-]+$'; then
  echo "ERROR: REPO_NAME in ${CONF} has unexpected format: ${REPO_NAME}" >&2
  exit 1
fi
```

## Post-conditions

- [ ] No hardcoded `REPO_OWNER="cowwoc"` or `REPO_NAME="cat"` in `plugin/scripts/download-git-filter-repo.sh`
- [ ] `plugin/.git-filter-repo-config/release.conf` contains `REPO_OWNER` and `REPO_NAME` fields
- [ ] The download script reads `REPO_OWNER` and `REPO_NAME` from `release.conf`
- [ ] Download script fails with a clear error when `REPO_OWNER` or `REPO_NAME` is missing or malformed
- [ ] No regressions in `plugin/scripts/download-git-filter-repo.sh` functionality

## Jobs

### Job 1
- In `plugin/.git-filter-repo-config/release.conf`: add `REPO_OWNER="cowwoc"` and `REPO_NAME="cat"` fields before `RELEASE_TAG`, and add the step-0 note to the update comment block
- In `plugin/scripts/download-git-filter-repo.sh`: remove hardcoded `REPO_OWNER` and `REPO_NAME` lines (~174-175) and add parsing code that reads them from `release.conf` using the same grep/sed pattern as `RELEASE_TAG` parsing, with fail-fast validation
- Update `.cat/issues/v2/v2.1/replace-hardcoded-owner-repo-in-workflow/index.json` with `status: "closed"` and `progress: 100`
- Commit type: `bugfix:`
