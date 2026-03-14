# Plan: fix-github-actions-sha-pinning

## Problem

All `.github/workflows/` files use mutable version tags (e.g., `@v4`, `@v2`) to reference GitHub Actions.
A supply-chain attack that compromises or hijacks an action's tag would silently inject malicious code into
the CI/CD pipeline. This is a pre-existing security gap identified during stakeholder review of
2.1-update-github-actions-versions.

The four workflow files affected:
- `.github/workflows/pages.yml` — 4 actions
- `.github/workflows/integration-tests.yml` — 3 actions
- `.github/workflows/build-jlink-bundle.yml` — 5 actions
- `.github/workflows/build-jdk-bundle.yml` — 5 actions

Actions currently using mutable tags:
| Action | Tag | Files |
|--------|-----|-------|
| `actions/checkout` | `@v4` | all 4 files |
| `actions/setup-java` | `@v4` | integration-tests.yml, build-jlink-bundle.yml, build-jdk-bundle.yml |
| `actions/upload-artifact` | `@v4` | integration-tests.yml, build-jlink-bundle.yml, build-jdk-bundle.yml |
| `actions/download-artifact` | `@v4` | build-jlink-bundle.yml, build-jdk-bundle.yml |
| `actions/configure-pages` | `@v4` | pages.yml |
| `actions/upload-pages-artifact` | `@v4` | pages.yml |
| `actions/deploy-pages` | `@v4` | pages.yml |
| `softprops/action-gh-release` | `@v2` | build-jlink-bundle.yml, build-jdk-bundle.yml |

## Parent Requirements

None (security maintenance)

## Approaches

### A: Manual SHA pinning with Dependabot automation — chosen

Look up each action's current tag SHA via the GitHub API or public registry, then replace the mutable tag
with the full 40-character commit SHA and add a comment with the version tag. Configure Dependabot for the
`github-actions` ecosystem to automatically open PRs when SHAs need updating.

**Pros:** Fully immutable references; Dependabot keeps them current automatically.
**Cons:** Verbose workflow files; PRs require human review.

### B: SHA pinning without automation

Same as A but without Dependabot. **Rejected**: SHAs would become stale and require manual maintenance.

### C: Stay on mutable version tags (status quo)

**Rejected**: Supply-chain risk; explicitly flagged by security stakeholder.

## Research Findings

GitHub recommends pinning actions to full commit SHAs for supply-chain security. The Dependabot `github-actions`
ecosystem supports SHA pinning and will automatically open PRs to update pinned SHAs when new versions are
released. The comment `# vX.Y.Z` preserves human readability.

SHA lookup process: For each action, use the GitHub API endpoint:
`https://api.github.com/repos/{owner}/{repo}/git/ref/tags/{tag}` to resolve a tag to a commit SHA.
For lightweight tags that point directly to a commit, use the `sha` field directly.
For annotated tags (which point to a tag object), follow the `url` to the tag object and extract the `object.sha`.

The implementation subagent must:
1. Look up the current SHA for each action tag using WebFetch or WebSearch on the GitHub API
2. Verify each SHA is a valid 40-character hex string before inserting it
3. Add `# vX.Y.Z` comment after the SHA

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None to runtime behavior. The workflow will run the exact same code as before.
- **Mitigation:** Any SHA that doesn't match the expected tag indicates a potential supply-chain attack and should
  block the PR. Post-merge E2E verification via CI run confirms workflows still execute correctly.

## Files to Modify

- `.github/workflows/pages.yml` — pin 4 action references
- `.github/workflows/integration-tests.yml` — pin 3 action references
- `.github/workflows/build-jlink-bundle.yml` — pin 5 action references
- `.github/workflows/build-jdk-bundle.yml` — pin 5 action references
- `.github/dependabot.yml` — add or update `package-ecosystem: github-actions` configuration

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

SHA lookup procedure for each action: Use WebFetch on
`https://api.github.com/repos/{owner}/{repo}/git/ref/tags/{tag}` (e.g.,
`https://api.github.com/repos/actions/checkout/git/ref/tags/v4`). If the returned `object.type` is `"commit"`,
use `object.sha` directly. If `object.type` is `"tag"` (annotated tag), follow `object.url` and use the
`object.sha` from that response. Verify the result is a 40-character hex string before using it.

