# Plan: replace-load-skill-with-java

## Problem

Commit 09c1743d added `bindings.json` resolution to the Java `SkillLoader` class and deleted `handler.sh`, but the
production entry point `plugin/scripts/load-skill.sh` was not updated to use the Java `SkillLoader`. As a result,
`${CAT_SKILL_OUTPUT}` in `plugin/skills/status/content.md` goes unresolved when `/cat:status` is invoked.

The shell script `load-skill.sh` only substitutes 3 built-in variables via `sed` and has no awareness of
`bindings.json`. The Java `SkillLoader` already handles all variable resolution (built-in + bindings) correctly.

## Satisfies

None - internal wiring fix

## Root Cause

The `inline-skill-handlers` issue (09c1743d) implemented bindings.json resolution in the Java `SkillLoader` class but
did not wire the production skill loading path to use it. The production path still goes through:
1. `SKILL.md` `!` command invokes `load-skill.sh`
2. `load-skill.sh` uses sed-based `substitute_vars` (only 3 built-in variables)
3. `${CAT_SKILL_OUTPUT}` passes through unresolved

## Approach

Replace `load-skill.sh` with a call to Java `SkillLoader.main()` using the jlink runtime.

### Execution Steps

1. **Update `plugin/scripts/load-skill.sh`** to invoke Java `SkillLoader.main()` instead of doing its own variable
   substitution:
   - Locate the jlink runtime at `$CLAUDE_PLUGIN_ROOT/hooks/bin/java` (same pattern as `session-start.sh`)
   - Invoke: `"$CLAUDE_PLUGIN_ROOT/hooks/bin/java" -Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1
     -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.SkillLoader
     "$CLAUDE_PLUGIN_ROOT" "$SKILL" "$CLAUDE_SESSION_ID" "$CLAUDE_PROJECT_DIR"`
   - Remove the bash-based `substitute_vars` function, `escape_sed` function, handler.sh invocation, context.list
     processing, and manual session tracking (`/tmp/cat-skills-loaded-*`)
   - Keep the parameter validation at the top (fail-fast on missing args)
   - Keep the `CLAUDE_PROJECT_DIR` check
   - The script becomes a thin wrapper: validate args, invoke Java

2. **Add integration test for SkillLoader bindings resolution** in `SkillLoaderTest.java`:
   - Test that `load()` resolves `bindings.json` variables to actual output (not placeholder text)
   - Verify that `${CAT_SKILL_OUTPUT}` does NOT appear in the resolved output
   - The existing `loadResolvesBindingsJsonVariables` test already covers this, but add a test that specifically
     verifies the status skill's content.md pattern (placeholder surrounded by other text)

3. **Run `mvn -f hooks/pom.xml verify`** to confirm all tests pass

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `plugin/scripts/load-skill.sh` | Modify | Replace sed substitution with Java SkillLoader invocation |
| `hooks/src/test/java/.../SkillLoaderTest.java` | Modify | Add integration test for bindings resolution |

### Key Constraints

- The jlink runtime path is `$CLAUDE_PLUGIN_ROOT/hooks/bin/java`
- JVM flags match session-start.sh: `-Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`
- SkillLoader.main() accepts: `plugin-root skill-name session-id [project-dir]`
- Module invocation: `-m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.SkillLoader`

## Success Criteria

- [ ] User-visible behavior unchanged: all skills load with variables fully resolved
- [ ] `${CAT_SKILL_OUTPUT}` in status/content.md resolves to actual GetStatusOutput result
- [ ] Undefined variables cause fail-fast with clear error message
- [ ] All three built-in variables (CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR) resolve correctly
- [ ] Integration test verifying bindings.json resolution through SkillLoader
- [ ] Existing SkillLoader tests continue to pass
- [ ] `mvn -f hooks/pom.xml verify` passes
