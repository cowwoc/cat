# Plan

## Goal

Refactor the scope hierarchy to:

1. Move `getPluginRoot()` and `getPluginPrefix()` from `JvmScope`/`AbstractJvmScope` into `ClaudeHook` and `ClaudeTool` (and their abstract implementations)
2. Remove `ClaudeScope` interface and `AbstractClaudeScope` class entirely, duplicating their methods and field implementations into both `AbstractClaudeHook` and `AbstractClaudeTool` (duplication is intentional — these two hierarchies serve different execution contexts)
3. Change `AbstractClaudeStatusline` to extend `AbstractJvmScope` directly (no longer needs plugin-root or Claude-config concerns)
4. Remove `CLAUDE_PLUGIN_ROOT` from `MainClaudeStatusline` — it reads `projectPath` from the stdin JSON `workspace.project_dir` field instead of env var

This fixes the statusline error "CLAUDE_PLUGIN_ROOT is not set" because the statusline execution context does not provide plugin environment variables.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged for ClaudeHook and ClaudeTool consumers
- [ ] Tests passing
- [ ] `JvmScope` no longer declares `getPluginRoot()` or `getPluginPrefix()`
- [ ] `ClaudeScope` interface and `AbstractClaudeScope` class deleted
- [ ] `getPluginRoot()`, `getPluginPrefix()`, `getClaudeConfigPath()`, `getClaudeSessionsPath()`, `getClaudeSessionPath()` declared on both `ClaudeHook` and `ClaudeTool`
- [ ] `AbstractClaudeHook` and `AbstractClaudeTool` contain the implementations formerly in `AbstractJvmScope` and `AbstractClaudeScope`
- [ ] `AbstractClaudeStatusline` extends `AbstractJvmScope` directly
- [ ] `MainClaudeStatusline` does not read `CLAUDE_PLUGIN_ROOT` from environment
- [ ] E2E verification: statusline command runs without error when `CLAUDE_PLUGIN_ROOT` is unset

## Jobs

### Job 1: Refactor scope hierarchy

All changes are in a single job because they are tightly interdependent.

#### Step 1: Remove `getPluginRoot()` and `getPluginPrefix()` from `JvmScope` interface

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Delete the `getPluginRoot()` method declaration (lines 109-116)
- Delete the `getPluginPrefix()` method declaration (lines 118-127)

#### Step 2: Remove plugin-root fields and methods from `AbstractJvmScope`

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Remove the `pluginRoot` field (line 74)
- Remove the `pluginPrefix` lazy ref field (lines 71-72)
- Remove the `pluginRoot` parameter from the constructor signature: change
  `protected AbstractJvmScope(Path projectPath, Path pluginRoot)` to
  `protected AbstractJvmScope(Path projectPath)`. Remove the `requireThat(pluginRoot, ...)` validation and
  `this.pluginRoot = pluginRoot` assignment
- Delete the `getPluginRoot()` method override (lines 98-103)
- Delete the `getPluginPrefix()` method override (lines 143-148)
- Delete the `derivePluginPrefix()` private method (lines 150-170)

#### Step 3: Add methods to `ClaudeHook` interface

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`

- Change `extends ClaudeScope` to `extends JvmScope` in the interface declaration
- Add the following method declarations (copied from `JvmScope` and `ClaudeScope`):
  - `Path getPluginRoot()` with Javadoc from the old `JvmScope` declaration
  - `String getPluginPrefix()` with Javadoc from the old `JvmScope` declaration
  - `Path getClaudeConfigPath()` with Javadoc from the old `ClaudeScope` declaration
  - `Path getClaudeSessionsPath()` with Javadoc from the old `ClaudeScope` declaration
  - `Path getClaudeSessionPath(String sessionId)` with Javadoc from the old `ClaudeScope` declaration

#### Step 4: Add same methods to `ClaudeTool` interface

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java`

- Change `extends ClaudeScope` to `extends JvmScope` in the interface declaration
- Remove the `@Override Path getProjectPath()` redeclaration (inherited from `JvmScope`)
- Remove the `@Override Path getPluginRoot()` redeclaration (will be declared fresh below)
- Add the following method declarations (same as Step 3):
  - `Path getPluginRoot()` with Javadoc
  - `String getPluginPrefix()` with Javadoc
  - `Path getClaudeConfigPath()` with Javadoc
  - `Path getClaudeSessionsPath()` with Javadoc
  - `Path getClaudeSessionPath(String sessionId)` with Javadoc

