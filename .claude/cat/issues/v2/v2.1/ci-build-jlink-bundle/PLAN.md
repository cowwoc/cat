# Plan: ci-build-jlink-bundle

## Goal
Create a GitHub Actions workflow that builds the CAT jlink bundle (JDK runtime + cat-hooks.jar + Jackson dependencies)
on push and publishes it as a GitHub release artifact.

## Satisfies
None - infrastructure subtask of add-java-build-to-ci

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** CI environment needs JDK 25; cross-platform builds (linux-x64, macos-aarch64); release artifact management
- **Mitigation:** Use GitHub-hosted runners with setup-java action; build matrix for platforms

## Files to Modify
- `.github/workflows/build-jlink-bundle.yml` - New GitHub Actions workflow
- `plugin/hooks/jlink-config.sh` - Modify to include cat-hooks.jar in the jlink bundle
- `plugin/hooks/java/build.sh` - May need updates to output JAR to a location jlink-config.sh can consume

## Acceptance Criteria
- [ ] GitHub Actions workflow triggers on push to relevant branches
- [ ] Workflow builds cat-hooks.jar via Maven, then builds jlink bundle including the JAR
- [ ] Bundle is published as a GitHub release artifact with platform-specific naming
- [ ] Bundle includes a version marker file matching plugin.json version
- [ ] Cross-platform builds work (at minimum linux-x64)
- [ ] java.sh can find and use cat-hooks.jar from within the jlink bundle

## Execution Steps
1. **Modify jlink-config.sh to include cat-hooks.jar**
   - Files: `plugin/hooks/jlink-config.sh`
   - After building the jlink runtime, copy cat-hooks.jar and Jackson JARs into a `lib/` directory inside the bundle
   - Write a version marker file (`VERSION`) inside the bundle directory containing the plugin.json version
   - Update `build_runtime()` to first build cat-hooks.jar via `plugin/hooks/java/build.sh`

2. **Update java.sh classpath resolution**
   - Files: `plugin/hooks/java.sh`
   - Update `find_hooks_jar()` and `build_classpath()` to look for JARs inside the jlink bundle directory
   - Add priority path: `${CAT_JAVA_HOME}/lib/cat-hooks.jar`

3. **Create GitHub Actions workflow**
   - Files: `.github/workflows/build-jlink-bundle.yml`
   - Trigger on push to main/v* branches when Java source files change
   - Steps: checkout, setup JDK 25, build cat-hooks.jar, run jlink-config.sh build, upload artifact
   - Use build matrix for platform variants (linux-x64 at minimum)
   - Publish as release artifact tagged with plugin.json version

4. **Run tests**
   - `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] jlink-config.sh produces a bundle containing JDK + cat-hooks.jar + Jackson
- [ ] Bundle contains VERSION marker file
- [ ] GitHub Actions workflow builds and publishes successfully
- [ ] java.sh finds JAR from bundle location
