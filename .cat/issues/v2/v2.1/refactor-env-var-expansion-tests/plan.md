# Plan: refactor-env-var-expansion-tests

## Current State
Three tests in `BlockWorktreeIsolationViolationTest` (`allowsRedirectWhenEnvVarExpandsToWorktreePath`,
`allowsRedirectWhenBareEnvVarExpandsToWorktreePath`, `blocksRedirectWhenEnvVarExpandsOutsideWorktree`) rely on
the real `$HOME` environment variable. If `$HOME` is unset, they skip via `SkipException`. The handler calls
`ShellParser.expandEnvVars(String)` which reads variables via `System.getenv()`.

## Target State
`ShellParser.expandEnvVars` has an overload that accepts a `Map<String, String> env` parameter.
`BlockWorktreeIsolationViolation` accepts an optional env map in a second constructor. The three tests pass a
controlled fake HOME value via this constructor, removing the `$HOME` dependency and `SkipException` guard.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — new constructor overload; existing constructor unchanged
- **Mitigation:** All existing tests continue to pass; three tests now run unconditionally

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java` — add `expandEnvVars(String, Map<String, String>)` overload; delegate existing single-arg method to it
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java` — add `Map<String, String> env` field; add second constructor accepting env map; use env in `expandEnvVars` call
- `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java` — refactor three tests to use fake HOME temp dir via new constructor; remove `SkipException` import and guards

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- In `ShellParser.java`, add `import java.util.Map;`
- Add `expandEnvVars(String target, Map<String, String> env)` with the lookup logic using `env.get(varName)`
  instead of `System.getenv(varName)`. Add `requireThat(env, "env").isNotNull()` validation.
  Javadoc: documents `env` param and `@throws NullPointerException if {@code target} or {@code env} are null`
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`
- Change existing `expandEnvVars(String target)` body to `return expandEnvVars(target, System.getenv());`
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`

### Job 2
- In `BlockWorktreeIsolationViolation.java`, add `import java.util.Map;`
- Add `private final Map<String, String> env;` field (null = use system environment)
- Change existing constructor to also set `this.env = null;`
- Add second public constructor `BlockWorktreeIsolationViolation(ClaudeHook scope, Map<String, String> env)`
  that sets `this.env = env` (with `requireThat(env, "env").isNotNull()`)
- In `check()`, replace `ShellParser.expandEnvVars(target)` with:
  ```java
  String expanded;
  if (env == null)
    expanded = ShellParser.expandEnvVars(target);
  else
    expanded = ShellParser.expandEnvVars(target, env);
  ```
- Update class Javadoc to mention the second constructor for injectable environment
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`

### Job 3
- In `BlockWorktreeIsolationViolationTest.java`, add `import java.util.Map;`
- Remove `import org.testng.SkipException;`
- Refactor `allowsRedirectWhenEnvVarExpandsToWorktreePath`:
  - Replace `String home = System.getenv("HOME"); if (home == null ...) throw SkipException(...)` with
    `Path fakeHome = Files.createTempDirectory("fake-home-");`
  - Change `Files.createTempDirectory(Path.of(home), "bwiv-test-")` to
    `Files.createTempDirectory(fakeHome, "bwiv-test-")`
  - Change `Path.of(home).relativize(...)` to `fakeHome.relativize(...)`
  - Add `Map<String, String> env = Map.of("HOME", fakeHome.toString());` before handler construction
  - Change `new BlockWorktreeIsolationViolation(scope)` to `new BlockWorktreeIsolationViolation(scope, env)`
  - Wrap entire test body in outer `try { ... } finally { TestUtils.deleteDirectoryRecursively(fakeHome); }`
    (the inner `try (TestClaudeHook scope = ...)` remains as-is; remove the old `finally` that deleted `projectPath`)
  - Files: `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`
- Refactor `allowsRedirectWhenBareEnvVarExpandsToWorktreePath` with identical changes, command uses `$HOME/`
  - Files: `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`
- Refactor `blocksRedirectWhenEnvVarExpandsOutsideWorktree` with identical structural changes,
  relativize uses `projectPath.resolve("plugin/file.txt")` against `fakeHome`
  - Files: `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`

## Post-conditions
- [ ] `mvn -f client/pom.xml verify -e` passes with exit code 0
- [ ] Three refactored tests no longer contain `System.getenv` or `SkipException`
- [ ] `SkipException` import removed from `BlockWorktreeIsolationViolationTest.java`