#### Step 5: Update `AbstractClaudeHook` to extend `AbstractJvmScope` directly

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`

- Change `extends AbstractClaudeScope implements ClaudeHook` to `extends AbstractJvmScope implements ClaudeHook`
- Add fields absorbed from `AbstractClaudeScope` and `AbstractJvmScope`:
  - `private final Path pluginRoot;`
  - `private final ConcurrentLazyReference<String> pluginPrefix;` (with `this::derivePluginPrefix` supplier)
  - `private final Path claudeConfigPath;`
- Update the constructor signature: keep `(JsonNode data, Path projectPath, Path pluginRoot, Path claudeConfigPath)`.
  Change the `super(...)` call from `super(projectPath, pluginRoot, claudeConfigPath)` to `super(projectPath)`.
  Add field assignments: `this.pluginRoot = pluginRoot;`, `requireThat(claudeConfigPath, ...).isNotNull();`,
  `this.claudeConfigPath = claudeConfigPath;`
- Add `pluginRoot` validation: `requireThat(pluginRoot, "pluginRoot").isNotNull();`
- Copy the `derivePluginPrefix()` private method from `AbstractJvmScope` (the method body that navigates
  parent directories to extract the prefix name)
- Add method implementations absorbed from `AbstractClaudeScope`:
  - `getPluginRoot()` returning `pluginRoot` (with `ensureOpen()` guard)
  - `getPluginPrefix()` returning `pluginPrefix.getValue()` (with `ensureOpen()` guard)
  - `getClaudeConfigPath()` returning `claudeConfigPath` (with `ensureOpen()` guard)
  - `getClaudeSessionsPath()` returning
    `claudeConfigPath.resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()))` (with
    `ensureOpen()` guard)
  - `getClaudeSessionPath(String sessionId)` returning `getClaudeSessionsPath().resolve(sessionId)` (with
    `requireThat(sessionId, ...).isNotBlank()`)
- Add required imports: `ConcurrentLazyReference`, `requireThat` for java validators (already has jackson one)

#### Step 6: Update `AbstractClaudeTool` to extend `AbstractJvmScope` directly

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`

- Change `extends AbstractClaudeScope implements ClaudeTool` to `extends AbstractJvmScope implements ClaudeTool`
- Add fields (same as Step 5, intentional duplication):
  - `private final Path pluginRoot;`
  - `private final ConcurrentLazyReference<String> pluginPrefix;`
  - `private final Path claudeConfigPath;`
- Update the constructor: change `super(projectPath, pluginRoot, claudeConfigPath)` to `super(projectPath)`.
  Add field assignments and validations for `pluginRoot` and `claudeConfigPath`
- Copy `derivePluginPrefix()` private method from `AbstractJvmScope`
- Add the same five method implementations as Step 5:
  `getPluginRoot()`, `getPluginPrefix()`, `getClaudeConfigPath()`, `getClaudeSessionsPath()`,
  `getClaudeSessionPath(String sessionId)`
- Add required imports: `ConcurrentLazyReference`, `Path`

#### Step 7: Update `AbstractClaudeStatusline` to extend `AbstractJvmScope` directly

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeStatusline.java`

- Change `extends AbstractClaudeScope implements ClaudeStatusline` to
  `extends AbstractJvmScope implements ClaudeStatusline`
- Remove the `pluginRoot` and `claudeConfigPath` parameters from both constructors:
  - 3-param constructor: `protected AbstractClaudeStatusline(Path projectPath, Path pluginRoot, Path claudeConfigPath)`
    becomes `protected AbstractClaudeStatusline(Path projectPath)`
  - 4-param constructor:
    `protected AbstractClaudeStatusline(Path projectPath, Path pluginRoot, Path claudeConfigPath, InputStream stdin)`
    becomes `protected AbstractClaudeStatusline(Path projectPath, InputStream stdin)`
- Update `super(...)` calls: from `super(projectPath, pluginRoot, claudeConfigPath)` to `super(projectPath)`
- Remove the `ClaudeScope`/`AbstractClaudeScope` import if present
- Update Javadoc on constructors to remove `pluginRoot` and `claudeConfigPath` `@param`/`@throws` tags

#### Step 8: Update `MainClaudeStatusline` to not require `CLAUDE_PLUGIN_ROOT`

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeStatusline.java`

