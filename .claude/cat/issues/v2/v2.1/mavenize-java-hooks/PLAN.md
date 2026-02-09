# Plan: mavenize-java-hooks

## Current State
Java hook sources live in a flat directory `plugin/hooks/src/io/github/cowwoc/cat/hooks/` outside standard Maven conventions. The hook bridge script is `plugin/hooks/java.sh`.

## Target State
Java sources follow standard Maven layout under `hooks/src/main/java/io/github/cowwoc/cat/hooks/`. The hook bridge script is `plugin/hooks/hook.sh` with improved classpath resolution and JVM configuration.

## Satisfies
None - infrastructure/build improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** hook.sh replaces java.sh - hooks.json references must be updated
- **Mitigation:** All hooks.json entries already reference hook.sh; tests verify compilation

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/**/*.java` - Move all 58 Java source files to Maven layout
- `plugin/hooks/hook.sh` - New hook bridge script replacing java.sh
- `plugin/hooks/java.sh` - Delete (replaced by hook.sh)
- `hooks/pom.xml` - Update source directory configuration
- `hooks/build.sh` - Update build paths
- `plugin/hooks/hooks.json` - Update hook command references

## Execution Steps
1. **Step 1:** Move all Java sources from `plugin/hooks/src/` to `hooks/src/main/java/`
   - Files: All 58 .java files under `plugin/hooks/src/io/github/cowwoc/cat/hooks/`
2. **Step 2:** Create `plugin/hooks/hook.sh` as the new Java hook bridge
   - Files: `plugin/hooks/hook.sh`
3. **Step 3:** Delete old `plugin/hooks/java.sh`
   - Files: `plugin/hooks/java.sh`
4. **Step 4:** Update `hooks/pom.xml` to use standard Maven source directory
   - Files: `hooks/pom.xml`
5. **Step 5:** Update `hooks/build.sh` for new paths
   - Files: `hooks/build.sh`
6. **Step 6:** Verify compilation with `mvn -f hooks/pom.xml compile`

## Success Criteria
- [ ] All Java sources under standard Maven layout `src/main/java/`
- [ ] `mvn -f hooks/pom.xml compile` succeeds
- [ ] All tests pass: `mvn -f hooks/pom.xml test`
- [ ] No references to old `plugin/hooks/src/` directory remain
- [ ] hook.sh correctly invokes Java handlers