---
issue: 2.1-jvmenv-w2-interface
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 2 of 4
---

# Plan: jvmenv-w2-interface

## Objective

Remove `getClaudeSessionId()`, `getProjectPath()`, `getClaudePluginRoot()`, and `getClaudeEnvFile()`
from the `JvmScope` interface and their implementations in `MainJvmScope` and `TestJvmScope`.

## Dependencies

- `2.1-jvmenv-w1-claudeenv` must be merged first (`getClaudeEnv()` accessor must exist)

## Scope

### JvmScope interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Remove declarations of `getClaudeSessionId()`, `getProjectPath()`, `getClaudePluginRoot()`,
  `getClaudeEnvFile()`.

### MainJvmScope

File: `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`

- Remove `ConcurrentLazyReference` fields `claudeProjectPath`, `claudePluginRoot`, `claudeSessionId`,
  `claudeEnvFile` and their `@Override` methods.
- Change the constructor to call `super(new ClaudeEnv())`.

### TestJvmScope

File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`

- Remove `@Override getProjectPath()`, `@Override getClaudePluginRoot()`,
  `@Override getClaudeSessionId()`, `@Override getClaudeEnvFile()` method bodies and their backing
  fields (`claudeProjectPath`, `claudePluginRoot`, `claudeSessionId`, `claudeEnvFile`).
- In each constructor, build a `Map<String, String>` from the constructor parameters and pass
  `SharedSecrets.newClaudeEnv(map)` to `super()`. Map keys:
  - `CLAUDE_PROJECT_DIR` → `claudeProjectPath.toString()`
  - `CLAUDE_PLUGIN_ROOT` → `claudePluginRoot.toString()`
  - `CLAUDE_SESSION_ID` → `claudeSessionId` (default: `"test-session"`)
  - `CLAUDE_ENV_FILE` → `claudeEnvFile.toString()`
- Keep all existing constructor signatures unchanged.

## Post-conditions

- [ ] No method named `getClaudeSessionId`, `getProjectPath`, `getClaudePluginRoot`, or
  `getClaudeEnvFile` exists in `JvmScope`, `MainJvmScope`, or `TestJvmScope`
- [ ] `MainJvmScope` and `TestJvmScope` pass a `ClaudeEnv` to `AbstractJvmScope` via `super()`
- [ ] Code compiles (call sites in Wave 3/4 are updated next)
