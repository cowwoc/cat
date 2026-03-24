---
issue: 2.1-jvmenv-w1-claudeenv
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 1 of 5
---

# Plan: jvmenv-w1-claudeenv

## Objective

Absorb `ClaudeEnv` into `AbstractClaudeTool` (deleting the `ClaudeEnv` class), introduce a
`ClaudeTool` interface exposing its methods, rename `MainJvmScope`/`TestJvmScope` to
`MainClaudeTool`/`TestClaudeTool`, and refactor `AbstractJvmScope` to use protected abstract
accessors so its derived methods no longer depend on a `ClaudeEnv` parameter.

In addition, introduce a `ClaudeHook` interface hierarchy parallel to `ClaudeTool`. `ClaudeHook`
unifies the current `HookInput` and `HookOutput` classes into a single scope object, and
`MainClaudeHook` replaces `MainJvmScope` for hook handler and infrastructure CLI entry points.
`JvmScope` is trimmed to contain only truly generic JVM methods — all Claude-specific env var
methods move to `ClaudeTool` and `ClaudeHook` respectively.

**Naming convention:** Methods inside `ClaudeTool` (and `ClaudeHook`) omit "Claude" from their
names since the interface is already Claude-specific. The exception is methods that have a
CAT-equivalent counterpart (e.g., `getClaudeSessionPath()` vs `getCatSessionPath()`), where the
"Claude" prefix is kept to disambiguate.

## Scope

### Refactor AbstractJvmScope

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Remove the `ClaudeEnv claudeEnv` field and `AbstractJvmScope(ClaudeEnv)` constructor.
- Add `protected abstract` accessors for the four values derived methods need:
  - `protected abstract String getSessionId()`
  - `protected abstract Path getProjectPath()`
  - `protected abstract Path getPluginRoot()`
  - `protected abstract Path getEnvFile()`
- Update derived methods to call these abstract methods instead of `claudeEnv.*`:
  - `getCatDir()`: `getProjectPath().resolve(Config.CAT_DIR_NAME)`
  - `getClaudeSessionsPath()`: `getClaudeConfigDir().resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()))`
  - `getClaudeSessionPath()`: `getClaudeSessionsPath().resolve(getSessionId())`
  - `getCatWorkPath()`: `getProjectPath().resolve(".cat").resolve("work")`
  - `getCatSessionPath()`: `getCatWorkPath().resolve("sessions").resolve(getSessionId())`
  - `derivePluginPrefix()`: `getPluginRoot().toAbsolutePath().normalize()`

### Create ClaudeTool interface

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java`

- `public interface ClaudeTool`
- Declares:
  - `String getSessionId()`
  - `Path getProjectPath()`
  - `Path getPluginRoot()`
  - `Path getEnvFile()`

### Create AbstractClaudeTool (absorbs ClaudeEnv)

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`

- `public abstract class AbstractClaudeTool extends AbstractJvmScope implements ClaudeTool`
- Absorbs `ClaudeEnv`'s fields and env-reading logic directly:
  - `private final String sessionId` — read from `CLAUDE_SESSION_ID` env var
  - `private final Path projectPath` — read from `CLAUDE_PROJECT_DIR` env var
  - `private final Path pluginRoot` — read from `CLAUDE_PLUGIN_ROOT` env var
  - `private final Path envFile` — read from `CLAUDE_ENV_FILE` env var
- Protected constructor: `AbstractClaudeTool()` reads env vars and stores them.
- Implements the four `ClaudeTool` methods (and the four `AbstractJvmScope` abstract accessors)
  by returning the stored fields.
- `ClaudeEnv.java` is deleted — no longer needed.

### Rename MainJvmScope → MainClaudeTool

File: `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java` → `MainClaudeTool.java`

- `public final class MainClaudeTool extends AbstractClaudeTool`
- No-arg constructor: calls `super()`.
- Remove all `ConcurrentLazyReference` fields (`claudeProjectPath`, `claudePluginRoot`,
  `claudeSessionId`, `claudeEnvFile`) and their `@Override` methods.
- Delete `MainJvmScope.java`.

### Rename TestJvmScope → TestClaudeTool

