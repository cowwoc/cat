# Plan: build-git-filter-repo-ci

## Goal
Create the GitHub Actions CI workflow (`build-git-filter-repo.yml`), trigger it to build standalone
git-filter-repo binaries for all 4 platforms (Linux x86_64, Linux ARM64, macOS Intel, macOS Apple Silicon),
collect the real SHA256 hashes, and update `plugin/.git-filter-repo-config/release.conf` with the hash values.

## Type
feature

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** CI workflow must run successfully on all 4 platforms
- **Mitigation:** Build output will be validated; CI logs available for debugging

## Files to Modify
- `.github/workflows/build-git-filter-repo.yml` (new) — CI workflow for PyInstaller binary builds
- `plugin/.git-filter-repo-config/release.conf` — update SHA256 hashes after CI completes

## Pre-conditions
- [ ] No other branches have this workflow file (will be deployed via this issue)

## Sub-Agent Waves

### Wave 1

Execute the following steps **in order**. Steps 2 and 3 are git/CLI operations with no file commits.

**Step 1: Create CI workflow file**
- Create `.github/workflows/build-git-filter-repo.yml` with the full workflow definition:
  - Build matrix for 4 platforms: `linux-x64`, `linux-aarch64`, `macos-x64`, `macos-aarch64`
  - PyInstaller to create standalone binaries
  - SHA256 checksum generation for each platform artifact
  - GitHub release creation with checksums in release notes
- Commit both the workflow file AND an index.json progress update together:
  - Workflow file: `.github/workflows/build-git-filter-repo.yml`
  - index.json: set `"progress": 50` (keep `"status": "open"`)
  - Commit message: `feature: add GitHub Actions CI workflow for git-filter-repo binary builds`

**Step 2: Push branch to GitHub remote**
- Run: `git push -u origin 2.1-build-git-filter-repo-ci`
- This is a git push only — no new commit needed

**Step 3: Trigger CI workflow and wait for completion**
- Trigger: `gh workflow run build-git-filter-repo.yml --ref 2.1-build-git-filter-repo-ci`
- Poll until complete: `gh run list --workflow=build-git-filter-repo.yml --limit 1 --json status,conclusion`
- Wait for all 4 platform jobs to succeed (status=completed, conclusion=success)
- If any job fails, report the failure and stop

**Step 4: Collect SHA256 hashes and update release.conf**
- Download the `.sha256` artifact files from the completed CI run using `gh run download`
- Update `plugin/.git-filter-repo-config/release.conf` with the real SHA256 values:
  - `RELEASE_TAG` — from the CI run's tag or release name
  - `PLATFORM_SHA256_linux_x64` — from `git-filter-repo-linux-x64.sha256`
  - `PLATFORM_SHA256_linux_aarch64` — from `git-filter-repo-linux-aarch64.sha256`
  - `PLATFORM_SHA256_macos_x64` — from `git-filter-repo-macos-x64.sha256`
  - `PLATFORM_SHA256_macos_aarch64` — from `git-filter-repo-macos-aarch64.sha256`
- Final commit with both the updated release.conf AND the closed index.json:
  - release.conf: `plugin/.git-filter-repo-config/release.conf`
  - index.json: set `"status": "closed"`, `"progress": 100`
  - Commit message: `feature: update release.conf with real SHA256 hashes from CI builds`

## Post-conditions
- [ ] `.github/workflows/build-git-filter-repo.yml` exists with all 4 platform build configs
- [ ] CI workflow executed successfully for all 4 platforms (visible in GitHub Actions runs)
- [ ] All 4 binaries created (artifacts available from CI run)
- [ ] `plugin/.git-filter-repo-config/release.conf` updated with real (non-placeholder) SHA256 values
- [ ] E2E: The GitHub release artifact for at least one platform is downloadable and its SHA256 hash matches the value in `plugin/.git-filter-repo-config/release.conf`
