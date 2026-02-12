# Plan: inline-skill-handlers

## Problem

SkillLoader.executeHandler() spawns `handler.sh` as a subprocess, which then calls `bin/get-status-output` to spawn
another JVM process. This creates a JVM→bash→JVM round-trip that is wasteful since SkillLoader is already running inside
the JVM and could invoke the handler class directly in-process.

Additionally, SkillLoader.load() assembles its output in a non-intuitive order: handler output first, then
context/content. The method should be restructured for clarity.

## Satisfies

None - internal optimization

## Root Cause

When skills were originally bash scripts, handler.sh was the natural entry point. After migrating to Java, the handler
invocation wasn't updated to use direct in-process calls.

## Approach

1. Replace `handler.sh` file convention with a `handler.class` property file (or similar manifest) that names the Java
   class to invoke directly
2. SkillLoader calls the handler class's method in-process instead of spawning a subprocess
3. Delete `handler.sh` for the status skill (the only current handler.sh)
4. Restructure `load()` method to have a more intuitive ordering of inclusions

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Only one handler.sh exists (status skill)
- **Mitigation:** Verify status skill output is identical before and after

## Files to Modify

- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Replace executeHandler() subprocess call
  with direct in-process invocation; restructure load() method ordering
- `plugin/skills/status/handler.sh` - Delete (replace with handler class manifest)
- `plugin/skills/status/handler.class` (or similar) - New file naming the handler class

## Success Criteria

- [ ] SkillLoader invokes handler class directly in-process (no subprocess spawn)
- [ ] No handler.sh files remain
- [ ] Status skill output is identical before and after
- [ ] load() method has intuitive ordering of inclusions
- [ ] `mvn -f hooks/pom.xml test` passes
