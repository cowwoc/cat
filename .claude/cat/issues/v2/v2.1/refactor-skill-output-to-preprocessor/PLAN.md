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

### Claude Code Preprocessor Behavior (Empirically Tested)

Tested via nested Claude instances with 7 scenarios. Results:

| Scenario | Exit | What Claude Receives |
|----------|------|---------------------|
| stdout only | 0 | stdout content substituted directly |
| stderr only | 0 | stderr content substituted directly (treated same as stdout) |
| stdout + stderr | 0 | stdout and stderr concatenated, no markers |
| stderr only | 1 | `<error>Bash command failed for pattern "!`cmd`": [stderr]\nSTDERR_CONTENT</error>` |
| stdout only | 1 | `<error>Bash command failed for pattern "!`cmd`": STDOUT_CONTENT</error>` |
| stdout + stderr | 1 | `<error>Bash command failed for pattern "!`cmd`": STDOUT_CONTENT\n[stderr]\nSTDERR_CONTENT</error>` |
| stderr only | 2 | Same as exit 1 — exit code value doesn't change format |

**Rules to replicate:**
1. **Exit 0**: Concatenate stdout + stderr (in that order). Substitute into content. No wrapping.
2. **Non-zero exit**: Wrap in `<error>Bash command failed for pattern "!`<original-directive>`": <content></error>`
3. **`[stderr]` marker**: On non-zero exit, if stderr exists, prepend `[stderr]\n` before stderr content. If stdout
   also exists, stdout comes first, then `[stderr]\n`, then stderr.
4. **Exit code 1 vs 2**: Identical treatment — only zero vs non-zero matters.
5. **Content never discarded**: All output included regardless of exit code.

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

In `SkillLoader.java`, add a new processing step (after variable substitution so `${CLAUDE_PLUGIN_ROOT}` is resolved):

1. Scan content for lines matching the preprocessor directive pattern. After variable substitution, directives will
   look like: `` !`"/resolved/path/hooks/bin/<launcher>" [args]` ``
2. For each match, parse the launcher name from the resolved path
3. Read the launcher script at `pluginRoot/hooks/bin/<launcher>`
4. Extract the `-m module/class` argument to get the fully-qualified class name
5. Load the class via `Class.forName()`, find `public static void main(String[])`, invoke with captured args
6. Capture stdout and stderr separately (redirect `System.out` and `System.err` to `ByteArrayOutputStream` instances,
   restore originals after invocation)
7. Handle exit behavior — `main()` methods that call `System.exit()` must be intercepted. Install a SecurityManager
   or use a custom approach to catch exit calls and capture the exit code without killing the JVM.

**Output formatting (must match Claude Code's native behavior):**

- **Exit 0 (normal return from main):** Concatenate stdout + stderr. Replace the directive line with the combined
  content.
- **Non-zero exit (System.exit(N) or exception):** Format as:
  `` <error>Bash command failed for pattern "!`<original-directive>`": <content></error> ``
  Where `<content>` is:
  - If only stdout: stdout content
  - If only stderr: `[stderr]\n` + stderr content
  - If both: stdout + `\n[stderr]\n` + stderr content
- **Exception from main():** Treat as non-zero exit. Write exception message to stderr stream before formatting.

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
  - Test exit 0 stdout-only: directive replaced with stdout content
  - Test exit 0 stderr-only: directive replaced with stderr content (same as stdout)
  - Test exit 0 both: directive replaced with stdout + stderr concatenated, no markers
  - Test non-zero exit stderr-only: wrapped in `<error>...:[stderr]\nSTDERR</error>`
  - Test non-zero exit stdout-only: wrapped in `<error>...:STDOUT</error>`
  - Test non-zero exit both: wrapped in `<error>...:STDOUT\n[stderr]\nSTDERR</error>`
  - Test that arguments are passed correctly to main()
  - Test that unknown launchers (not in hooks/bin/) pass through unchanged
  - Test that `${CLAUDE_PLUGIN_ROOT}` is resolved before directive processing

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