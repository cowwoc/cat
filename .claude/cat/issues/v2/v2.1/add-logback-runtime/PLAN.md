# Plan: add-logback-runtime

## Goal
Add logback as a runtime dependency to the hooks Java project so SLF4J log messages are captured instead of silently
dropped by the NOP logger. Create a production logback.xml configuration.

## Satisfies
None (infrastructure improvement)

## Problem
The hooks Java project has `logback-classic` scoped to `test` only. At runtime, SLF4J falls back to
`NOP LoggerFactory`, producing stderr warnings:
```
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
```
All runtime logging is lost, making hook debugging difficult.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Logback is an automatic module; jlink patching must handle it. Adding runtime deps increases jlink image
  size slightly.
- **Mitigation:** build-jlink.sh already patches automatic modules. Logback adds ~1MB.

## Files to Modify
- `hooks/pom.xml` - Remove `<scope>test</scope>` from logback-classic dependency (SLF4J API remains transitive from
  logback-classic, consistent with existing pattern)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - Add `requires org.slf4j;`
- `hooks/src/main/resources/logback.xml` - Create production logging config

## Acceptance Criteria
- [ ] `logback-classic` dependency has no `<scope>` element
- [ ] `module-info.java` includes `requires org.slf4j;`
- [ ] `hooks/src/main/resources/logback.xml` exists with: NopStatusListener, stderr-only appender, root level WARN
- [ ] `mvn -f hooks/pom.xml test` passes
- [ ] After jlink rebuild, running `hook.sh GetSkillOutput` produces no SLF4J "Failed to load class" warnings on stderr

## Execution Steps
1. **Update pom.xml:** Remove `<scope>test</scope>` from the logback-classic dependency
2. **Update module-info.java:** Add `requires org.slf4j;` to the main module descriptor
3. **Create logback.xml:** Add `hooks/src/main/resources/logback.xml` with NopStatusListener, stderr-only CONSOLE
   appender at WARN level (modeled on the existing `logback-test.xml`)
4. **Run tests:** `mvn -f hooks/pom.xml test`
5. **Rebuild jlink and verify:** Run `build-jlink.sh` then invoke `hook.sh GetSkillOutput` and confirm no SLF4J
   warnings appear on stderr