File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` → `TestClaudeTool.java`

- `public class TestClaudeTool extends AbstractClaudeTool`
- Constructor accepts explicit values for the four fields (instead of reading env vars):
  override `AbstractClaudeTool`'s env-reading with injected test values.
- Remove `@Override getProjectPath()`, `@Override getClaudePluginRoot()`,
  `@Override getClaudeSessionId()`, `@Override getClaudeEnvFile()` method bodies.
- Delete `TestJvmScope.java`.

### Refactor JvmScope (Wave 2)

File: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Remove `getSessionId()` — moves to `ClaudeTool` (for tool processes) and is provided via hook
  input on the `ClaudeHook` side.
- Remove all infrastructure Claude path methods — they move to both `ClaudeTool` and `ClaudeHook`:
  `getProjectPath()`, `getPluginRoot()`, `getClaudeConfigDir()`, `getPluginPrefix()`,
  `getCatDir()`, `getClaudeSessionsPath()`, `getClaudeSessionPath(String)`, `getCatWorkPath()`,
  `getCatSessionPath(String)`.
- Retain only generic JVM methods: `getDisplayUtils()`, `getWorkDir()`, `getJsonMapper()`,
  `getYamlMapper()`, `getTerminalType()`, `getTimezone()`, `getDetectSequentialTools()`,
  `getPredictBatchOpportunity()`, `getUserIssues()`, `isClosed()`, `ensureOpen()`, `close()`.

### Create ClaudeHook interface (Wave 2)

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`

- `public interface ClaudeHook extends JvmScope`
- Declares all infrastructure Claude path methods (same set as `ClaudeTool`):
  `getProjectPath()`, `getPluginRoot()`, `getClaudeConfigDir()`, `getPluginPrefix()`,
  `getCatDir()`, `getClaudeSessionsPath()`, `getClaudeSessionPath(String)`, `getCatWorkPath()`,
  `getCatSessionPath(String)`
- Declares all public instance methods from current `HookInput`:
  `getCommand()`, `getString(String)`, `getString(String...)`, `getString(String, String)`,
  `getBoolean(String, boolean)`, `getObject(String)`, `getRaw()`, `isEmpty()`,
  `getSessionId()`, `getAgentId()`, `getCatAgentId(String)`, `getToolName()`,
  `getToolInput()`, `getToolResult()`, `getMapper()`, `getUserPrompt()`
- Declares all public instance methods from current `HookOutput`:
  `empty()`, `block(String)`, `block(String, String)`, `additionalContext(String, String)`,
  `toJson(ObjectNode)`
- Static utility method `wrapSystemReminder(String)` moves to a utility class (not on the interface)

### Create AbstractClaudeHook (Wave 2)

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`

- `public abstract class AbstractClaudeHook extends AbstractJvmScope implements ClaudeHook`
- Stores parsed `HookInput` data (JsonNode) internally — parsed from stdin at construction
- Reads infrastructure env vars at construction: `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`,
  `CLAUDE_CONFIG_DIR`, `TZ`
- Implements all `ClaudeHook` interface methods by delegating to stored input data and building
  output JSON
- Has `static AbstractClaudeHook readFromStdin(...)` factory that reads from stdin

### Create MainClaudeHook (Wave 2)

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java`

- `public final class MainClaudeHook extends AbstractClaudeHook`
- No-arg constructor reads from stdin and env vars via `super()`
- Replaces `MainJvmScope` — hook handlers and infrastructure CLIs (e.g., `GetSkill`) switch to
  this class
- Hook handler constructors that previously accepted `JvmScope scope` change to accept
  `ClaudeHook scope`

### Create TestClaudeHook (Wave 2)

