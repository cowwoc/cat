# Plan: add-java-build-to-ci

## Goal
Add a JAR build step to ensure cat-hooks.jar is built before Java hooks can be invoked. As hooks migrate from Python
to Java, a missing JAR would silently break all Java hooks.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Build step adds startup time to plugin installation
- **Mitigation:** JAR build is fast (~5s); only runs on install/update

## Files to Modify
- `plugin/hooks/hooks.json` - Add SessionStart hook to build JAR
- `plugin/hooks/jdk/build_jar.sh` - New script: build cat-hooks.jar if missing or stale

## Acceptance Criteria
- [ ] JAR is automatically built when plugin is installed or updated
- [ ] JAR is rebuilt when Java source files change (stale detection)
- [ ] Build failure produces clear error message to user
- [ ] Existing SessionStart hooks still work correctly
- [ ] Tests pass

## Execution Steps
1. **Create build_jar.sh script**
   - Files: `plugin/hooks/jdk/build_jar.sh`
   - Check if `plugin/hooks/java/target/cat-hooks.jar` exists and is newer than source files
   - If missing or stale, run `plugin/hooks/java/build.sh`
   - Output JSON status for Claude Code hook system
   - Verify: Script builds JAR when missing, skips when up-to-date

2. **Register SessionStart hook in hooks.json**
   - Files: `plugin/hooks/hooks.json`
   - Add new SessionStart entry that runs `build_jar.sh`
   - Place it before other hooks that depend on Java (skill output, bash pretool, etc.)
   - Verify: Hook fires on session start and builds JAR

3. **Test end-to-end**
   - Delete `target/cat-hooks.jar`, start new session, verify JAR is rebuilt
   - Verify existing hooks still work after adding the build step
   - Run `python3 /workspace/run_tests.py` to check no regressions

## Success Criteria
- [ ] JAR exists after fresh plugin installation
- [ ] JAR is rebuilt when source changes
- [ ] No regression in existing hook behavior
