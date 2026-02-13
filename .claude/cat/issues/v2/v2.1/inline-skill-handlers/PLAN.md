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

1. Replace `handler.sh` with `bindings.json` — a JSON object mapping variable names to `SkillOutput` class FQCNs
2. `content.md` references these variables via `${VAR_NAME}` alongside built-in variables
3. SkillLoader resolves all `${VAR}` references: bindings → instantiate SkillOutput, call `getOutput()`; built-ins →
   substitute directly
4. Fail-fast if `bindings.json` defines a built-in variable name (e.g., `CLAUDE_PLUGIN_ROOT`)
5. Fail-fast if `content.md` references a user-defined variable not defined in `bindings.json`
6. Delete `handler.sh` for the status skill (the only current handler.sh)
7. Restructure `load()` method for clarity

### bindings.json Format

```json
{
  "CAT_SKILL_OUTPUT": "io.github.cowwoc.cat.hooks.skills.GetStatusOutput"
}
```

Each key is a variable name usable in `content.md` as `${KEY}`. Each value is the fully-qualified class name of a class
implementing `SkillOutput`. The class must have a public constructor accepting `JvmScope`.

### Variable Resolution Order

1. Load `bindings.json` if it exists in the skill directory
2. Validate: no binding key matches a built-in variable name → fail-fast if collision
3. For each `${VAR}` in content: check bindings first (instantiate class, call `getOutput()`), then check built-ins
4. Fail-fast if a variable is not found in either bindings or built-ins

### Built-in Variables

- `CLAUDE_PLUGIN_ROOT` — plugin root directory path
- `CLAUDE_SESSION_ID` — current session identifier
- `CLAUDE_PROJECT_DIR` — project directory path

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Only one handler.sh exists (status skill)
- **Mitigation:** Verify status skill output is identical before and after

## Files to Modify

- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — Replace executeHandler() subprocess with
  bindings.json resolution; restructure load() method
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SkillOutput.java` — Interface for skill output generators
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` — Implements SkillOutput
- `plugin/skills/status/handler.sh` — Delete
- `plugin/skills/status/bindings.json` — New file mapping variables to SkillOutput classes
- `plugin/hooks/README.md` — Update skill directory structure documentation

## Execution Steps

1. Create `SkillOutput` interface with `getOutput()` method and JvmScope constructor contract
2. Update `GetStatusOutput` to implement `SkillOutput`, inline getOutput logic using `scope.getClaudeProjectDir()`
3. Create `plugin/skills/status/bindings.json` with `CAT_SKILL_OUTPUT` → `GetStatusOutput` mapping
4. Update `SkillLoader` to load `bindings.json`, resolve bindings via reflection, fail-fast on collisions and undefined
   variables
5. Delete `handler.sh` and `handler.class`
6. Update `content.md` to use `${CAT_SKILL_OUTPUT}` where handler output was previously prepended
7. Update `plugin/hooks/README.md` to document `bindings.json` mechanism
8. Run `mvn -f hooks/pom.xml verify`

## Success Criteria

- [ ] SkillLoader resolves `bindings.json` variables via in-process SkillOutput invocation (no subprocess)
- [ ] No handler.sh or handler.class files remain
- [ ] Fail-fast on built-in variable collision in bindings.json
- [ ] Fail-fast on undefined variable references in content.md
- [ ] Status skill output is identical before and after
- [ ] load() method has intuitive variable resolution
- [ ] SkillLoader javadoc and hooks/README.md updated
- [ ] `mvn -f hooks/pom.xml verify` passes
