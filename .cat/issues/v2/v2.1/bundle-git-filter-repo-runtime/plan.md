# Plan: Bundle git-filter-repo into Runtime Artifacts

## Goal

Bundle a platform-appropriate `git-filter-repo` executable into generated CAT runtime artifacts so installed clients use the bundled binary and fail fast with a clear error when it is missing or not executable.

## Parent Requirements

- None

## Research Findings

- Runtime artifact assembly is performed by [client/common-cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java] and [client/distribution/scripts/build-runtime-artifacts.sh].
- Before this issue, the resolver [client/plugin/scripts/download-git-filter-repo.sh] resolved `git-filter-repo` from `PATH` and otherwise performed network download + SHA verification at first use.
- `git-rewrite-history` skill docs and metadata still describe on-demand download behavior:
  - [client/plugin/skills/common/git-rewrite-history/first-use.md]
  - [client/plugin/skills/claude/git-rewrite-history/SKILL.md]
  - [client/plugin/skills/codex/git-rewrite-history/SKILL.md]
- Existing Bats coverage for the resolver is centered on network download/cache/curl failure branches: [client/plugin/tests/scripts/download-git-filter-repo.bats].
- Runtime artifact tests already validate copied plugin tree structure and are the correct location for bundling assertions: [client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java].
- Distribution packaging runs `build-jlink-images.sh` then `build-runtime-artifacts.sh` during `package`, so build-time bundling must occur before or during runtime artifact build: [client/distribution/pom.xml].

## Approaches

### A: Keep runtime first-use download and only improve error messages
- **Risk:** HIGH
- **Scope:** Small
- **Description:** Leave network download behavior in runtime installs and tweak messaging.
- **Why rejected:** Violates issue goal (runtime install must be self-contained and fail fast instead of downloading at first use).

### B: Commit prebuilt platform binaries directly into source control under `client/plugin/lib/`
- **Risk:** MEDIUM
- **Scope:** Moderate
- **Description:** Track binaries in git and copy them through artifact build unchanged.
- **Why rejected:** Bloats source repo, complicates updates, and bypasses existing release-config + checksum contract.

### C: Build-time bundle + runtime fail-fast resolver (chosen)
- **Risk:** MEDIUM
- **Scope:** Moderate
- **Description:** During distribution build, materialize the platform binary into plugin `lib/` using release-config checksums; runtime resolver uses only the bundled file and never downloads or consults `PATH`.
- **Why chosen:** Meets self-contained runtime requirement, preserves existing release checksum governance, and minimizes changes to skill invocation surface.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:**
  - Platform naming mismatches between build-time bundling and runtime resolution could cause false missing-binary failures.
  - Removing download behavior can break existing tests and docs that assume curl fallback.
  - Build pipeline may fail on environments without network access at package time.
- **Mitigation:**
  - Reuse one platform normalization strategy across bundling and runtime resolution.
  - Replace obsolete tests with fail-fast bundled behavior tests.
  - Keep checksum verification and explicit actionable error messages when bundled binary is absent.

## Files to Modify

- `client/plugin/scripts/download-git-filter-repo.sh`
  - Remove runtime download path and curl fallback.
  - Resolve only the bundled binary under plugin root; fail with a clear error if it is absent, not executable, or checksum-mismatched.
  - Keep platform detection for bundled filename selection (`git-filter-repo-<platform>`).
  - Runtime checksum policy must be explicit:
    - Bundled binary: verify SHA256 against `client/plugin/.git-filter-repo-config/release.conf` before returning path.
    - On checksum mismatch: fail fast and do not attempt any network download.
- `client/distribution/scripts/build-runtime-artifacts.sh`
  - Add pre-build step to ensure platform binary is materialized under plugin `lib/` before artifact flattening.
  - Invoke resolver/bundling logic with deterministic environment so build output always includes bundled binary.
- `client/common-cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java`
  - Ensure bundled `lib/git-filter-repo-*` artifacts are copied into each runtime output and verified executable.
  - Add/adjust artifact verification checks for bundled binary presence in runtime roots.
- `client/plugin/skills/common/git-rewrite-history/first-use.md`
  - Update wording and procedure to reference bundled resolver behavior and fail-fast semantics (no runtime download).