New file: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java`

- `public class TestClaudeHook extends AbstractClaudeHook`
- Constructor accepts injectable values (hook JSON payload, project path, plugin root, etc.)
  so tests do not depend on stdin or environment variables

### Update hook handlers and GetSkill (Wave 2)

- Update each hook handler class to accept `ClaudeHook scope` instead of `JvmScope scope`
- Update `GetSkill` to instantiate `MainClaudeHook` instead of `MainJvmScope`
- Replace direct `HookInput` / `HookOutput` instantiation with calls to the unified
  `ClaudeHook` methods
- Delete `HookInput.java` and `HookOutput.java` after all callers are migrated

## Dependencies

- None (first in sequence)

## Post-conditions

**Wave 1 (ClaudeTool):**
- [ ] `ClaudeEnv.java` is deleted
- [ ] `ClaudeTool` interface exists with `getSessionId()`, `getProjectPath()`, `getPluginRoot()`, `getEnvFile()`
- [ ] `AbstractJvmScope` has four `protected abstract` accessors; no `ClaudeEnv` field or constructor parameter
- [ ] `AbstractClaudeTool` exists, extends `AbstractJvmScope`, implements `ClaudeTool`, stores env-var fields directly
- [ ] `MainClaudeTool` exists (replaces `MainJvmScope`), extends `AbstractClaudeTool`
- [ ] `TestClaudeTool` exists (replaces `TestJvmScope`), extends `AbstractClaudeTool`
- [ ] `MainJvmScope.java` and `TestJvmScope.java` are deleted
- [ ] `mvn -f client/pom.xml test` compiles (may have failures until w2–w4 update call sites)

**Wave 2 (ClaudeHook and JvmScope trimming):**
- [ ] `JvmScope` contains only generic JVM methods — `getSessionId()` and all infrastructure path methods removed
- [ ] `ClaudeHook` interface exists with all listed `HookInput`/`HookOutput` instance methods plus infrastructure path methods
- [ ] `AbstractClaudeHook` exists, extends `AbstractJvmScope`, implements `ClaudeHook`
- [ ] `MainClaudeHook` exists (replaces `MainJvmScope` for hook handlers and `GetSkill`), extends `AbstractClaudeHook`
- [ ] `TestClaudeHook` exists with injectable values for tests
- [ ] `HookInput.java` and `HookOutput.java` are deleted; all callers use `ClaudeHook` methods
- [ ] Hook handlers accept `ClaudeHook scope` instead of `JvmScope scope`
- [ ] `GetSkill` uses `MainClaudeHook` instead of `MainJvmScope`
- [ ] `mvn -f client/pom.xml test` passes with exit code 0

## Sub-Agent Waves

### Wave 1: Implement All Changes

This issue is small enough for a single implementation wave. All steps must be completed
and committed before the build verification step.

**Step 1: Refactor `AbstractJvmScope.java`**

Remove the `ClaudeEnv` dependency from `AbstractJvmScope` and replace with direct calls to
`getProjectPath()` and `getPluginRoot()` (which are already declared abstract in `JvmScope`):

- Remove `private final ClaudeEnv claudeEnv` field (line 67)
- Remove `protected AbstractJvmScope(ClaudeEnv claudeEnv)` constructor (lines 75–79); the class
  becomes default-constructible (implicit no-arg constructor)
- Remove `getClaudeEnv()` method implementation (lines 81–86)
- In `getCatDir()` (line 99): change `claudeEnv.getProjectPath()` → `getProjectPath()`
- In `getClaudeSessionsPath()` (line 131): change `claudeEnv.getProjectPath()` → `getProjectPath()`
- In `getCatWorkPath()` (line 165): change `claudeEnv.getProjectPath()` → `getProjectPath()`
- In `derivePluginPrefix()` (line 210): change `claudeEnv.getPluginRoot()` → `getPluginRoot()`
- Remove the `ClaudeEnv` import

**Step 2: Remove `getClaudeEnv()` from `JvmScope.java`**

Remove the `getClaudeEnv()` method declaration and its full Javadoc block (lines 139–151) from
`JvmScope.java`. Also remove the `ClaudeEnv` import from this file.

**Step 3: Create `ClaudeTool.java`**

Create `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java` with this content:

```java
/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;

/**
 * Exposes the Claude session environment values that a tool process receives at startup.
 * <p>
 * Implementations read these values from environment variables set by Claude Code when
 * spawning CLI tool processes.
 */
public interface ClaudeTool
{
  /**
   * Returns the Claude session ID.
   *
   * @return the session ID
   * @throws AssertionError if {@code CLAUDE_SESSION_ID} is not set in the environment
   */
  String getSessionId();

  /**
   * Returns the project's root directory.
   *
   * @return the project directory path
   * @throws AssertionError if {@code CLAUDE_PROJECT_DIR} is not set in the environment
   */
  Path getProjectPath();

  /**
   * Returns the Claude plugin root directory.
   *
   * @return the plugin root directory path
   * @throws AssertionError if {@code CLAUDE_PLUGIN_ROOT} is not set in the environment
   */
  Path getPluginRoot();

