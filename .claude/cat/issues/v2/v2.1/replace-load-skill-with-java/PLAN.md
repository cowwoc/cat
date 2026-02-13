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

Replace `load-skill.sh` with a call to Java `SkillLoader.main()` using the jlink runtime. Also replace
`includes.txt` with inline `@path` references in `content.md` that SkillLoader expands by inlining file contents
(no wrapping tags), matching Claude Code's built-in `@file` behavior.

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

2. **Replace `includes.txt` with `@path` references in `content.md`** in SkillLoader:
   - **Remove `loadIncludes()` method** entirely
   - **Add `@path` expansion to `substituteVars()`** (or a new method called before/after var substitution):
     - Match lines starting with `@` followed by a relative path ending in `.md` or `.json`:
       pattern `^@(.+\.(?:md|json))$` (whole line match, no leading whitespace)
     - Resolve path relative to plugin root (same as includes.txt did)
     - Replace the `@path` line with the raw file contents (no `<include>` wrapping)
     - Fail-fast if the referenced file does not exist
     - Apply variable substitution to the inlined content (so `${VAR}` in referenced files resolves)
   - **Processing order:** Expand `@path` references first, then substitute `${VAR}` variables in the combined
     content. This ensures variables inside referenced files are resolved.

3. **Migrate the 4 skills using `includes.txt` to `@path` in `content.md`:**

   | Skill | includes.txt references | Migration |
   |-------|------------------------|-----------|
   | `remove` | `concepts/version-paths.md` | Add `@concepts/version-paths.md` at top of content.md |
   | `decompose-issue` | `concepts/version-paths.md` | Add `@concepts/version-paths.md` at top of content.md |
   | `init` | `templates/project.md`, `templates/roadmap.md`, `templates/cat-config.json` | Add 3 `@path` lines at top of content.md |
   | `add` | 9 files (templates + concepts) | Add 9 `@path` lines at top of content.md |

   - Delete all 4 `includes.txt` files after migration

4. **Add tests for `@path` expansion** in `SkillLoaderTest.java`:
   - Test that `@concepts/file.md` in content.md is replaced with file contents (no wrapping)
   - Test that variables inside `@path`-expanded content are resolved
   - Test that missing `@path` file causes IOException (fail-fast)
   - Test that `@` in non-path contexts (email addresses, annotations) is not expanded
   - Test existing bindings.json resolution still works

5. **Update refactor acceptance criteria in `/cat:add` stakeholder prompt** in
   `plugin/skills/add/content.md`:
   - In the `task_ask_type_and_criteria` step's standard criteria table, change the Refactor row from
     "Behavior unchanged" to "User-visible behavior unchanged"
   - This prevents false contradiction flags when refactoring changes internal implementation while preserving
     external behavior

6. **Run `mvn -f hooks/pom.xml verify`** to confirm all tests pass

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `plugin/scripts/load-skill.sh` | Modify | Replace sed substitution with Java SkillLoader invocation |
| `hooks/src/main/java/.../SkillLoader.java` | Modify | Remove `loadIncludes()`, add `@path` expansion |
| `hooks/src/test/java/.../SkillLoaderTest.java` | Modify | Add tests for `@path` expansion and bindings |
| `plugin/skills/add/content.md` | Modify | Add `@path` refs at top, clarify refactor criteria |
| `plugin/skills/remove/content.md` | Modify | Add `@concepts/version-paths.md` at top |
| `plugin/skills/remove/includes.txt` | Delete | Replaced by `@path` in content.md |
| `plugin/skills/decompose-issue/content.md` | Modify | Add `@concepts/version-paths.md` at top |
| `plugin/skills/decompose-issue/includes.txt` | Delete | Replaced by `@path` in content.md |
| `plugin/skills/init/content.md` | Modify | Add 3 `@path` lines at top |
| `plugin/skills/init/includes.txt` | Delete | Replaced by `@path` in content.md |
| `plugin/skills/add/includes.txt` | Delete | Replaced by `@path` in content.md |

### Key Constraints

- The jlink runtime path is `$CLAUDE_PLUGIN_ROOT/hooks/bin/java`
- JVM flags match session-start.sh: `-Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`
- SkillLoader.main() accepts: `plugin-root skill-name session-id [project-dir]`
- Module invocation: `-m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.SkillLoader`
- `@path` references use bare file contents (no XML wrapping) to match Claude Code's built-in `@file` behavior
- `@path` pattern: `^@(.+\.(?:md|json))$` — must be the entire line, path ends in `.md` or `.json`

## Success Criteria

- [ ] User-visible behavior unchanged: all skills load with variables fully resolved
- [ ] `${CAT_SKILL_OUTPUT}` in status/content.md resolves to actual GetStatusOutput result
- [ ] Undefined variables cause fail-fast with clear error message
- [ ] All three built-in variables (CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR) resolve correctly
- [ ] `@path` references in content.md expand to raw file contents (no wrapping tags)
- [ ] Missing `@path` files cause fail-fast with clear error
- [ ] All 4 `includes.txt` files deleted and replaced with `@path` in content.md
- [ ] Integration tests for `@path` expansion, bindings resolution, and variable substitution
- [ ] Existing SkillLoader tests continue to pass
- [ ] `mvn -f hooks/pom.xml verify` passes
