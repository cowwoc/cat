# Plan

## Goal

Remove CLAUDE_ENV_FILE from ClaudeTool and ClaudeHook scopes — only InjectEnv should read it via System.getenv().

Currently, `MainClaudeTool` eagerly reads `CLAUDE_ENV_FILE` at construction time (line 47), causing all ~45 CLI tools
to require this env var even though none of them actually call `getEnvFile()`. The only production consumer of
`getEnvFile()` is `InjectEnv` (the SessionStart hook handler), which writes environment variables into that file.

This removal simplifies the scope interfaces, eliminates a construction-time requirement that serves no purpose for
CLI tools, and fixes preprocessor failures where `GetOutput` (via `MainClaudeTool`) fails because `CLAUDE_ENV_FILE`
is not set outside of SessionStart context.

## Pre-conditions

(none)

## Post-conditions

- [ ] `ClaudeTool` interface and implementations no longer reference `CLAUDE_ENV_FILE`
- [ ] `ClaudeHook` interface and implementations no longer reference `CLAUDE_ENV_FILE`
- [ ] `InjectEnv` reads `CLAUDE_ENV_FILE` directly via `System.getenv()` (added to whitelist in `EnforceJvmScopeEnvAccessTest`)
- [ ] User-visible behavior unchanged — all CLI tools and hooks function identically
- [ ] Tests passing — `mvn -f client/pom.xml verify -e` exits 0
- [ ] No regressions in existing functionality
- [ ] E2E: Invoke a CLI tool (e.g., `get-status-output`) and confirm it no longer requires `CLAUDE_ENV_FILE` to be set

## Research Findings

### Current Architecture

The `getEnvFile()` method exists in two parallel interface hierarchies:
- `ClaudeTool` (for CLI tools) → `AbstractClaudeTool` (stores `envFile` field) → `MainClaudeTool` (reads env var at
  construction)
- `ClaudeHook` (for hook handlers) → `AbstractClaudeHook` (declares abstract) → `MainClaudeHook` (reads env var
  on-demand)

Neither `JvmScope` (the base interface) defines `getEnvFile()` — it's specific to `ClaudeTool` and `ClaudeHook`.

### Single Real Consumer

Only `InjectEnv.java` (line 68) calls `scope.getEnvFile()`. It uses the path to write environment variable export
statements for subsequent Bash tool invocations. `InjectEnv` receives a `ClaudeTool` scope, so it can simply call
`System.getenv("CLAUDE_ENV_FILE")` directly instead of going through the scope.

### Files to Modify

| File | Change |
|------|--------|
| `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java` | Remove `getEnvFile()` method declaration |
| `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java` | Remove `getEnvFile()` method declaration |
| `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java` | Remove `envFile` field, constructor param, `getEnvFile()` impl |
| `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java` | Remove abstract `getEnvFile()` declaration |
| `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeTool.java` | Remove `CLAUDE_ENV_FILE` from constructor |
| `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java` | Remove `getEnvFile()` implementation |
| `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` | Read `CLAUDE_ENV_FILE` via `System.getenv()` directly |
| `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeTool.java` | Remove envFile constructor params and `withEnvFile()` factory |
| `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java` | Remove `getEnvFile()` implementation |
| `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeToolTest.java` | Remove `getEnvFile()` test assertion |
| `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHookTest.java` | Remove `getEnvFile()` test assertions |
| `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceJvmScopeEnvAccessTest.java` | Add `InjectEnv.java` to whitelist |

## Sub-Agent Waves

### Wave 1

Each step below is a discrete edit. Read each file before editing. Commit type: `refactor:`.

**Step 1: Remove `getEnvFile()` from `ClaudeTool` interface**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java`
- Remove the `getEnvFile()` method declaration and its Javadoc comment block

**Step 2: Remove `getEnvFile()` from `ClaudeHook` interface**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`
- Remove the `getEnvFile()` method declaration and its Javadoc comment block

**Step 3: Remove `envFile` from `AbstractClaudeTool`**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`
- Remove the `private final Path envFile;` field
- Remove the `envFile` parameter from the protected constructor and the `this.envFile = envFile;` assignment
- Remove the entire `getEnvFile()` method implementation
- Remove the `@Override` annotation above `getEnvFile()` if present
- Keep all other fields and methods intact

**Step 4: Remove abstract `getEnvFile()` from `AbstractClaudeHook`**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`
- Remove the `@Override public abstract Path getEnvFile();` declaration

**Step 5: Remove `CLAUDE_ENV_FILE` from `MainClaudeTool` constructor**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeTool.java`
- In the constructor, remove the `Path.of(getEnvVar("CLAUDE_ENV_FILE"))` argument from the `super()` call
- The super call should become:
  `super(getEnvVar("CLAUDE_SESSION_ID"), Path.of(getEnvVar("CLAUDE_PROJECT_DIR")), Path.of(getEnvVar("CLAUDE_PLUGIN_ROOT")))`

**Step 6: Remove `getEnvFile()` from `MainClaudeHook`**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java`
- Remove the entire `getEnvFile()` method (the `@Override` block that reads `System.getenv("CLAUDE_ENV_FILE")`)

**Step 7: Update `InjectEnv` to read `CLAUDE_ENV_FILE` directly**
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
- At line 68 (or wherever `Path envPath = scope.getEnvFile();` appears), replace with:
  ```java
  String envFileValue = System.getenv("CLAUDE_ENV_FILE");
  if (envFileValue == null || envFileValue.isBlank())
      throw new AssertionError("CLAUDE_ENV_FILE is not set");
  Path envPath = Path.of(envFileValue);
  ```
- Update imports if needed (should already have `java.nio.file.Path`)

**Step 8: Update `TestClaudeTool`**
- File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeTool.java`
- Remove the `envFile` parameter from ALL constructors (there are multiple — check each one)
- Remove the `withEnvFile()` factory method (around line 194-198)
- Update `super()` calls to no longer pass `envFile`
- Remove any constructor that defaults to `claudeProjectPath.resolve(".env")` — remove that default

**Step 9: Update `TestClaudeHook`**
- File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java`
- Remove the `getEnvFile()` implementation method

**Step 10: Update `TestClaudeToolTest`**
- File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeToolTest.java`
- Remove any test assertion that calls `getEnvFile()` (around line 276)

**Step 11: Update `TestClaudeHookTest`**
- File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHookTest.java`
- Remove test assertions that call `getEnvFile()` (around lines 42 and 184)
- If entire test methods only test `getEnvFile()`, remove the whole test method

**Step 12: Update `EnforceJvmScopeEnvAccessTest` whitelist**
- File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceJvmScopeEnvAccessTest.java`
- Add `io/github/cowwoc/cat/hooks/session/InjectEnv.java` to the whitelist set (around lines 64-73)
- This authorizes `InjectEnv` to call `System.getenv()` directly
- If `MainClaudeHook.java` no longer calls `System.getenv("CLAUDE_ENV_FILE")`, verify whether it still
  needs to be in the whitelist (it may call `System.getenv()` for other variables — check before removing)

**Step 13: Remove unused imports**
- After all changes, check each modified file for unused imports (especially `java.nio.file.Path` in files
  that no longer reference `Path`)

**Step 14: Build and test**
- Run `mvn -f client/pom.xml verify -e` and ensure all tests pass
- Fix any compilation errors from the changes above

**Step 15: Update `index.json`**
- Update `.cat/issues/v2/v2.1/remove-env-file-from-scopes/index.json` to set status to `closed`

**Step 16: Commit**
- Stage all changed files
- Commit with message: `refactor: remove CLAUDE_ENV_FILE from ClaudeTool and ClaudeHook scopes`
