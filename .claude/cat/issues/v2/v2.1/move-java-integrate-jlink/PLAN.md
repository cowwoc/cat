# Plan: move-java-integrate-jlink

## Current State
Java hooks source lives at `plugin/hooks/java/`. This means the entire Java project (source, test, target/) gets copied into the plugin cache on every install. The jlink runtime is built by a standalone bash script (`jlink-config.sh`) that has broken Maven Central URLs.

## Target State
Java source moved to `/workspace/hooks/` (outside plugin dir). Maven build produces both the hooks JAR and a jlink runtime image. The build-hooks skill copies both artifacts to the plugin cache.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** All paths referencing `plugin/hooks/java/` must be updated
- **Mitigation:** Comprehensive grep for all references; existing tests validate build

## Files to Modify

### Move Java source
- `plugin/hooks/java/` → `/workspace/hooks/` (git mv the entire directory)
- `CLAUDE.md` line 46 - Update `mvn -f plugin/hooks/java/pom.xml test` → `mvn -f hooks/pom.xml test`
- `.claude/cat/conventions/java.md` - Update `cd plugin/hooks/java` → `cd hooks`
- `plugin/skills/delegate/content.md` line 302 - Update mvn compile path

### Integrate jlink into Maven build
- `hooks/pom.xml` (after move) - Add maven-jlink-plugin or exec-maven-plugin to run jlink during `verify` phase
- Output jlink image to `target/cat-jdk-25/`
- Use the JDK modules from `jlink-config.sh`: java.base, java.logging, java.sql, jdk.unsupported
- Jackson JARs are already Maven dependencies - resolve from local repo instead of downloading via curl

### Update build-hooks skill
- `.claude/skills/build-hooks/SKILL.md` - Update Maven path from `/workspace/plugin/hooks/java/pom.xml` to `/workspace/hooks/pom.xml`
- Add step to copy jlink runtime: `cp -r /workspace/hooks/target/cat-jdk-25/ PLUGIN_CACHE/runtime/cat-jdk-25/`
- Fix JAR copy path: `cp /workspace/hooks/target/cat-hooks-2.1.jar PLUGIN_CACHE/hooks/cat-hooks-2.1.jar`

### Update existing issue PLANs (path references only)
These files contain `plugin/hooks/java/` paths that will be stale after the move. Update them to `hooks/`:
- `.claude/cat/issues/v2/v2.1/unify-project-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-sessionstart-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-stop-sessionend-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-pretooluse-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-posttooluse-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-userpromptsubmit-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/ci-build-jlink-bundle/PLAN.md`
- `.claude/cat/issues/v2/v2.1/developer-local-bundle-rebuild/PLAN.md`
- `.claude/cat/issues/v2/v2.1/migrate-python-tests/PLAN.md`
- `.claude/cat/issues/v2/v2.1/migrate-token-counting/PLAN.md`
- `.claude/cat/issues/v2/v2.1/migrate-python-to-java/PLAN.md`
- `.claude/cat/issues/v2/v2.1/mavenize-java-hooks/PLAN.md`

## Execution Steps

1. **Step 1: Move Java project directory**
   - Run: `git mv plugin/hooks/java hooks`
   - Verify: `ls hooks/pom.xml` exists

2. **Step 2: Update direct references in active files**
   - Files: `CLAUDE.md`, `.claude/cat/conventions/java.md`, `plugin/skills/delegate/content.md`, `.claude/skills/build-hooks/SKILL.md`
   - Replace `plugin/hooks/java` with `hooks` in all paths

3. **Step 3: Add jlink to Maven build**
   - File: `hooks/pom.xml`
   - Add exec-maven-plugin bound to `verify` phase that runs jlink
   - jlink command: `${java.home}/bin/jlink --module-path <jackson-jars> --add-modules java.base,java.logging,java.sql,jdk.unsupported --output target/cat-jdk-25 --strip-debug --no-man-pages --no-header-files --compress zip-6`
   - Use maven-dependency-plugin to copy Jackson JARs to a staging dir for jlink module-path

4. **Step 4: Update build-hooks skill**
   - File: `.claude/skills/build-hooks/SKILL.md`
   - Update Maven path to `/workspace/hooks/pom.xml`
   - Add step to copy jlink runtime to plugin cache `runtime/cat-jdk-25/`
   - Fix JAR artifact name to `cat-hooks-2.1.jar`

5. **Step 5: Update issue PLAN.md references**
   - Files: All 12 issue PLAN.md files listed above
   - Replace `plugin/hooks/java/` with `hooks/` throughout

6. **Step 6: Run tests**
   - `mvn -f hooks/pom.xml verify`
   - All 318 tests must pass

## Success Criteria
- [ ] `plugin/hooks/java/` directory no longer exists
- [ ] `hooks/pom.xml` exists and `mvn -f hooks/pom.xml verify` passes all tests
- [ ] `mvn -f hooks/pom.xml verify` produces `target/cat-jdk-25/bin/java` (jlink image)
- [ ] build-hooks skill correctly installs both JAR and jlink image to plugin cache
- [ ] No remaining references to `plugin/hooks/java` in active files