# Refactor SkillOutput to Preprocessor Directives

## Goal

Replace the SkillOutput interface + bindings.json mechanism with preprocessor directive processing in SkillLoader.
This enables parameterized output from Java classes invoked in-JVM without forking, using the same
launcher scripts that already exist.

## Type

refactor

## Satisfies

None

## Research Findings

### Current Architecture

**SkillOutput interface** (`engine/.../util/SkillOutput.java`):
- `@FunctionalInterface` with single method `String getOutput() throws IOException`
- 3 implementors: `GetStatusOutput`, `GetStatuslineOutput`, `GetRetrospectiveOutput`

**bindings.json** (3 files):
- `plugin/skills/status/bindings.json` → `GetStatusOutput`
- `plugin/skills/statusline/bindings.json` → `GetStatuslineOutput`
- `plugin/skills/run-retrospective/bindings.json` → `GetRetrospectiveOutput`

**SkillLoader flow** (`engine/.../util/SkillLoader.java`):
1. Reads `bindings.json` from skill directory
2. Scans content for `${VAR_NAME}` patterns
3. Instantiates binding class via reflection with `JvmScope` constructor
4. Calls `getOutput()` and substitutes result into content

**Target Architecture:**
- Skill MD files use `!\x60command\x60` preprocessor directives (exclamation-backtick)
- SkillLoader detects directives referencing known Java launchers in `hooks/bin/`
- Instead of forking a process, SkillLoader invokes the class `main(String[] args)` in the same JVM
- Arguments enable parameterized output (e.g., `GetWorkOutput FORK_IN_ROAD opt1 opt2`)

### Existing Launcher Pattern

Each launcher in `hooks/bin/` follows this pattern:
```sh
exec "$DIR/java" -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.skills.ClassName "$@"
```

SkillLoader can parse the `-m` argument to extract the fully-qualified class name and invoke
`main()` directly.

### Classes That Already Have main()

- `GetStatusOutput` (implements SkillOutput + has main)
- `GetRetrospectiveOutput` (implements SkillOutput + has main)
- `GetCheckpointOutput`, `GetNextTaskOutput`, `GetRenderDiffOutput`, `GetIssueCompleteOutput`
- `ProgressBanner`

### Classes That Need main() Added

- `GetStatuslineOutput` (implements SkillOutput, no main)
- `GetWorkOutput` (no SkillOutput, no main)
- Plus others that currently have no CLI entry point

## Execution Steps

### Step 1: Add main() to GetStatuslineOutput

Add a `main(String[] args)` method to `GetStatuslineOutput` that creates `MainJvmScope`,
instantiates the class, calls `getOutput()`, and prints to stdout. Follow the pattern from
`GetStatusOutput.main()`.

### Step 2: Inline getOutput() into classes that have both main() and getOutput()

For `GetStatusOutput` and `GetRetrospectiveOutput`:
- Move the `getOutput()` logic into `main()` or into a helper called by `main()`
- Remove `implements SkillOutput` from class declaration
- Remove the `getOutput()` method
- Ensure `main()` prints the same output to stdout

### Step 3: Update SkillLoader to process preprocessor directives

In `SkillLoader.java`, add a new processing step in `substituteVars()` (or a new method):

1. Scan content for lines matching the preprocessor directive pattern:
   `!\x60"${CLAUDE_PLUGIN_ROOT}/hooks/bin/<launcher>" [args]\x60`
2. For each match, parse the launcher name from the path
3. Read the launcher script at `pluginRoot/hooks/bin/<launcher>`
4. Extract the `-m` module/class argument to get the fully-qualified class name
5. Load the class, find `main(String[])`, and invoke it with captured args
6. Capture stdout output (redirect System.out to ByteArrayOutputStream)
7. Replace the directive line with the captured output
8. If stderr has content, throw IOException with the stderr text

### Step 4: Update skill MD files to use preprocessor directives

Replace `${CAT_SKILL_OUTPUT}` variables with preprocessor directives:

**`plugin/skills/status/first-use.md`:**
- Replace `${CAT_SKILL_OUTPUT}` with: `!\x60"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-status-output"\x60`

**`plugin/skills/statusline/first-use.md`:**
- Replace `${CAT_SKILL_OUTPUT}` with the appropriate preprocessor directive

**`plugin/skills/run-retrospective/first-use.md`:**
- Replace `${CAT_RETROSPECTIVE_OUTPUT}` with the appropriate preprocessor directive

### Step 5: Remove bindings.json files

Delete:
- `plugin/skills/status/bindings.json`
- `plugin/skills/statusline/bindings.json`
- `plugin/skills/run-retrospective/bindings.json`

### Step 6: Remove SkillOutput interface and binding infrastructure from SkillLoader

In `SkillLoader.java`:
- Remove `loadBindings()` method
- Remove `invokeBinding()` method
- Remove `bindingsCache` and `bindingOutputCache` fields
- Remove binding-related code from `substituteVars()` and `resolveVariable()`
- Remove import of `SkillOutput`

Delete `engine/.../util/SkillOutput.java`.

### Step 7: Update tests

Update `SkillLoaderTest.java`:
- Remove tests for bindings.json (`loadResolvesBindingsJsonVariables`, `loadRejectsReservedVariableInBindings`, etc.)
- Add tests for preprocessor directive processing:
  - Test that directives referencing known launchers are resolved in-JVM
  - Test that arguments are passed correctly
  - Test that stderr content causes IOException
  - Test that unknown launchers pass through unchanged

### Step 8: Run tests

```bash
mvn -f client/pom.xml test
```

All tests must pass.

## Acceptance Criteria

- [ ] User-visible behavior unchanged (skill output identical before and after)
- [ ] SkillOutput interface removed from codebase
- [ ] All bindings.json files removed
- [ ] All former SkillOutput implementors have main() methods with CLI entry points
- [ ] SkillLoader processes preprocessor directives and invokes Java classes in-JVM when launcher is recognized
- [ ] Parameterized invocation works (arguments passed through to main())
- [ ] Skill MD files use preprocessor directives instead of ${CAT_SKILL_OUTPUT} variable substitution
- [ ] Tests passing
- [ ] No regressions