# Plan: add-java-build-to-ci

## Goal
Automate building and distributing the CAT jlink bundle (JDK runtime + cat-hooks.jar + dependencies) so end users
download a pre-built bundle from GitHub rather than building locally. The jlink bundle should be a single self-contained
artifact that includes everything needed to run Java hooks.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** GitHub API rate limits for version checks; bundle size (~30-40MB); CI build complexity
- **Mitigation:** Cache version checks; compress bundles; use standard GitHub Actions workflows

## Design

### Architecture
The jlink bundle is a single artifact containing:
- Minimal JDK 25 runtime (java.base, java.logging, etc.)
- Jackson 3 modules (JSON processing)
- cat-hooks.jar (compiled hook handlers)

### Version Model
- The bundle version matches `plugin.json` version (e.g., "2.1")
- `session_start.sh` checks: `local_bundle_version == plugin.json version`
- If mismatch (upgrade OR downgrade), download the bundle for the plugin.json version
- Plugin developers build locally; their bundle version matches plugin.json so no download occurs

### Distribution Flow
```
Developer pushes code
  -> GitHub Actions builds jlink bundle (JDK + JAR + deps)
  -> Publishes as GitHub release artifact

End user starts session
  -> session_start.sh reads plugin.json version
  -> Checks local bundle version
  -> If mismatch: downloads bundle from GitHub releases via API
  -> If match: skip (fast path, no network call needed)
```

## Acceptance Criteria
- [ ] GitHub Actions workflow builds jlink bundle on push
- [ ] Bundle includes JDK runtime + cat-hooks.jar + Jackson dependencies
- [ ] session_start.sh downloads bundle when local version != plugin.json version
- [ ] session_start.sh uses GitHub API to find the correct release
- [ ] Local bundle stores its version for comparison
- [ ] Plugin developers can build bundle locally
- [ ] Existing Java hook execution still works with bundled JAR
- [ ] Tests pass

## Decomposition

This issue is decomposed into 3 sub-issues:

### Sub-issue 1: ci-build-jlink-bundle
**GitHub Actions CI pipeline** - Build the jlink bundle (JDK + cat-hooks.jar + deps) on push and publish as a GitHub
release artifact. Modify `jlink-config.sh` to include cat-hooks.jar in the bundle. Create `.github/workflows/` config.

### Sub-issue 2: session-start-version-check
**SessionStart version-based download** - Update `session_start.sh` to compare local bundle version against
`plugin.json` version. If mismatch, use GitHub API to find and download the correct release. Store version in a marker
file inside the bundle directory.

### Sub-issue 3: developer-local-bundle-rebuild
**Developer automation** - Provide a way for plugin developers to rebuild the jlink bundle locally when Java source
files change. This is the local development workflow counterpart to the CI build.

## Execution Steps
See individual sub-issue PLAN.md files for detailed execution steps.

## Success Criteria
- [ ] End users get a working jlink bundle without needing Maven or JDK installed
- [ ] Bundle is automatically updated when plugin version changes
- [ ] Plugin developers can iterate on Java hooks with local builds
- [ ] No regression in existing hook behavior