- Change the constructor: instead of reading `CLAUDE_PROJECT_DIR` and `CLAUDE_PLUGIN_ROOT` from env vars and
  `CLAUDE_CONFIG_DIR` from env, read `projectPath` from the stdin JSON `workspace.project_dir` field.
  The new approach:
  1. Read all bytes from stdin into a String first
  2. Parse the JSON to extract `workspace.project_dir` for the `projectPath`
  3. Call `super(projectPath, stdin)` where stdin is reconstructed from the already-read bytes (use a
     `ByteArrayInputStream`)
  - Actually, since `super(projectPath, stdin)` reads stdin too, we need to restructure: read the bytes once,
    extract `projectPath` from the JSON, then pass a `ByteArrayInputStream` wrapping those bytes to the super
    constructor
  - Use a static factory pattern or read bytes in a static helper called before `super()`:
    ```
    private static byte[] stdinBytes;
    ```
    Or better: use a static helper that returns both projectPath and bytes, then call super with a
    ByteArrayInputStream
- Remove `getEnvVar("CLAUDE_PLUGIN_ROOT")` call
- Remove `createClaudeConfigPath()` call (no longer needed since AbstractClaudeStatusline does not take
  claudeConfigPath)
- Remove the `getEnvVar` and `createClaudeConfigPath` private static methods if they become unused
- Keep `getEnvVar("CLAUDE_PROJECT_DIR")` OR replace it with reading from stdin JSON `workspace.project_dir`
  (the latter is preferred per the goal)
- The constructor pattern becomes:
  ```java
  public MainClaudeStatusline(InputStream stdin) throws IOException
  {
    this(stdin.readAllBytes());
  }

  private MainClaudeStatusline(byte[] stdinBytes) throws IOException
  {
    super(extractProjectPath(stdinBytes), new ByteArrayInputStream(stdinBytes));
  }

  private static Path extractProjectPath(byte[] stdinBytes)
  {
    // Parse JSON, extract workspace.project_dir, return Path.of(value)
    // Fallback to CLAUDE_PROJECT_DIR env var if workspace.project_dir is absent
  }
  ```
- Remove `CLAUDE_PLUGIN_ROOT` from the class Javadoc

#### Step 9: Replace `MainJvmScope` callers with `MainClaudeTool`, then delete `MainJvmScope`

**Files to update:**

1. `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatusAlignmentValidator.java`:
   - Change `import MainJvmScope` to `import MainClaudeTool`
   - Change `new MainJvmScope()` to `new MainClaudeTool()` in `main()`
   - Change `JvmScope scope` to `ClaudeTool scope` in `main()` try-with-resources (since `MainClaudeTool`
     implements `ClaudeTool`)

2. `client/src/main/java/io/github/cowwoc/cat/hooks/util/MarkdownWrapper.java`:
   - Same changes as StatusAlignmentValidator: `MainJvmScope` to `MainClaudeTool`, `JvmScope` to `ClaudeTool`

3. `client/src/test/java/io/github/cowwoc/cat/hooks/test/JvmScopePathResolutionTest.java`:
   - Update any usage of `MainJvmScope` to use `TestClaudeTool` instead (tests should not use Main* classes)

4. **Delete** `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`

Note: `StatusAlignmentValidator.run()` and `MarkdownWrapper.run()` accept `JvmScope` as parameter type. Since
these methods do not call `getPluginRoot()` or `getPluginPrefix()`, the parameter type can remain `JvmScope`.
Only the `main()` method that creates the scope needs to change to `MainClaudeTool`.

#### Step 10: Delete `ClaudeScope` and `AbstractClaudeScope`

- **Delete** `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeScope.java`
- **Delete** `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeScope.java`
- Grep for any remaining references to `ClaudeScope` or `AbstractClaudeScope` across the codebase and remove
  them (imports, Javadoc `{@link}` references, etc.)

#### Step 11: Update callers that used `JvmScope.getPluginRoot()` or `JvmScope.getPluginPrefix()`

After Steps 1-2, `JvmScope` no longer has `getPluginRoot()`/`getPluginPrefix()`. Callers that declared their
scope field as `JvmScope` but called these methods must be updated to use a more specific type.

**Callers that already use `ClaudeHook` or `ClaudeTool` (no change needed):**
- `GetSkill` (ClaudeTool) -- already correct
- `InjectEnv` (ClaudeHook) -- already correct
- `InjectMainAgentRules` (ClaudeHook) -- already correct
- `InjectSubAgentRules` (ClaudeHook) -- already correct
- `CheckDataMigration` (ClaudeHook) -- already correct
- `CheckUpdateAvailable` (ClaudeHook) -- already correct
- `RequireSkillForCommand` (ClaudeHook) -- already correct

**Callers that use `JvmScope` and call `getPluginRoot()` -- must widen to `ClaudeHook` or `ClaudeTool`:**

