# Plan: developer-local-bundle-rebuild

## Goal
Provide a way for plugin developers to rebuild the jlink bundle locally when Java source files change. This is the
developer workflow counterpart to the CI build.

## Satisfies
None - infrastructure sub-issue of add-java-build-to-ci

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Developer must have JDK 25 installed
- **Mitigation:** Clear error message if JDK not available; document requirements

## Files to Modify
- `plugin/hooks/jlink/build-bundle.sh` - New script: rebuild jlink bundle locally when sources change
- `plugin/hooks/build-jlink.sh` - Already modified in ci-build-jlink-bundle to include cat-hooks.jar

## Post-conditions
- [ ] Developer can run a single command to rebuild the local jlink bundle
- [ ] Script detects when Java sources are newer than the bundle and rebuilds
- [ ] Rebuilt bundle has the same version as plugin.json (so session_start.sh skips download)
- [ ] Clear error if JDK 25 is not installed
- [ ] Works on Linux and macOS

- [ ] Single command rebuilds the full bundle
- [ ] Stale detection works (only rebuilds when needed)
- [ ] Bundle version matches plugin.json after rebuild

## Execution Steps
1. **Create build-bundle.sh script**
   - Files: `plugin/hooks/jlink/build-bundle.sh`
   - Check if JDK 25 is available
   - Compare mtimes of Java source files against the bundle VERSION marker
   - If stale: run `hooks/build.sh` then `build-jlink.sh`
   - Write VERSION marker matching plugin.json version
   - If up-to-date: skip with message

2. **Document developer workflow**
   - Files: `plugin/hooks/README.md`
   - Add section on local development: when to rebuild, how to rebuild, prerequisites

3. **Run tests**
   - `python3 /workspace/run_tests.py`

