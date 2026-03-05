# Plan: fix-skillloader-non-ascii-characters

## Goal
Fix `SkillLoader.executeDirective()` to handle non-ASCII characters (e.g., box-drawing characters │, ─, →) in
skill file content without throwing `InvalidPathException`.

## Satisfies
None

## Current Behavior
`processPreprocessorDirectives()` scans the entire skill file content with `PREPROCESSOR_DIRECTIVE_PATTERN`
(`!`"([^\"]+)"(\s+[^`]+)?``) but does not skip fenced code blocks. When a skill file contains a code block diagram
with box-drawing characters that also includes a `!`"` sequence (e.g., showing a preprocessor directive inside a
diagram), the regex matches across lines:

```
│                      │     │ !`"${CLAUDE_PLUGIN_ROOT}/    │
│ Returns content via  │──→  │  client/bin/get-output" TYPE`│
```

Here, `[^\"]+` greedily captures everything from the `"` on the first line to the `"` on the second line, including
the `│` and `→` box-drawing characters. After variable substitution, `Paths.get()` receives a multi-line string
containing non-ASCII characters and throws:
```
java.nio.file.InvalidPathException: Malformed input or input contains unmappable characters
```

## Target Behavior
`PREPROCESSOR_DIRECTIVE_PATTERN` does not match across line boundaries. The path capture group `[^"\n]+` and
arguments capture group `[^`\n]+` both exclude newlines, preventing cross-line matches in code block diagrams.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Fix must not alter the actual path resolution logic for valid paths
- **Mitigation:** Preprocessor directives are always single-line; adding `\n` to the exclusion set is safe

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — fix `PREPROCESSOR_DIRECTIVE_PATTERN`
  regex to exclude newlines from capture groups
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/SkillLoaderTest.java` — add test case for skill
  files containing non-ASCII characters

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Read `SkillLoader.java` and locate `executeDirective()` method to understand exact failure point
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Fix `executeDirective()` to properly handle non-ASCII characters in content strings before passing to
  `Paths.get()`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Add a test that loads a skill file containing box-drawing characters and verifies no exception is thrown
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/util/SkillLoaderTest.java`
- Run `mvn -f client/pom.xml test` and verify all tests pass

## Post-conditions
- `SkillLoader` loads `plugin/skills/work-with-issue-agent/first-use.md` without `InvalidPathException`
- `skill-builder-agent` can process skill files containing non-ASCII box-drawing characters
- All existing tests still pass
- New test covering non-ASCII skill file content passes