1. `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java`:
   - Field: `private final JvmScope scope` -- check constructor callers to determine whether to use
     `ClaudeHook` or `ClaudeTool`. LicenseValidator is called from SessionStartHook (ClaudeHook context).
     Change field type to `ClaudeHook`.

2. `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/Entitlements.java`:
   - Check what type `scope` is declared as. It calls `scope.getPluginRoot()`. Change to `ClaudeHook` or
     `ClaudeTool` based on callers.

3. `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`:
   - Field: `private final JvmScope scope`. Calls `scope.getPluginRoot()`. Change to `ClaudeTool` (since
     MergeAndCleanup is a skill CLI tool).

4. `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java`:
   - Calls `scope.getPluginRoot()`. DisplayUtils is constructed from `AbstractJvmScope` via a lazy ref that
     passes `this`. After this refactor, `AbstractJvmScope` no longer has `getPluginRoot()`.
     DisplayUtils must be updated: either change its constructor to accept `ClaudeHook`/`ClaudeTool`, or
     restructure. Since DisplayUtils is used by the statusline too (which has no plugin root), this needs
     careful handling. **Solution:** Make DisplayUtils accept `Path pluginRoot` as a nullable parameter
     directly (or have two construction paths). Alternatively, move the `DisplayUtils` lazy ref from
     `AbstractJvmScope` into `AbstractClaudeHook` and `AbstractClaudeTool`, and provide a separate
     construction path in `AbstractClaudeStatusline` that does not require pluginRoot.
     **Preferred approach:** Keep `DisplayUtils` in `AbstractJvmScope` but change its constructor to not
     require `pluginRoot`. If DisplayUtils uses pluginRoot only to locate an emoji-width config file, make
     that path optional (null means use defaults).

For each file, update the constructor parameter type and field type accordingly. Update Javadoc `@param` tags.

#### Step 12: Update `EnforceJvmScopeEnvAccessTest` whitelist

**File:** `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceJvmScopeEnvAccessTest.java`

- Remove `MainJvmScope.java` from the whitelist (file is deleted in Step 9)
- Verify `MainClaudeStatusline.java` is still in the whitelist (it still reads `CLAUDE_PROJECT_DIR` or other
  env vars, if applicable after Step 8 changes)
- If `MainClaudeStatusline` no longer calls `System.getenv()` after Step 8, remove it from the whitelist
- Update the class Javadoc to reflect the current whitelist

#### Step 13: Update test classes

**Test scope classes:**

1. `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeTool.java`:
   - Verify constructor still passes `pluginRoot` to `AbstractClaudeTool`. After Step 6, `AbstractClaudeTool`
     still accepts `(sessionId, projectPath, pluginRoot, claudeConfigPath)` -- no change needed if signature
     is unchanged.

2. `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java` (if it exists):
   - Same verification as TestClaudeTool.

3. `TestClaudeStatusline` (if it exists):
   - Update to match the new `AbstractClaudeStatusline` constructor that no longer takes `pluginRoot` or
     `claudeConfigPath`.

4. `JvmScopePathResolutionTest.java`:
   - Update to not use `MainJvmScope` (deleted). Use `TestClaudeTool` instead.

5. Any test that references `ClaudeScope` or `AbstractClaudeScope` -- remove those references.

#### Step 14: Update `module-info.java`

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/module-info.java`

- No package-level changes expected (all files remain in existing packages). Verify the module compiles.
- If `ClaudeScope` or `AbstractClaudeScope` were the only public types exported from a package, verify that
  removing them does not break the exports. Both are in `io.github.cowwoc.cat.hooks` which has many other
  types, so no change needed.

#### Step 15: Build and test

```bash
mvn -f client/pom.xml verify -e
```

Fix any compilation errors, test failures, or linter violations. All tests must pass.

#### Step 16: E2E verification

1. Build the jlink image:
   ```bash
   mvn -f client/pom.xml package -e
   ```
2. Test the statusline command without `CLAUDE_PLUGIN_ROOT`:
   ```bash
   unset CLAUDE_PLUGIN_ROOT
   echo '{"session_id":"test","workspace":{"project_dir":"/workspace"},"model":{"display_name":"test"}}' | \
     client/target/jlink-image/bin/get-statusline-output
   ```
3. Verify the statusline renders without error.

#### Step 17: Update `index.json` to closed

**File:** `.cat/issues/v2/v2.1/index.json`

- Set the `extract-plugin-root-from-jvmscope` issue status to `"closed"`
