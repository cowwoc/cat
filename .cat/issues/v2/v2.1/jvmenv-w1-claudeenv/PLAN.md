---
issue: 2.1-jvmenv-w1-claudeenv
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 1 of 4
---

# Plan: jvmenv-w1-claudeenv

## Objective

Rename three `ClaudeEnv` methods that carry redundant `Claude` prefixes, and add a `ClaudeEnv`
field to `AbstractJvmScope` with a `getClaudeEnv()` accessor exposed via `JvmScope`.

## Scope

### ClaudeEnv renames

File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java`

- Rename `getClaudeSessionId()` → `getSessionId()`
- Rename `getClaudePluginRoot()` → `getPluginRoot()`
- Rename `getClaudeEnvFile()` → `getEnvFile()`
- Update Javadoc for each renamed method.

### Add getClaudeEnv() to JvmScope interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

Add declaration:
```java
/**
 * Returns the Claude environment accessor for this scope.
 *
 * @return the ClaudeEnv instance
 * @throws IllegalStateException if this scope is closed
 */
ClaudeEnv getClaudeEnv();
```

### Update AbstractJvmScope

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Add `final ClaudeEnv claudeEnv` field.
- Add protected constructor parameter `AbstractJvmScope(ClaudeEnv claudeEnv)`.
- Implement `getClaudeEnv()` returning the field.
- Update derived methods to use `claudeEnv`:
  - `getCatDir()`: `claudeEnv.getProjectPath().resolve(Config.CAT_DIR_NAME)`
  - `getClaudeSessionsPath()`: `getClaudeConfigDir().resolve("projects").resolve(encodeProjectPath(claudeEnv.getProjectPath().toString()))`
  - `getClaudeSessionPath()`: `getClaudeSessionsPath().resolve(claudeEnv.getSessionId())`
  - `getCatWorkPath()`: `claudeEnv.getProjectPath().resolve(".cat").resolve("work")`
  - `getCatSessionPath()`: `getCatWorkPath().resolve("sessions").resolve(claudeEnv.getSessionId())`
  - `derivePluginPrefix()`: `claudeEnv.getPluginRoot().toAbsolutePath().normalize()`

## Dependencies

- None (first in sequence)

## Post-conditions

- [ ] `ClaudeEnv` exposes `getSessionId()`, `getPluginRoot()`, `getEnvFile()` (old names removed)
- [ ] `JvmScope` declares `getClaudeEnv()`
- [ ] `AbstractJvmScope` stores `ClaudeEnv` in a field and implements `getClaudeEnv()`
- [ ] Derived methods in `AbstractJvmScope` delegate to `claudeEnv` field
- [ ] `mvn -f client/pom.xml test` passes (may fail until Wave 2 updates concrete impls — compile only must succeed)