  /**
   * Returns the path to the Claude environment file.
   *
   * @return the environment file path
   * @throws AssertionError if {@code CLAUDE_ENV_FILE} is not set in the environment
   */
  Path getEnvFile();
}
```

**Step 4: Create `AbstractClaudeTool.java`**

Create `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`. This class
absorbs `ClaudeEnv`'s four fields and env-reading logic. The protected constructor takes all four
values explicitly so that `TestClaudeTool` can inject test values without reading from
`System.getenv()`. `MainClaudeTool` will read from env vars and pass them to this constructor.

```java
/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Abstract base class for Claude tool processes that reads session environment values
 * at construction time and exposes them via the {@link ClaudeTool} interface.
 * <p>
 * Subclasses that run in production (e.g., {@link MainClaudeTool}) pass values read from
 * {@code System.getenv()} to the protected constructor. Test subclasses pass injected values
 * to avoid host-environment dependencies.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudeTool extends AbstractJvmScope implements ClaudeTool
{
  private final String sessionId;
  private final Path projectPath;
  private final Path pluginRoot;
  private final Path envFile;

  /**
   * Creates a new abstract Claude tool scope with the given environment values.
   *
   * @param sessionId the Claude session ID
   * @param projectPath the project's root directory path
   * @param pluginRoot the Claude plugin root directory path
   * @param envFile the path to the Claude environment file
   * @throws NullPointerException if {@code projectPath}, {@code pluginRoot}, or {@code envFile} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  protected AbstractClaudeTool(String sessionId, Path projectPath, Path pluginRoot, Path envFile)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(envFile, "envFile").isNotNull();
    this.sessionId = sessionId;
    this.projectPath = projectPath;
    this.pluginRoot = pluginRoot;
    this.envFile = envFile;
  }

  @Override
  public String getSessionId()
  {
    ensureOpen();
    return sessionId;
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return projectPath;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }

  @Override
  public Path getEnvFile()
  {
    ensureOpen();
    return envFile;
  }
}
```

**Step 5: Update `SharedSecrets.java`**

Remove all `ClaudeEnvAccess`-related code from `SharedSecrets.java`:
- Remove `private static ClaudeEnvAccess claudeEnvAccess` field (line 37)
- Remove `setClaudeEnvAccess()` method (lines 133–137) and its Javadoc
- Remove `newClaudeEnv()` method (lines 149–154) and its Javadoc
- Remove `ClaudeEnvAccess` inner interface (lines 221–234) and its Javadoc
- Remove the `ClaudeEnv` import (line 5 area) and the `Map` import if no longer used

**Step 6: Delete `ClaudeEnv.java`**

Delete `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java`.

**Step 7: Create `MainClaudeTool.java`**

Create `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeTool.java`. This replaces
`MainJvmScope.java`. Reads the four ClaudeEnv fields from `System.getenv()` and passes them to
`AbstractClaudeTool`'s constructor. Also retains `CLAUDE_CONFIG_DIR`, `TZ`, `workDir`, and
`terminalType` handling from the old `MainJvmScope`.

The constructor must validate each env var before calling `super()`. Use the same pattern as the
original `ClaudeEnv` methods: fail with `AssertionError` if a required variable is not set.

Concretely:
- For each of the 4 env vars, read with `System.getenv()`, validate non-blank, then pass to `super()`
- Keep existing lazy refs: `claudeConfigDir` (reads `CLAUDE_CONFIG_DIR`, falls back to `~/.claude`),
  `terminalType` (calls `TerminalType.detect()`), `tz` (reads `TZ`, falls back to `"UTC"`)
- Keep `AtomicBoolean closed`
- Keep all overrides: `getClaudeConfigDir()`, `getTerminalType()`, `getTimezone()`, `getWorkDir()`,
  `isClosed()`, `close()`
- Remove `getProjectPath()` and `getPluginRoot()` overrides (now in `AbstractClaudeTool`)
- The `getWorkDir()` override reads from `System.getProperty("user.dir")` — keep as-is

**Step 8: Update all production callers of `MainJvmScope` → `MainClaudeTool`**

All production Java files that instantiate `MainJvmScope` must be updated before the class is deleted.
Use sed to perform bulk replacement across all affected files:

```bash
find client/src/main/java -name "*.java" ! -name "MainJvmScope.java" \
  -exec grep -l "MainJvmScope" {} \; | \
  xargs sed -i 's/MainJvmScope/MainClaudeTool/g'
```

This updates both the import declaration and the constructor call in each file.
Verify with: `grep -r "MainJvmScope" client/src/main/java --include="*.java" ! -name "MainJvmScope.java"`
The only remaining reference should be in `MainJvmScope.java` itself.

**Step 9: Delete `MainJvmScope.java`**

Delete `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`.

**Step 10: Create `TestClaudeTool.java`**

Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeTool.java`. This replaces
`TestJvmScope.java`. Key design changes:
- Replace `super(buildClaudeEnv(...))` calls with `super(sessionId, projectPath, pluginRoot, envFile)`
- Use `"00000000-0000-0000-0000-000000000000"` as session ID in all constructors
- Use `projectPath.resolve(".env")` as the envFile placeholder
- Remove `buildClaudeEnv()` method and `TempDirBundle` record; use a `TempDirBundle` record that holds
  only `projectPath`, `pluginRoot`, `claudeConfigDir` (no ClaudeEnv field)
- Remove `getProjectPath()` and `getPluginRoot()` overrides (provided by `AbstractClaudeTool`)
- Keep `getPluginPrefix()` returning `"cat"`
- Keep `claudeConfigDir`, `terminalType`, `workDir` fields and their overrides
- Keep `copyEmojiWidthsIfNeeded()` and `findWorkspaceRoot()` static helpers
- Constructor signatures remain identical (same public API), only internals change

The five constructor variants translate as follows:
1. `TestClaudeTool()` — calls `createTempDirs()`, which builds the 4 values then calls `this(bundle)`
2. `TestClaudeTool(TempDirBundle)` — private; calls `super(SESSION_ID, bundle.projectPath(), bundle.pluginRoot(), bundle.projectPath().resolve(".env"))`
3. `TestClaudeTool(Path, Path)` — calls `this(path, path, path)` (3-arg)
4. `TestClaudeTool(Path, Path, Path workDir)` — calls `super(SESSION_ID, projectPath, pluginRoot, projectPath.resolve(".env"))`
5. `TestClaudeTool(Path, Path, TerminalType)` — calls `this(path, path, type, path)` (4-arg)
6. `TestClaudeTool(Path, Path, TerminalType, Path workDir)` — calls `super(SESSION_ID, projectPath, pluginRoot, projectPath.resolve(".env"))`

**Step 11: Update all test callers of `TestJvmScope` → `TestClaudeTool`**

All test Java files that instantiate `TestJvmScope` must be updated before the class is deleted.
Use sed to perform bulk replacement across all affected files:

```bash
find client/src/test/java -name "*.java" ! -name "TestJvmScope.java" ! -name "TestJvmScopeTest.java" \
  -exec grep -l "TestJvmScope" {} \; | \
  xargs sed -i 's/TestJvmScope/TestClaudeTool/g'
```

This updates import declarations, constructor calls, and comments referencing `TestJvmScope`.
Verify with: `grep -r "TestJvmScope" client/src/test/java --include="*.java" ! -name "TestJvmScope.java" ! -name "TestJvmScopeTest.java"`
No references should remain in other test files.

**Step 12: Delete `TestJvmScope.java`**

Delete `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`.

**Step 13: Create `TestClaudeToolTest.java`** (replaces `TestJvmScopeTest.java`)

Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeToolTest.java`. Read the
full content of `TestJvmScopeTest.java` first, then write the updated version:
- Rename class from `TestJvmScopeTest` to `TestClaudeToolTest`
- Replace all `TestJvmScope` references with `TestClaudeTool`
- Replace `scope.getClaudeEnv().getProjectPath()` → `scope.getProjectPath()`
- Replace `scope.getClaudeEnv().getPluginRoot()` → `scope.getPluginRoot()`
- Replace `ClaudeEnv claudeEnv = scope.getClaudeEnv()` and subsequent `claudeEnv.getX()` calls
  with direct `scope.getX()` calls
- Replace test method `getClaudeEnvReturnsNonNull` → `getProjectPathReturnsInjectedValue` (verifies
  `scope.getProjectPath()` equals the injected `tempDir` path)
- Replace test method `getClaudeEnvThrowsAfterClose` → `getProjectPathThrowsAfterClose` (verifies
  `scope.getProjectPath()` throws `IllegalStateException` after close)
- Replace test method `getClaudeEnvReturnsInjectedValues` → `injectedValuesReturnedDirectly`
  (verifies `scope.getProjectPath()` equals `projectPath` and `scope.getPluginRoot()` equals `pluginRoot`)
- Replace test method `derivedPathMethodsDelegateToClaudeEnv` → `derivedPathMethodsUseProjectPath`
  (change `scope.getClaudeEnv().getProjectPath()` → `scope.getProjectPath()`)
- Remove any `ClaudeEnv` imports

**Step 14: Delete `TestJvmScopeTest.java`**

Delete `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScopeTest.java`.

**Step 15: Update `EnforceJvmScopeEnvAccessTest.java`**

Update the whitelist in both test methods:
- In `onlyAllowedFilesCallSystemGetenv()`: replace filter for `ClaudeEnv.java` with filter for
  `AbstractClaudeTool.java` — wait, `AbstractClaudeTool` does NOT call `System.getenv()` (the env
  reads are in `MainClaudeTool`). So simply REMOVE the `ClaudeEnv.java` filter and change
  `MainJvmScope.java` filter to `MainClaudeTool.java`.
- In `whitelistedFilesCallSystemGetenv()`: update the `whitelistedFiles` array to replace
  `io/github/cowwoc/cat/hooks/ClaudeEnv.java` with nothing and change
  `io/github/cowwoc/cat/hooks/MainJvmScope.java` to `io/github/cowwoc/cat/hooks/MainClaudeTool.java`
- Update the error message string to reference `MainClaudeTool.java` instead of `ClaudeEnv.java`
  and `MainJvmScope.java`
- Update the class Javadoc to reference `MainClaudeTool.java` instead of `MainJvmScope.java` and
  `ClaudeEnv.java`

**Step 16: Update convention files**

Update `.claude/rules/jackson.md`:
- Line 9: `TestJvmScope` → `TestClaudeTool`
- Line 10: `MainJvmScope` → `MainClaudeTool`

Update `.claude/rules/java.md` (all in the worktree at `/workspace/.cat/work/worktrees/2.1-jvmenv-w1-claudeenv`):
- In the "Scope implementations" bullet list: the text already says `MainClaudeTool` and `TestClaudeTool` — verify and keep as-is
- In the Environment Variable Access table (line ~1435): change the cell "scope.getSessionId()` via `MainClaudeTool`" to
  "`scope.getSessionId()` via `ClaudeTool`" — `getSessionId()` is declared on the `ClaudeTool` interface, not the concrete type
- In the explanatory paragraph (line ~1441): change "use `MainClaudeTool` which reads env vars" to
  "use `MainClaudeTool` (a `ClaudeTool` implementation) which reads env vars"
- In the good code example (line ~1448): change `try (MainClaudeTool scope = new MainClaudeTool())` to
  `try (JvmScope scope = new MainClaudeTool())` — use the interface as the declared type
- In the AssertionError example (lines ~1506-1507): remove the `ClaudeEnv` reference comment and code. Replace:
  ```
  // Use ClaudeEnv to safely read session configuration
  String sessionId = new ClaudeEnv().getSessionId();
  if (sessionId.isBlank())
    throw new AssertionError("CLAUDE_SESSION_ID is not set");
  ```
  With a comment-only approach that doesn't reference `ClaudeEnv`:
  ```
  // Good - AssertionError for environment invariant (caller cannot prevent or query)
  String sessionId = scope.getSessionId();  // throws AssertionError if env var not set
  ```
- In test isolation rule 6 (line ~1697-1698): change "Use `TestJvmScope`, not `MainJvmScope`" to
  "Use `TestClaudeTool`, not `MainClaudeTool`" and update the description accordingly.
  Change "Use `TestJvmScope(tempDir, tempDir)`" to "Use `TestClaudeTool(tempDir, tempDir)`"
- In the code example comment (line ~1712): change "// Good - self-contained test with TestJvmScope" to
  "// Good - self-contained test with TestClaudeTool"
- In the code example (line ~1717): change `new TestJvmScope(tempDir, tempDir)` to `new TestClaudeTool(tempDir, tempDir)`

Note: `.claude/rules/` files per CLAUDE.md use `config:` commit type, but since convention changes belong with their
application, these changes will be included in the same commit as the Java implementation (Step 17, using `refactor:`).

**Step 17: Commit implementation work**

Commit all Java source changes and convention file updates with message
`refactor: absorb ClaudeEnv into AbstractClaudeTool; introduce ClaudeTool interface`.

**Step 18: Run build verification**

Run `mvn -f client/pom.xml verify` from the worktree root (NOT from /workspace).

All tests must pass. If there are compilation errors due to remaining references to `ClaudeEnv`,
`TestJvmScope`, `MainJvmScope`, or `getClaudeEnv()`, fix them before proceeding.

Common issues to check:
- Any test file that imports `ClaudeEnv` or `TestJvmScope` needs updating
- Any production file that imports `ClaudeEnv` needs updating (should have been caught in steps above)
- `module-info.java` exports — no changes needed since all new classes are in the same package

### Wave 2: ClaudeHook Hierarchy

This wave introduces the `ClaudeHook` interface, trims `JvmScope` to generic methods only, and
replaces `HookInput`/`HookOutput` with unified hook scope objects.

**Step 19: Trim `JvmScope.java`**

Remove from `JvmScope`:
- `getSessionId()` declaration and its Javadoc
- `getProjectPath()`, `getPluginRoot()`, `getClaudeConfigDir()`, `getPluginPrefix()`,
  `getCatDir()`, `getClaudeSessionsPath()`, `getClaudeSessionPath(String)`,
  `getCatWorkPath()`, `getCatSessionPath(String)` declarations and their Javadocs

These methods will be declared on `ClaudeTool` and `ClaudeHook` instead.

**Step 20: Add infrastructure path methods to `ClaudeTool.java`**

Add declarations (with Javadoc) to `ClaudeTool`:
- `Path getProjectPath()`
- `Path getPluginRoot()`
- `Path getClaudeConfigDir()`
- `String getPluginPrefix()`
- `Path getCatDir()`
- `Path getClaudeSessionsPath()`
- `Path getClaudeSessionPath(String sessionId)`
- `Path getCatWorkPath()`
- `Path getCatSessionPath(String sessionId)`

These replace the methods removed from `JvmScope`.

**Step 21: Create `ClaudeHook.java`**

Create `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`:
- `public interface ClaudeHook extends JvmScope`
- Declares all infrastructure path methods (same set added to `ClaudeTool` in Step 20)
- Declares all `HookInput` public instance methods: `getCommand()`, `getString(String)`,
  `getString(String...)`, `getString(String, String)`, `getBoolean(String, boolean)`,
  `getObject(String)`, `getRaw()`, `isEmpty()`, `getSessionId()`, `getAgentId()`,
  `getCatAgentId(String)`, `getToolName()`, `getToolInput()`, `getToolResult()`,
  `getMapper()`, `getUserPrompt()`
- Declares all `HookOutput` public instance methods: `empty()`, `block(String)`,
  `block(String, String)`, `additionalContext(String, String)`, `toJson(ObjectNode)`

**Step 22: Create `AbstractClaudeHook.java`**

Create `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`:
- `public abstract class AbstractClaudeHook extends AbstractJvmScope implements ClaudeHook`
- Constructor reads `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_CONFIG_DIR`, `TZ`
  from env vars and stores them as fields; also parses hook JSON from the provided `JsonNode`
- Implements all infrastructure path methods using stored env-var fields
- Implements all `ClaudeHook` input methods by delegating to the stored `JsonNode`
- Implements all `ClaudeHook` output methods by constructing the hook JSON response
- Includes `static AbstractClaudeHook readFromStdin(JsonMapper mapper)` factory that reads
  from `System.in` and calls the protected constructor

**Step 23: Create `MainClaudeHook.java`**

Create `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java`:
- `public final class MainClaudeHook extends AbstractClaudeHook`
- No-arg constructor reads hook JSON from stdin via `AbstractClaudeHook.readFromStdin()`
- Used by all hook handlers and infrastructure CLI entry points (e.g., `GetSkill`)

**Step 24: Create `TestClaudeHook.java`**

Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java`:
- `public class TestClaudeHook extends AbstractClaudeHook`
- Constructor accepts `(JsonNode hookPayload, Path projectPath, Path pluginRoot,
  Path claudeConfigDir)` — injectable values that avoid stdin and env var reads
- Add convenience builder or factory methods for common hook payload shapes

**Step 25: Update hook handlers to use `ClaudeHook`**

For each hook handler class (find with `grep -r "JvmScope" client/src/main/java --include="*.java" -l`):
- Change constructor parameter type from `JvmScope scope` to `ClaudeHook scope`
- Replace `new HookInput(scope, ...)` / `new HookOutput(scope)` with direct calls to
  `scope.getCommand()`, `scope.block(...)`, etc.
- Update `main()` to instantiate `MainClaudeHook` instead of `MainJvmScope`

**Step 26: Update `GetSkill` to use `MainClaudeHook`**

In `GetSkill.java`:
- Change `try (MainJvmScope scope = new MainJvmScope())` to
  `try (MainClaudeHook scope = new MainClaudeHook())`
- Update the import

**Step 27: Delete `HookInput.java` and `HookOutput.java`**

After all callers are migrated:
- Delete `client/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java`
- Delete `client/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java`

Verify no remaining references:
```bash
grep -r "HookInput\|HookOutput" client/src/main/java --include="*.java"
```

**Step 28: Update `EnforceJvmScopeEnvAccessTest.java` for Wave 2**

Update whitelist to replace any `HookInput.java`/`HookOutput.java` entries with
`AbstractClaudeHook.java` (if `AbstractClaudeHook` calls `System.getenv()`). Update Javadoc to
reflect the new class names.

**Step 29: Commit Wave 2 implementation**

Commit all Wave 2 Java source changes with message
`refactor: introduce ClaudeHook hierarchy; replace HookInput/HookOutput; trim JvmScope`.

**Step 30: Run final build verification**

Run `mvn -f client/pom.xml verify` from the worktree root (NOT from /workspace).

All tests must pass. If there are compilation errors due to remaining references to `HookInput`,
`HookOutput`, or removed `JvmScope` methods, fix them before proceeding.

### Wave 3: Fix Missing Criteria (Iteration 1)

**Step 31: Fix `MainClaudeHook.readStdin()` to eliminate direct `JsonMapper.builder().build()` call**

`MainClaudeHook.readStdin()` constructs a `JsonMapper` via `JsonMapper.builder().build()` (line 62), violating the
convention that only `AbstractJvmScope` is the permitted call site for `JsonMapper.builder()`.

The fix: add a package-private static method `createStdinMapper()` in `AbstractClaudeHook` that constructs and returns
a minimal `JsonMapper` (no `INDENT_OUTPUT`), and update `MainClaudeHook.readStdin()` to call
`AbstractClaudeHook.createStdinMapper()` instead of constructing its own mapper.

Concretely:
- In `AbstractClaudeHook.java`: add a static method `static JsonMapper createStdinMapper()` that calls
  `JsonMapper.builder().build()` and returns the result. Add a comment explaining this is the permitted call site for
  the stdin parse mapper (a separate mapper from the shared instance in `AbstractJvmScope`).
- In `MainClaudeHook.java`: replace the two lines in `readStdin()`:
  ```java
  tools.jackson.databind.json.JsonMapper mapper =
    tools.jackson.databind.json.JsonMapper.builder().build();
  ```
  with:
  ```java
  JsonMapper mapper = AbstractClaudeHook.createStdinMapper();
  ```
  Also remove the fully-qualified import of `tools.jackson.databind.json.JsonMapper` from `readStdin()` if it is
  no longer needed (add a top-level `import tools.jackson.databind.json.JsonMapper` if one is not already present).

**Step 32: Fix `AbstractJvmScope` abstract method visibility and completeness**

The Wave 1 post-condition requires `AbstractJvmScope` to have four `protected abstract` accessors
(`getSessionId()`, `getProjectPath()`, `getPluginRoot()`, `getEnvFile()`). Currently:
- `getProjectPath()`, `getPluginRoot()`, and `getClaudeConfigDir()` are declared `public abstract` (not `protected abstract`)
- `getSessionId()` and `getEnvFile()` are absent from `AbstractJvmScope`

Concretely in `AbstractJvmScope.java`:
- Change `public abstract Path getProjectPath()` to `protected abstract Path getProjectPath()`
- Change `public abstract Path getPluginRoot()` to `protected abstract Path getPluginRoot()`
- Change `public abstract Path getClaudeConfigDir()` to `protected abstract Path getClaudeConfigDir()`
- Add `protected abstract String getSessionId()` with Javadoc: returns the Claude session ID; throws
  `AssertionError` if `CLAUDE_SESSION_ID` is not set
- Add `protected abstract Path getEnvFile()` with Javadoc: returns the path to the Claude environment file;
  throws `AssertionError` if `CLAUDE_ENV_FILE` is not set
- Remove the Javadoc from `getProjectPath()`, `getPluginRoot()`, and `getClaudeConfigDir()` in
  `AbstractJvmScope` (they are internal protected hooks; public contract is declared on `ClaudeTool`/`ClaudeHook`)
- Update any subclass overrides in `MainClaudeHook.java`, `AbstractClaudeTool.java`, and
  `AbstractClaudeHook.java` to add `@Override` if not already present (the compiler will flag missing ones)

**Step 33: Run build verification after Wave 3 fixes**

Run `mvn -f client/pom.xml verify` from the worktree root (NOT from /workspace).

All tests must pass. If there are compilation errors due to the visibility change on the abstract methods
(e.g., callers outside the package that previously accessed `getProjectPath()` directly on an `AbstractJvmScope`
reference), fix them by accessing those methods via the `ClaudeTool` or `ClaudeHook` interfaces instead.