- `client/plugin/skills/claude/git-rewrite-history/SKILL.md`
  - Update description text to remove “on-demand binary download”.
- `client/plugin/skills/codex/git-rewrite-history/SKILL.md`
  - Update description text to remove “on-demand binary download”.
- `client/plugin/tests/scripts/download-git-filter-repo.bats`
  - Remove/replace download-cache/curl-specific assertions.
  - Add tests for bundled-binary success and explicit fail-fast when bundled binary is missing/non-executable.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java`
  - Extend fixture + assertions to verify runtime artifacts include bundled `git-filter-repo` binary and executable bit.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/ReleaseDocumentationTest.java`
  - Update source-contract assertions if resolver internals/message expectations change.

## Pre-conditions

- [ ] All dependent issues are closed.
- [ ] `client/plugin/.git-filter-repo-config/release.conf` contains valid release tag and platform SHA256 values.
- [ ] Build environment can run `mvn -f client/pom.xml verify -e`.

## Jobs

### Job 1
- Invoke `/cat:tdd-implementation` and follow red-green-refactor for all testable behavior changes.
  - Files: `client/plugin/tests/scripts/download-git-filter-repo.bats`, `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java`, `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/ReleaseDocumentationTest.java`
- Add failing tests that define target behavior:
  - Runtime resolver returns bundled executable path without network download.
  - Runtime resolver exits non-zero with explicit missing/non-executable bundled binary message.
  - Required error substrings for failure-path assertions:
    - `ERROR: Bundled git-filter-repo executable not found`
    - `Expected bundled path:`
    - `Reinstall CAT runtime artifact`
    - `ERROR: Bundled git-filter-repo is not executable`
  - Runtime artifact build outputs include bundled `git-filter-repo` binary with executable permissions.
  - Files: same test files as above.
- Implement build-time bundling and runtime fail-fast resolution:
  - Update resolver script to eliminate runtime curl/download branches.
  - Update distribution build script to materialize bundled binary before runtime artifact flattening.
  - Use explicit source/destination conventions:
    - Source URL pattern: `https://github.com/cowwoc/cat/releases/download/${RELEASE_TAG}/git-filter-repo-${PLATFORM}`
    - Destination binary: `client/plugin/lib/git-filter-repo-${PLATFORM}`
    - Destination version marker: `client/plugin/lib/git-filter-repo-${PLATFORM}.version`
  - Update artifact builder verification/copy rules for bundled binary.
  - Files: `client/plugin/scripts/download-git-filter-repo.sh`, `client/distribution/scripts/build-runtime-artifacts.sh`, `client/common-cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java`
- Update skill docs and metadata to match implemented behavior:
  - Remove references to first-use on-demand download.
  - Describe bundled-binary and fail-fast semantics.
  - Files: `client/plugin/skills/common/git-rewrite-history/first-use.md`, `client/plugin/skills/claude/git-rewrite-history/SKILL.md`, `client/plugin/skills/codex/git-rewrite-history/SKILL.md`
- Run verification commands and fix regressions until green:
  - `bats client/plugin/tests/scripts/download-git-filter-repo.bats`
  - `mvn -f client/pom.xml -pl claude-cli -Dtest=PluginArtifactBuilderTest,ReleaseDocumentationTest test -e`
  - `mvn -f client/pom.xml verify -e`
- Update issue status record in the same implementation close-out commit.
  - File: `.cat/issues/v2/v2.1/bundle-git-filter-repo-runtime/index.json`

## Post-conditions

- [ ] Generated runtime artifacts (`client/distribution/target/runtime/{claude,codex}`) include a platform-appropriate bundled `git-filter-repo` executable under plugin `lib/`.
- [ ] `git-rewrite-history` resolves and uses the bundled executable for runtime installs.
- [ ] Runtime execution fails fast with a clear, actionable error when bundled executable is missing or not executable.
- [ ] Runtime resolver has no network-download code path for `git-filter-repo` acquisition (no runtime `curl`/remote fetch branch).
- [ ] Runtime first-use behavior resolves only the bundled binary, never `PATH`, and fails fast otherwise.
- [ ] Script/skill tests are updated from download-cache behavior to bundled fail-fast behavior.
- [ ] `mvn -f client/pom.xml verify -e` passes.
