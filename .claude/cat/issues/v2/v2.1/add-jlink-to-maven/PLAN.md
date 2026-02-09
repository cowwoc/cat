# Plan: add-jlink-to-maven

## Goal
Integrate jlink runtime image generation into the Maven build so `mvn verify` produces both the hooks JAR and a minimal JDK 25 runtime. Update the build-hooks skill to install both artifacts to the plugin cache.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** jlink requires specific module configuration; Jackson JARs must be on the module-path
- **Mitigation:** Module list already validated in jlink-config.sh; Maven dependency plugin provides JARs

## Files to Modify
- `hooks/pom.xml` - Add exec-maven-plugin or maven-jlink-plugin to produce jlink image during verify phase
- `.claude/skills/build-hooks/SKILL.md` - Add step to copy jlink runtime to plugin cache
- `plugin/hooks/session_start.sh` - Verify JDK_SUBDIR path still matches output location

## Acceptance Criteria
- [ ] `mvn -f hooks/pom.xml verify` produces `target/jlink/bin/java`
- [ ] jlink image includes modules: java.base, java.logging, java.sql, jdk.unsupported
- [ ] jlink image includes Jackson 3 modules from Maven dependencies
- [ ] build-hooks skill installs jlink image to `CLAUDE_PLUGIN_ROOT/runtime/cat-jdk-25/`
- [ ] session_start.sh finds and validates the installed runtime
- [ ] All existing tests still pass

## Execution Steps

1. **Step 1: Add maven-dependency-plugin to stage Jackson JARs**
   - File: `hooks/pom.xml`
   - Add execution bound to `prepare-package` phase
   - Copy jackson-core, jackson-databind, jackson-annotations JARs to `target/jlink-libs/`

2. **Step 2: Add exec-maven-plugin to run jlink**
   - File: `hooks/pom.xml`
   - Add execution bound to `verify` phase (after tests pass)
   - Command: `${java.home}/bin/jlink`
   - Args: `--module-path target/jlink-libs --add-modules java.base,java.logging,java.sql,jdk.unsupported --output target/jlink --strip-debug --no-man-pages --no-header-files --compress zip-6`
   - Add `target/jlink` to .gitignore if not already

3. **Step 3: Update build-hooks skill**
   - File: `.claude/skills/build-hooks/SKILL.md`
   - Add step after JAR install: copy `hooks/target/jlink/` to plugin cache `runtime/cat-jdk-25/`
   - Add verification step for jlink image

4. **Step 4: Verify end-to-end**
   - Run `mvn -f hooks/pom.xml verify`
   - Check `hooks/target/jlink/bin/java -version` works
   - Run build-hooks skill and verify session_start.sh finds the runtime

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` produces working jlink image at `target/jlink/`
- [ ] build-hooks skill installs both JAR and jlink image to plugin cache
- [ ] session_start.sh debug trace shows JDK runtime verified
- [ ] All 318+ tests still pass