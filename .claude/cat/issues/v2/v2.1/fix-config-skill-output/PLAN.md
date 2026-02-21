# Plan: fix-config-skill-output

## Problem
The `/cat:config` skill is non-functional. It requires "SKILL OUTPUT CONFIG BOXES" to be injected via
preprocessor directive, but GetConfigOutput.java was created during the Python-to-Java migration without
being connected to the SkillLoader preprocessor pipeline. Every invocation fails with "SKILL OUTPUT
CONFIG BOXES not found."

## Satisfies
None (bugfix — incomplete migration)

## Expected vs Actual
- **Expected:** `/cat:config` displays a CURRENT_SETTINGS box before prompting the user
- **Actual:** Fails immediately with "SKILL OUTPUT CONFIG BOXES not found" error

## Root Cause
GetConfigOutput.java exists with all box-building methods but:
1. Does not implement the `SkillOutput` interface
2. Has no jlink launcher in `client/target/jlink/bin/`
3. `plugin/skills/config/first-use.md` has no `<output>` preprocessor directive section

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal — adding a new launcher and implementing an interface on an existing class
- **Mitigation:** Existing GetConfigOutput tests verify box output; new test for getOutput() method

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` — Add `implements SkillOutput`,
  add `getOutput(String[] args)` method and `main(String[] args)` entry point
- `client/src/main/java/module-info.java` — Verify GetConfigOutput is exported (likely already is via package export)
- `client/pom.xml` — Add `<launcher>` entry for `get-config-output` pointing to GetConfigOutput class
- `plugin/skills/config/first-use.md` — Add `<output>` section with
  `!${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output` preprocessor directive
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetConfigOutputTest.java` — Add test for
  `getOutput(new String[0])` returning non-null output containing "CURRENT SETTINGS"

## Test Cases
- [ ] Existing GetConfigOutput tests still pass (getCurrentSettings, box formatting)
- [ ] New test: `getOutput(new String[0])` returns non-null string containing "CURRENT SETTINGS" box
- [ ] New test: `getOutput(new String[0])` returns non-null string containing "CONFIGURATION SAVED" box

## Acceptance Criteria
- [ ] GetConfigOutput class signature includes `implements SkillOutput`
- [ ] GetConfigOutput defines `String getOutput(String[] args) throws IOException` matching interface contract
- [ ] GetConfigOutput has `main(String[] args)` entry point for jlink launcher
- [ ] `client/pom.xml` has launcher entry for `get-config-output`
- [ ] `plugin/skills/config/first-use.md` contains `<output>` section with preprocessor directive
- [ ] All existing GetConfigOutput tests pass
- [ ] New test verifies `getOutput()` returns output containing all config boxes
- [ ] E2E: Invoke `/cat:config` and confirm the CURRENT_SETTINGS box is displayed before the first
  AskUserQuestion prompt

## Execution Steps

1. **Implement SkillOutput interface on GetConfigOutput:**
   - Read `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` as reference for
     the pattern (implements SkillOutput, getOutput method, main method)
   - Add `implements SkillOutput` to GetConfigOutput class declaration
   - Add `import io.github.cowwoc.cat.hooks.util.SkillOutput;`
   - Implement `getOutput(String[] args)` method that calls `getCurrentSettings()` and concatenates all
     box outputs (CURRENT_SETTINGS, VERSION_GATES_OVERVIEW, SETTING_UPDATED template,
     CONFIGURATION_SAVED, NO_CHANGES) into a single string with section headers matching the names
     expected by first-use.md (e.g., `CURRENT_SETTINGS`, `VERSION_GATES_OVERVIEW`, etc.)
   - Add `main(String[] args)` entry point following GetStatusOutput pattern

2. **Add jlink launcher configuration:**
   - Read `client/pom.xml` and find existing `<launcher>` entries
   - Add `<launcher>get-config-output=io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.skills.GetConfigOutput</launcher>`

3. **Add output preprocessor directive to first-use.md:**
   - Read `plugin/skills/config/first-use.md`
   - Add `<output>` section with `!${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output` directive,
     following the pattern used by other skills (e.g., status skill)
   - Place it before the `<process>` section

4. **Add regression test:**
   - Read existing test file for GetConfigOutput
   - Add test method that creates a temp project dir with cat-config.json, instantiates GetConfigOutput
     with a test JvmScope, and verifies `getOutput(new String[0])` returns non-null output containing
     expected box headers

5. **Build and run tests:**
   - Run `mvn -f client/pom.xml test` to verify all tests pass

## Success Criteria
- [ ] `mvn -f client/pom.xml test` passes with exit code 0
- [ ] jlink build produces `client/target/jlink/bin/get-config-output` launcher
- [ ] GetConfigOutput implements SkillOutput interface
- [ ] first-use.md contains output preprocessor directive
