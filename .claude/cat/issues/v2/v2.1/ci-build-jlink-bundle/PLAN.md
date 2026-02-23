# Plan: ci-build-jlink-bundle

## Goal
Create a GitHub Actions workflow that builds the CAT jlink bundle (JDK runtime + cat-hooks.jar + Jackson dependencies)
on push and publishes it as a GitHub release artifact.

## Satisfies
None - infrastructure sub-issue of add-java-build-to-ci

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** CI environment needs JDK 25; cross-platform builds (linux-x64, macos-aarch64); release artifact management
- **Mitigation:** Use GitHub-hosted runners with setup-java action; build matrix for platforms

## Files to Modify
- `.github/workflows/build-jlink-bundle.yml` - New GitHub Actions workflow
- `plugin/hooks/build-jlink.sh` - Modify to include cat-hooks.jar in the jlink bundle
- `hooks/build.sh` - May need updates to output JAR to a location build-jlink.sh can consume

## Acceptance Criteria
- [ ] GitHub Actions workflow triggers on push to relevant branches
- [ ] Workflow builds cat-hooks.jar via Maven, then builds jlink bundle including the JAR
- [ ] Bundle is published as a GitHub release artifact with platform-specific naming
- [ ] Bundle includes a version marker file matching plugin.json version
- [ ] Cross-platform builds work (at minimum linux-x64)
- [x] ~~java.sh can find and use cat-hooks.jar from within the jlink bundle~~ (N/A - jlink launchers handle classpath via module system directly)

## Execution Steps
1. **Modify build-jlink.sh to include cat-hooks.jar**
   - Files: `plugin/hooks/build-jlink.sh`
   - After building the jlink runtime, copy cat-hooks.jar and Jackson JARs into a `lib/` directory inside the bundle
   - Write a version marker file (`VERSION`) inside the bundle directory containing the plugin.json version
   - Update the build function to first build cat-hooks.jar via `hooks/build.sh`

2. **Create GitHub Actions workflow**
   - Files: `.github/workflows/build-jlink-bundle.yml`
   - Trigger on push to main/v* branches when Java source files change
   - Steps: checkout, setup JDK 25, build cat-hooks.jar, run build-jlink.sh, upload artifact
   - Use build matrix for platform variants (linux-x64 at minimum)
   - Publish as release artifact tagged with plugin.json version

3. **Run tests**
   - `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] build-jlink.sh produces a bundle containing JDK + cat-hooks.jar + Jackson
- [ ] Bundle contains VERSION marker file
- [ ] GitHub Actions workflow builds and publishes successfully
- [x] ~~java.sh finds JAR from bundle location~~ (N/A - jlink module system handles this)
