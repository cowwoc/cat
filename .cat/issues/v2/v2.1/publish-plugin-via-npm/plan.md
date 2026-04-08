# Plan: publish-plugin-via-npm

## Goal
Replace GitHub Release artifact distribution with npm publishing. End users install the plugin from npm
(with platform-specific sub-packages for jlink binaries), while local development continues using the
existing local marketplace + `/cat-update-client` workflow.

## Parent Requirements
None

## Current State
- CI (`build-jlink-bundle.yml`) builds jlink bundles and uploads them as GitHub Release artifacts
- `session-start.sh` downloads the correct platform artifact on first use (curl + SHA256 verification)
- `marketplace.json` uses `"source": "./plugin"` (local relative path)
- End users install via `/plugin marketplace add cowwoc/cat` which clones the repo
- `/cat-update-client` builds locally and copies jlink into the plugin cache

## Target State
- `marketplace.json` uses `"source": "npm"` pointing to the main npm package on GitHub Packages registry (`npm.pkg.github.com`)
- Main npm package declares `optionalDependencies` with `os`/`cpu` fields for platform-specific jlink bundles
- A manually-invoked CI workflow builds jlink bundles and publishes all npm packages to GitHub Packages
- `session-start.sh` no longer downloads artifacts at runtime — binaries arrive via npm install
- `/cat-update-client` uses `.cat/work/local-plugin/` as a temp local marketplace for local dev installs
- GitHub Release artifact upload and download logic is removed
- GitHub Packages allows deleting and re-publishing the same version, enabling iterative testing

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  - npm's `optionalDependencies` with `os`/`cpu` must be verified to work through Claude Code's plugin install
  - Existing users on GitHub artifact flow need migration path
  - GitHub Packages npm registry requires authentication for installs of `@scope` packages (end users need a PAT or `.npmrc` config)
- **Mitigation:**
  - Empirically verified that `claude plugin install` from a local marketplace with symlinks works
  - Migration: first release publishes to npm; session-start detects npm-installed binaries and skips download
  - Use `GITHUB_TOKEN` (automatic in Actions) for publishing to GitHub Packages
  - GitHub Packages supports version deletion and re-publish, enabling safe iteration during development

## Files to Modify

### npm package structure (new files)
- `plugin/package.json` — add `optionalDependencies` for platform sub-packages, configure `files` for npm publish, set `publishConfig.registry` to `https://npm.pkg.github.com`
- `npm/cat-linux-x64/package.json` — platform sub-package (os: linux, cpu: x64), scoped as `@cowwoc/cat-linux-x64`
- `npm/cat-linux-arm64/package.json` — platform sub-package (os: linux, cpu: arm64), scoped as `@cowwoc/cat-linux-arm64`
- `npm/cat-darwin-x64/package.json` — platform sub-package (os: darwin, cpu: x64), scoped as `@cowwoc/cat-darwin-x64`
- `npm/cat-darwin-arm64/package.json` — platform sub-package (os: darwin, cpu: arm64), scoped as `@cowwoc/cat-darwin-arm64`

### CI workflows
- `.github/workflows/publish-npm.yml` — new manually-invoked workflow: build jlink bundles, pack platform sub-packages, publish all to npm
- `.github/workflows/build-jlink-bundle.yml` — remove GitHub Release upload steps (keep build matrix for use by publish-npm)

### Plugin source
- `.claude-plugin/marketplace.json` — change source from `"./plugin"` to `{ "source": "npm", "package": "@cowwoc/claude-code-cat", "registry": "https://npm.pkg.github.com" }`
- `plugin/hooks/session-start.sh` — remove GitHub artifact download logic (platform detection, curl, SHA256 verification, lock acquisition)

### Local development
- `.claude/skills/cat-update-client/SKILL.md` — update to use `.cat/work/local-plugin/` marketplace for local installs
- `.cat/work/local-plugin/.claude-plugin/marketplace.json` — local marketplace pointing to `./plugin` (already created)
- `.cat/work/local-plugin/plugin` — symlink to `/workspace/plugin` (already created)

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Create npm package structure
- Create platform sub-package directories under `npm/` with `package.json` files containing `os` and `cpu` fields
- Update `plugin/package.json` with `optionalDependencies` referencing the platform sub-packages
- Configure `files` field to control what gets published
  - Files: `npm/cat-linux-x64/package.json`, `npm/cat-linux-arm64/package.json`, `npm/cat-darwin-x64/package.json`, `npm/cat-darwin-arm64/package.json`, `plugin/package.json`

### Job 2: Create npm publish CI workflow
- Create `.github/workflows/publish-npm.yml` with `workflow_dispatch` trigger
- Reuse the jlink build matrix from `build-jlink-bundle.yml`
- After build, copy jlink output into each platform sub-package directory
- Run `npm publish` for each sub-package, then for the main package
- Use `GITHUB_TOKEN` (automatic in GitHub Actions) for authentication to GitHub Packages
  - Files: `.github/workflows/publish-npm.yml`, `.github/workflows/build-jlink-bundle.yml`

### Job 3: Update marketplace.json for npm source
- Change `.claude-plugin/marketplace.json` plugin source to npm
  - Files: `.claude-plugin/marketplace.json`

### Job 4: Remove session-start download logic
- Remove platform detection, artifact URL construction, curl download, SHA256 verification, and lock acquisition from `session-start.sh`
- Keep the jlink runtime detection and Java `SessionStartHook` invocation
  - Files: `plugin/hooks/session-start.sh`

### Job 5: Update /cat-update-client for local dev
- Update skill to create/use `.cat/work/local-plugin/` marketplace for local installs instead of installing from the repo-root marketplace
- Flow: build jlink → register temp local marketplace → install from it → remove marketplace → copy jlink into cache
  - Files: `.claude/skills/cat-update-client/SKILL.md`

## Post-conditions
- [ ] `marketplace.json` references `@cowwoc/claude-code-cat` on GitHub Packages as source
- [ ] Platform sub-packages have correct `os`/`cpu` fields in their `package.json`
- [ ] `publish-npm.yml` workflow exists with `workflow_dispatch` trigger
- [ ] `session-start.sh` no longer contains artifact download logic
- [ ] `/cat-update-client` installs via `.cat/work/local-plugin/` local marketplace
- [ ] E2E: `claude plugin install` from the local marketplace succeeds and plugin loads correctly