The replacement format on each `uses:` line is: `uses: {owner}/{repo}@{40-char-SHA}  # {tag-name}`
where `{tag-name}` is the tag resolved (e.g., `v4` resolves to the latest patch, such as `v4.2.2` — use
the exact tag name returned by listing tags for that action if needed, or use `v4` if only major tag is known).
Example: `uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4`

- Look up SHA for `actions/checkout@v4` (repo: `actions/checkout`, tag: `v4`). Replace every `uses: actions/checkout@v4`
  line in all 4 files (`.github/workflows/pages.yml`, `.github/workflows/integration-tests.yml`,
  `.github/workflows/build-jlink-bundle.yml`, `.github/workflows/build-jdk-bundle.yml`) with
  `uses: actions/checkout@{SHA}  # v4`
- Look up SHA for `actions/setup-java@v4` (repo: `actions/setup-java`, tag: `v4`). Replace every
  `uses: actions/setup-java@v4` line in `.github/workflows/integration-tests.yml`,
  `.github/workflows/build-jlink-bundle.yml`, `.github/workflows/build-jdk-bundle.yml` with
  `uses: actions/setup-java@{SHA}  # v4`
- Look up SHA for `actions/upload-artifact@v4` (repo: `actions/upload-artifact`, tag: `v4`). Replace every
  `uses: actions/upload-artifact@v4` line in `.github/workflows/integration-tests.yml`,
  `.github/workflows/build-jlink-bundle.yml`, `.github/workflows/build-jdk-bundle.yml` with
  `uses: actions/upload-artifact@{SHA}  # v4`

### Wave 2

Use the same SHA lookup procedure as Wave 1.

- Look up SHA for `actions/download-artifact@v4` (repo: `actions/download-artifact`, tag: `v4`). Replace every
  `uses: actions/download-artifact@v4` line in `.github/workflows/build-jlink-bundle.yml`,
  `.github/workflows/build-jdk-bundle.yml` with `uses: actions/download-artifact@{SHA}  # v4`
- Look up SHA for `actions/configure-pages@v4` (repo: `actions/configure-pages`, tag: `v4`). Replace
  `uses: actions/configure-pages@v4` in `.github/workflows/pages.yml` with
  `uses: actions/configure-pages@{SHA}  # v4`
- Look up SHA for `actions/upload-pages-artifact@v4` (repo: `actions/upload-pages-artifact`, tag: `v4`). Replace
  `uses: actions/upload-pages-artifact@v4` in `.github/workflows/pages.yml` with
  `uses: actions/upload-pages-artifact@{SHA}  # v4`
- Look up SHA for `actions/deploy-pages@v4` (repo: `actions/deploy-pages`, tag: `v4`). Replace
  `uses: actions/deploy-pages@v4` in `.github/workflows/pages.yml` with
  `uses: actions/deploy-pages@{SHA}  # v4`
- Look up SHA for `softprops/action-gh-release@v2` (repo: `softprops/action-gh-release`, tag: `v2`). Replace
  every `uses: softprops/action-gh-release@v2` line in `.github/workflows/build-jlink-bundle.yml`,
  `.github/workflows/build-jdk-bundle.yml` with `uses: softprops/action-gh-release@{SHA}  # v2`

### Wave 3

- `.github/dependabot.yml` does not currently exist. Create it with the following exact content:
  ```yaml
  version: 2
  updates:
    - package-ecosystem: "github-actions"
      directory: "/"
      schedule:
        interval: "weekly"
  ```
- Verify all 4 workflow files contain no remaining mutable tag references by running:
  `grep -rn '@v[0-9]' .github/workflows/` — the output must be empty (exit code 1 or no lines matched)
- Update STATE.md: status: closed, progress: 100%

## Post-conditions

- [ ] All GitHub Actions in all 4 workflow files are pinned to full 40-character commit SHAs with a
  version comment (e.g., `@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2`)
- [ ] No mutable version tags (`@v4`, `@v3`, `@v2`, etc.) remain in any `.github/workflows/` file
  (verified by grep)
- [ ] `.github/dependabot.yml` includes a `github-actions` package-ecosystem entry with weekly update schedule
- [ ] E2E: GitHub Actions workflows execute successfully after SHA pinning (verified via CI run on the
  merged branch)
