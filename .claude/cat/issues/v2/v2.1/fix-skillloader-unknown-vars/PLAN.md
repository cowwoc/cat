# Plan: fix-skillloader-unknown-vars

## Problem

The Java SkillLoader throws `IOException` on undefined `${...}` variables (line 351 of `SkillLoader.java`). This
breaks `/cat:add` because `@concepts/version-paths.md` contains bash variables like `${BASE}` inside code blocks,
which the SkillLoader treats as undefined template variables.

## Satisfies

None - bugfix

## Root Cause

Empirical testing of Claude Code's native SKILL.md variable resolution shows it **passes through** unknown `${...}`
patterns as literals while resolving known variables (`${CLAUDE_SESSION_ID}`). Our SkillLoader's fail-fast on unknown
variables does not match this behavior.

Test results (Claude Code native behavior):
- `${CLAUDE_SESSION_ID}` → resolved everywhere (plain text, code fences, inline code)
- `${SOME_UNKNOWN_VAR}` → left as literal `${SOME_UNKNOWN_VAR}` everywhere
- `${BASE}` → left as literal `${BASE}` everywhere

No code-fence awareness — Claude Code resolves known variables inside code fences too.

## Execution Steps

1. **Edit `hooks/src/main/java/.../util/SkillLoader.java`** — In the `resolveVariable()` method (line 330), replace
   the `throw new IOException("Undefined variable...")` at line 351 with returning the original `${varName}` literal:

   ```java
   // Pass through unknown variables unchanged (matches Claude Code's native behavior)
   return "${" + varName + "}";
   ```

2. **Update tests in `SkillLoaderTest.java`** — Change any test that expects `IOException` on unknown variables to
   instead expect pass-through of the literal `${...}` string. Add a new test confirming unknown variables are
   preserved as literals in the output.

3. **Run tests** — `mvn -f hooks/pom.xml test`

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `hooks/src/main/java/.../util/SkillLoader.java` | Modify | Return literal `${varName}` instead of throwing on unknowns |
| `hooks/src/test/java/.../test/SkillLoaderTest.java` | Modify | Update/add tests for pass-through behavior |

## Success Criteria

- [ ] Unknown `${...}` variables pass through as literals (not errors)
- [ ] Known variables (`CLAUDE_SESSION_ID`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PROJECT_DIR`, bindings) still resolve
- [ ] `/cat:add` invocation succeeds (no `${BASE}` error from `@concepts/version-paths.md`)
- [ ] `mvn -f hooks/pom.xml test` passes
