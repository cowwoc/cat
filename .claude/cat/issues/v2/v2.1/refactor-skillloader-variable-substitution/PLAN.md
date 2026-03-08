<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: refactor-skillloader-variable-substitution

## Current State

SkillLoader applies two global substitution passes across all content before executing preprocessor directives:

- **Pass 1** (`${VAR_NAME}`): Replaces `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_SESSION_ID}`, `${CLAUDE_PROJECT_DIR}`
  across the entire content body including `@`-inlined files. Unknown variables pass through unchanged.
- **Pass 2** (`$N`): Replaces `$0`, `$1`, etc. with skill arguments across the entire content body including
  `@`-inlined files (see `SkillLoader.java:625–641`, `POSITIONAL_ARG_PATTERN` at line 101).

This corrupts bash positional arguments in `@`-inlined files. For example, `@concepts/version-paths.md` contains
bash functions using `$1`, `$2`, `$3`, `$4` as bash positional args; when inlined into `add/first-use.md`, Pass 2
replaces them with skill arguments. This manifests visibly in `add-agent` output where
`local MAJOR="$1"` becomes `local MAJOR="<skill argument>"`.

Additionally, `skill-builder-agent/first-use.md:445` contains a real `!` directive inside a fenced code block
meant as a documentation example. Empirically verified: Claude Code DOES execute `!` directives inside fenced
code blocks, so this example runs on every load.

SkillLoader also implements `@path` file inlining (`expandPaths()`), which is a CAT-specific preprocessing step.
Claude Code does not natively support `@path` syntax (empirically verified: 0/3 trials). Claude Code's native
approach is `[name](file.md)` markdown links that agents read on demand via the Read tool. The `@path` mechanism
pre-inlines all referenced content at load time, consuming tokens upfront regardless of whether the agent needs
the content. Removing `@path` support and using native `[name](file.md)` references enables on-demand loading
and reduces token usage for skills with large reference files.

Finally, `getEnvironmentVariable(String name)` in `JvmScope` is currently a `default` method returning `null`.
Per project convention (`.claude/rules/java.md` § "Interface vs Abstract Class"), implementation logic belongs in
an abstract class, not as interface default methods.

## Target State

1. Variable substitution applies **only inside `!` backtick directive strings**. Content body (prose, code blocks)
   passes through untouched to Claude Code, which handles `${VAR}` natively. `CLAUDE_PLUGIN_ROOT`,
   `CLAUDE_SESSION_ID`, and `CLAUDE_PROJECT_DIR` are already injected as environment variables by `InjectEnv.java`
   — no SkillLoader special-casing needed.
2. `@path` file inlining is removed. All skill files that used `@path` references are updated to use
   `` `path` `` inline code references with contextual prose (primary pattern, matching Anthropic's
   skill-creator convention) or `[description](path)` markdown links when the description adds value.
   Claude loads both on demand via the Read tool.
3. `getEnvironmentVariable(String name)` is a normal abstract method on `JvmScope`, implemented in
   `AbstractJvmScope` (or left abstract for subclass implementation as appropriate).

## Parent Requirements

None — infrastructure/quality improvement

## Research Findings

1. Claude Code preprocessor is single-pass — does NOT recursively expand `${CLAUDE_PLUGIN_ROOT}` or execute
   nested `!` directives in preprocessor output (empirically verified)
2. Claude Code DOES execute `!` directives inside fenced code blocks (3/3 empirical trials)
3. `CLAUDE_PLUGIN_ROOT`, `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR` are injected as env vars by `InjectEnv.java`
   (lines 29–30, 99–101) — available via `System.getenv()` in SkillLoader
4. Per Claude Code docs (`skills#available-string-substitutions`), built-in substitutions include:
   `$0`..`$N`, `$ARGUMENTS`, `$ARGUMENTS[N]`, `${CLAUDE_SKILL_DIR}`
5. Claude Code does NOT natively expand `@path` references in system prompts or SKILL.md content
   (empirically verified: 0/3 trials for both `@path` and `[name](file.md)` inline expansion)
6. Claude Code's native approach for supporting files is `[name](file.md)` markdown links — agents
   load these on demand via the Read tool (confirmed by official docs at code.claude.com/docs/en/skills)
7. Per `.claude/rules/java.md` § "Interface vs Abstract Class": prefer abstract superclass over default
   methods on interfaces; interfaces define contracts, implementation logic belongs in abstract classes

## Impact Notes

Closes the `$1` corruption bug where bash `$1` in `@`-inlined `version-paths.md` was replaced with the skill
argument string. Removes the non-standard `@path` inlining mechanism in favor of Claude Code's native on-demand
file loading. Updates `plugin/hooks/README.md` and `plugin/concepts/skill-loading.md` to document the new
behavior.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:**
  - `$N` in skill prose no longer substituted. No known legitimate uses of `$N` in prose exist.
  - `@path` references no longer expanded. 17 `@path` references across 6 skill files must be converted to
    `[name](file.md)` links. Agents will need to use the Read tool to load these files on demand.
