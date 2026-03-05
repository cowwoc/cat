# Plan: fix-skillloader-non-ascii-characters

## Goal
Fix `SkillLoader.executeDirective()` to handle non-ASCII characters (e.g., box-drawing characters │, ─, →) in
skill file content without throwing `InvalidPathException`.

## Satisfies
None

## Current Behavior
When `SkillLoader` processes a skill file containing non-ASCII characters (e.g., box-drawing characters used in
architecture diagrams), `executeDirective()` passes strings containing those characters to `Paths.get()`. Java's
`UnixPath.encode()` cannot handle non-ASCII bytes and throws:
```
java.nio.file.InvalidPathException: Malformed input or input contains unmappable characters
```
This prevents `skill-builder-agent` and other preprocessor directives from loading skill files that contain visual
formatting such as `╭─── box ───╮` or `│──→` style diagrams.

## Target Behavior
`SkillLoader` handles non-ASCII characters in skill file content without crashing. Skill files containing
box-drawing characters, arrows, and other Unicode visual formatting load and process correctly.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Fix must not alter the actual path resolution logic for valid paths
- **Mitigation:** Scope fix narrowly to the string passed to `Paths.get()` in `executeDirective()`

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — fix `executeDirective()` to
  sanitize or properly handle non-ASCII characters before passing to `Paths.get()`
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
