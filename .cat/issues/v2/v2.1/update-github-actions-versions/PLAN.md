# Plan: update-github-actions-versions

## Current State

All 4 GitHub Actions workflow files use version-tagged action references. Most actions are at their latest major
version (v4), but `actions/upload-pages-artifact` remains at v3 while v4 has been available since August 2024.

## Target State

All GitHub Actions action references in all 4 workflow files are updated to their latest available major version tags.
Specifically, `actions/upload-pages-artifact` is updated from `@v3` to `@v4`. All other actions are confirmed at their
latest major version and require no changes.

## Parent Requirements

None (tech debt / maintenance)

## Research Findings

Current versions in use across all 4 workflows (`.github/workflows/`):

| Action | Current | Latest | Files |
|--------|---------|--------|-------|
| `actions/checkout` | v4 | v4 | integration-tests.yml, build-jlink-bundle.yml, build-jdk-bundle.yml, pages.yml |
| `actions/configure-pages` | v4 | v4 | pages.yml |
| `actions/deploy-pages` | v4 | v4 | pages.yml |
| `actions/download-artifact` | v4 | v4 | build-jlink-bundle.yml, build-jdk-bundle.yml |
| `actions/setup-java` | v4 | v4 | integration-tests.yml, build-jlink-bundle.yml, build-jdk-bundle.yml |
| `actions/upload-artifact` | v4 | v4 | integration-tests.yml, build-jlink-bundle.yml, build-jdk-bundle.yml |
| `actions/upload-pages-artifact` | **v3** | **v4** | pages.yml |
| `softprops/action-gh-release` | v2 | v2 | build-jlink-bundle.yml, build-jdk-bundle.yml |

`actions/upload-pages-artifact` v4.0.0 was released August 14, 2024. It requires `actions/deploy-pages@v4` or newer
(already in use). The key change in v4: hidden files (dotfiles) are excluded from artifacts. This does not affect the
`docs/` directory upload in `pages.yml` — no dotfiles in `docs/` are needed for the Pages deployment.

Pinning strategy: version tags only (e.g., `@v4`), not SHA pinning.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None to user-visible behavior. The v4 dotfile exclusion does not affect the `docs/` upload
  since no dotfiles in `docs/` are required for Pages deployment.
- **Mitigation:** Single-line change in one file. Straightforward find-and-replace. E2E verification via GitHub
  Actions run after merge.

## Files to Modify

- `.github/workflows/pages.yml` — change `actions/upload-pages-artifact@v3` to `actions/upload-pages-artifact@v4`
  (line 33)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `.github/workflows/pages.yml`: replace `uses: actions/upload-pages-artifact@v3` with
  `uses: actions/upload-pages-artifact@v4`
  - Files: `.github/workflows/pages.yml` (line 33)
- Update STATE.md to reflect completion
  - Files: `.cat/issues/v2.1/update-github-actions-versions/STATE.md`

## Post-conditions

- [ ] All 4 GitHub Actions workflow files (`integration-tests.yml`, `build-jlink-bundle.yml`, `build-jdk-bundle.yml`,
  `pages.yml`) have all actions at their latest available major version tags
- [ ] `pages.yml` uses `actions/upload-pages-artifact@v4` (was `@v3`)
- [ ] No other action version changes are present (all others were already at latest)
- [ ] E2E: Workflows execute successfully after the update (verified via GitHub Actions run on the merged branch)