- **Mitigation:** Tests explicitly verify content body `$N` is preserved; directive substitution verified
  with mocked directives; all `@path` references converted before merging; `mvn verify` catches regressions.

## Approach

### Variable Substitution Scoping (already implemented)

Refactor `processPreprocessorDirectives()` to expand variables inline within each directive string as it is
matched. Remove the global passes from `substituteVars()`. This co-locates substitution logic with directive
execution and minimizes the change surface.

### @path Removal

Remove `expandPaths()`, `PATH_PATTERN`, and all related code from SkillLoader. Update all skill files that
use `@path` syntax to use `[description](path)` markdown links (when a description adds value) or
`` `path` `` inline code references (when it doesn't). Claude loads both on demand via the Read tool.

### getEnvironmentVariable Interface Change

Change `getEnvironmentVariable(String name)` from a `default` method on `JvmScope` to a normal (abstract)
interface method. `MainJvmScope` and `TestJvmScope` already provide implementations, so no new code is needed
— only the `default` keyword and body are removed from the interface.

**Rejected alternatives:**

- **Keep global Pass 1 with code-block exclusion:** Excludes fenced code blocks but not `@`-inlined bash
  functions in non-code-block context. Doesn't address the root issue and adds fragile region tracking.
- **Keep `@path` with post-expansion protection:** Would preserve `@path` but add complexity to protect
  inlined content from variable substitution. The native `[name](file.md)` approach is simpler and aligns
  with Claude Code's design.

## Files to Modify

1. `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — remove `@path` support, scope
   variable substitution to directives only
2. `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — change `getEnvironmentVariable` from
   default to abstract
3. `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` — update/remove `@path` tests,
   update variable substitution tests
4. `plugin/skills/add/first-use.md` — convert 9 `@path` references to `[name](file.md)` links
5. `plugin/skills/decompose-issue-agent/first-use.md` — convert 1 `@path` reference
6. `plugin/skills/init/first-use.md` — convert 3 `@path` references
7. `plugin/skills/remove/first-use.md` — convert 1 `@path` reference
8. `plugin/skills/skill-builder-agent/first-use.md` — convert 2 `@path` references, fix `!` in code block
9. `plugin/skills/tdd-implementation-agent/tdd.md` — update 3 `@path` references in documentation template
10. `plugin/hooks/README.md` — update variable substitution and loading behavior documentation
11. `plugin/concepts/skill-loading.md` — remove `@path` documentation, update loading pipeline description

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Refactor `SkillLoader.java`**: remove `@path` support and scope variable substitution to directive strings
  - Remove `PATH_PATTERN` field (`Pattern.compile("^@(.+/.+)$", Pattern.MULTILINE)`)
  - Remove `expandPaths(String content)` and `expandPaths(String content, Set<Path> visitedPaths)` methods
  - Remove all circular-reference tracking for `@path` expansion
  - Note: `findCodeBlockRegions()`, `isInsideCodeBlock()`, and `CODE_BLOCK_PATTERN` are also used by
    `parseContent()` (to skip `<output>` tags in fenced code blocks) — do NOT remove these
  - Add `ARGUMENTS_INDEXED_PATTERN = Pattern.compile("\\$ARGUMENTS\\[(\\d+)\\]")` as a new class-level field
  - Rename `substituteVars()`/`expandPathsAndDirectives()` to just call `processPreprocessorDirectives()`
    directly (no path expansion step)
  - In `processPreprocessorDirectives()`, before tokenizing arguments, expand variables in both the launcher
    path and arguments token using a new private method `expandDirectiveString(String text, String skillName)`
  - Add private method `expandDirectiveString(String text, String skillName)` that applies substitution in
    order:
    1. `ARGUMENTS_INDEXED_PATTERN` (`$ARGUMENTS[N]`) → `skillArgs.get(N)` if in range, else literal
    2. Pattern `\\$ARGUMENTS` → `String.join(" ", skillArgs)` (all skill args joined with space)
    3. Pattern `\\$(\\d+)` → `skillArgs.get(N)` if in range, else literal `$N`
    4. `VAR_PATTERN` (`${name}`) → `resolveVariable(name, skillName)` for each match
  - Update `resolveVariable(String varName, String skillName)` to use `scope.getEnvironmentVariable()` for
    all variables except `CLAUDE_SKILL_DIR` (computed from `pluginRoot/skills/{stripPrefix(skillName)}/`)
  - Update class-level Javadoc to document: variable substitution applies only inside `!` directive strings;
    `@path` file inlining is no longer supported
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`

- **Change `getEnvironmentVariable` from default to abstract**: remove `default` keyword and method body from
  `JvmScope` interface
  - `MainJvmScope` already overrides with `System.getenv(name)`
  - `TestJvmScope` already overrides with injected `envVars` map
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- **Fix `skill-builder-agent/first-use.md`**: replace `!` with `[BANG]` at line 445
  - Files: `plugin/skills/skill-builder-agent/first-use.md`

- **Update `plugin/hooks/README.md`**: reflect directive-only substitution and removal of `@path`
  - In `Loading Behavior` section, change step 2 to: "Process preprocessor directives (with variable
    expansion inside directives)"
  - Remove all references to `@path` expansion
  - Update `Built-in Variables` section to document directive-only variable forms
  - Files: `plugin/hooks/README.md`

- **Update `plugin/concepts/skill-loading.md`**: remove `@path` from loading pipeline
  - Update step 5 in the loading pipeline to remove `@path` reference
  - Files: `plugin/concepts/skill-loading.md`

### Wave 2

- **Convert `@path` references in skill files**: use `` `path` `` inline code with surrounding prose
  that tells Claude what the file contains and when to read it. This matches the pattern used in
  Anthropic's own skill-creator (e.g., "See `references/schemas.md` for the full schema").
  Use `[description](path)` only when the description adds genuine value beyond what surrounding
  prose provides. Claude reads both formats on demand via the Read tool.
  - `plugin/skills/add/first-use.md`: convert 9 references. Examples:
    - `@templates/issue-state.md` → prose like "See `templates/issue-state.md` for the issue state
      template" or similar contextual reference
    - `@concepts/version-paths.md` → "See `concepts/version-paths.md` for version path conventions"
    - Apply same pattern to remaining references
  - `plugin/skills/decompose-issue-agent/first-use.md`: convert 1 reference
  - `plugin/skills/init/first-use.md`: convert 3 references
  - `plugin/skills/remove/first-use.md`: convert 1 reference
  - `plugin/skills/skill-builder-agent/first-use.md`: convert 2 references:
    - `@${CLAUDE_PLUGIN_ROOT}/concepts/work.md` → `[CAT work concepts](${CLAUDE_PLUGIN_ROOT}/concepts/work.md)`
    - `@${CLAUDE_PLUGIN_ROOT}/skills/merge-subagent/SKILL.md` → similar
  - `plugin/skills/tdd-implementation-agent/tdd.md`: update 3 `@path` references in documentation template
  - Files: all skill files listed above

- **Update `SkillLoaderTest.java`**: remove `@path` tests, update variable substitution tests
  - Remove or revise all tests that verify `@path` expansion behavior
  - Revise `positionalArgsResolved()` → `directivePositionalArgsSubstituted()`: verify directive context
  - Revise `missingPositionalArgsPassedThrough()` → `contentBodyPositionalArgPassedThrough()`: verify
    content body preservation
  - Add `directiveEnvVarSubstitution()`, `directiveArgumentsAllSubstituted()`,
    `directiveArgumentsIndexedSubstituted()`, `directiveSkillDirSubstituted()`,
    `contentBodyVarPassedThrough()` tests
  - Remove `inlinedFileBashArgPreserved()` test (no more `@path` inlining)
  - Run `mvn -f client/pom.xml verify` and confirm all tests pass
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`

- **Update STATE.md** in the issue directory: set status to closed, progress 100%

## Post-conditions

- [ ] SkillLoader `expandPaths()` method and `PATH_PATTERN` field are removed — `@path` references are no
      longer expanded
- [ ] All `@path` references in skill files (17 total across 6 files) are converted to `` `path` ``
      inline code with contextual prose or `[description](path)` links, loaded on demand via Read tool
- [ ] Pass 2 (`POSITIONAL_ARG_PATTERN` loop) is removed — `$N` in content body is never substituted
- [ ] Pass 1 (`${VAR_NAME}` expansion) applies only inside `!` backtick directive strings, not to entire
      content body
- [ ] `resolveVariable()` inside directives handles: `$0`..`$N` and `$ARGUMENTS` (positional args),
      `$ARGUMENTS[N]` (0-based array access), `${name}` via `scope.getEnvironmentVariable(name)`,
      `${CLAUDE_SKILL_DIR}` as SkillLoader-computed skill directory path (`pluginRoot/skills/{skill-name}/`)
- [ ] Hardcoded switch cases for `CLAUDE_PLUGIN_ROOT`, `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR` removed from
      `resolveVariable()` — they fall through to `scope.getEnvironmentVariable()`
- [ ] `getEnvironmentVariable(String name)` is a normal (non-default) method on `JvmScope` interface,
      with implementations in `MainJvmScope` and `TestJvmScope`
- [ ] `plugin/skills/skill-builder-agent/first-use.md` line 445: `!` replaced with `[BANG]` alias in fenced
      code block documentation example
- [ ] SkillLoader test suite updated: `@path` tests removed, content body `$N` and `${VAR}` preserved
      verbatim, directives have args and env vars substituted, `$ARGUMENTS[N]` resolves correctly,
      `${CLAUDE_SKILL_DIR}` resolves to skill directory
- [ ] SkillLoader class-level Javadoc updated: `@path` file inlining removed, substitution applies only
      inside `!` directive strings
- [ ] `plugin/hooks/README.md` updated: `@path` references removed, Built-in Variables section reflects
      directive-only substitution
- [ ] `plugin/concepts/skill-loading.md` updated: `@path` removed from loading pipeline description
- [ ] `mvn -f client/pom.xml verify` passes with no errors or warnings
