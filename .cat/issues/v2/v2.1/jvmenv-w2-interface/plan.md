---
issue: 2.1-jvmenv-w2-interface
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 2 of 5
commit-type: refactor
---

# Plan: jvmenv-w2-interface

## Objective

Remove `getClaudeConfigDir()`, `getClaudeSessionsPath()`, and `getClaudeSessionPath()` from the
`JvmScope` interface and from `AbstractJvmScope`. Add these methods to both `ClaudeTool` and
`ClaudeHook` interfaces. Move the implementations from `AbstractJvmScope` to `AbstractClaudeTool`
and `AbstractClaudeHook`. Rename `getClaudeConfigDir()` to `getClaudeConfigPath()` in all moved
declarations and implementations, since the method returns a `Path`, not a directory handle.

## Background

Waves 1 and 5 (creating `ClaudeHook`, `ClaudeTool`, `AbstractClaudeHook`, `AbstractClaudeTool`,
`MainClaudeTool`, `TestClaudeTool`) were implemented as part of `2.1-jvmenv-w1-claudeenv`. The
remaining claude-specific methods in `JvmScope` are the three path methods listed above.

## Dependencies

- `2.1-jvmenv-w1-claudeenv` is already closed and merged into v2.1

## Research Findings

### Current Method Locations

| Method | JvmScope | AbstractJvmScope | ClaudeTool | AbstractClaudeTool | ClaudeHook | AbstractClaudeHook |
|--------|----------|------------------|------------|-------------------|------------|-------------------|
| `getClaudeConfigDir()` | Declared | Not impl | Not declared | Not impl | Not declared | Implemented (line 192) |
| `getClaudeSessionsPath()` | Declared | Implemented (line 100) | Not declared | Inherited | Not declared | Inherited |
| `getClaudeSessionPath(String)` | Declared | Implemented (line 107) | Not declared | Inherited | Not declared | Inherited |

### Key Implementation Details

**`getClaudeConfigDir()`** is abstract in the hierarchy — it's NOT in `AbstractJvmScope`. Concrete
implementations exist in:
- `MainClaudeTool` (line 108): uses `ConcurrentLazyReference` to lazily read `CLAUDE_CONFIG_DIR` env var
- `TestClaudeTool` (line 211): returns the `claudeConfigDir` field set in constructor
- `AbstractClaudeHook` (line 192): returns the `claudeConfigDir` field set in constructor

**`getClaudeSessionsPath()`** implementation in `AbstractJvmScope` (lines 100-105):
```java
@Override
public Path getClaudeSessionsPath()
{
  ensureOpen();
  return getClaudeConfigDir().resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()));
}
```

**`getClaudeSessionPath(String)`** implementation in `AbstractJvmScope` (lines 107-112):
```java
@Override
public Path getClaudeSessionPath(String sessionId)
{
  requireThat(sessionId, "sessionId").isNotBlank();
  return getClaudeSessionsPath().resolve(sessionId);
}
```

**`encodeProjectPath(String)`** is a `static` method in `AbstractJvmScope` (lines 95-98). Both
`AbstractClaudeTool` and `AbstractClaudeHook` extend `AbstractJvmScope`, so they can call this
method directly.

## Scope

### JvmScope interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Remove declarations of `getClaudeConfigDir()` (lines 119-126), `getClaudeSessionsPath()` (lines
  148-156), and `getClaudeSessionPath(String sessionId)` (lines 158-168).
- Remove associated Javadoc for each method.

### AbstractJvmScope

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Remove the concrete `getClaudeSessionsPath()` (lines 100-105) and
  `getClaudeSessionPath(String sessionId)` (lines 107-112) implementations.
- Keep `encodeProjectPath(String)` — it remains in `AbstractJvmScope` as a shared utility.

### ClaudeTool interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java`

- Add declarations with the following Javadoc (originally from `JvmScope`), using the renamed
  method name `getClaudeConfigPath()`:

```java
/**
 * Returns the Claude config directory.
 * <p>
 * Reads the {@code CLAUDE_CONFIG_DIR} environment variable; defaults to {@code ~/.claude} if unset.
 *
 * @return the config directory path
 * @throws IllegalStateException if this scope is closed
 */
Path getClaudeConfigPath();

/**
 * Returns the base directory for session JSONL files.
 * <p>
 * Session files are stored at {@code {claudeSessionsPath}/{sessionId}.jsonl}.
 *
 * @return the session base directory path
 * @throws IllegalStateException if this scope is closed
 */
Path getClaudeSessionsPath();

/**
 * Returns the directory for a session's tracking files.
 * <p>
 * Located at {@code {claudeConfigDir}/projects/{encodedProjectRoot}/{sessionId}/}.
 *
 * @param sessionId the session ID
 * @return the session directory path
 * @throws NullPointerException if {@code sessionId} is null
 * @throws IllegalStateException if this scope is closed
 */
Path getClaudeSessionPath(String sessionId);
```

### AbstractClaudeTool

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`

- The `getClaudeSessionsPath()` implementation calls `getClaudeConfigPath()` (renamed from
  `getClaudeConfigDir()`).
- `getClaudeConfigPath()` is abstract here — `MainClaudeTool` and `TestClaudeTool` provide
  concrete implementations.

### MainClaudeTool

File: `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeTool.java`

- Rename `getClaudeConfigDir()` to `getClaudeConfigPath()`.
- Rename private helper method `claudeConfigDir()` to `claudeConfigPath()`.
- Rename field `claudeConfigDir` to `claudeConfigPath`.

### TestClaudeTool

File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeTool.java`

- Rename `getClaudeConfigDir()` to `getClaudeConfigPath()`.
- Rename field `claudeConfigDir` to `claudeConfigPath`.

### ClaudeHook interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`

- Add the same three method declarations with the same Javadoc as `ClaudeTool` above:
  - `Path getClaudeConfigPath()`
  - `Path getClaudeSessionsPath()`
  - `Path getClaudeSessionPath(String sessionId)`

### AbstractClaudeHook

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`

- Rename `getClaudeConfigDir()` implementation to `getClaudeConfigPath()`.
- Rename field `claudeConfigDir` to `claudeConfigPath`.
- The `getClaudeSessionsPath()` implementation calls `getClaudeConfigPath()` (renamed).

### MainClaudeHook

File: `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java`

- Rename private method `readConfigDir()` to `readConfigPath()`.
- Update the call site that passes the result to the constructor.

### Test files

Files:
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeToolTest.java`

- Rename references to `claudeConfigDir` and `getClaudeConfigDir` to `claudeConfigPath` and
  `getClaudeConfigPath` respectively.

## Sub-Agent Waves

### Wave 1

- **Rename in all files touched by this issue:** In the implementation commit, rename every
  occurrence of `getClaudeConfigDir` to `getClaudeConfigPath`, `claudeConfigDir` field to
  `claudeConfigPath`, and `claudeConfigDir()` private helper to `claudeConfigPath()` across
  all files listed in the Scope section above. Do NOT rename in files outside this issue's scope
  (call site updates are handled by sibling issues `jvmenv-w3-main` and `jvmenv-w4-tests`).

## Post-conditions

- [ ] `JvmScope` declares no method named `getClaudeConfigDir`, `getClaudeConfigPath`,
  `getClaudeSessionsPath`, or `getClaudeSessionPath`
- [ ] `AbstractJvmScope` contains no implementation of `getClaudeSessionsPath` or
  `getClaudeSessionPath` (it never implemented `getClaudeConfigDir`)
- [ ] `ClaudeTool` and `ClaudeHook` each declare `getClaudeConfigPath()`,
  `getClaudeSessionsPath()`, and `getClaudeSessionPath(String)` with Javadoc
- [ ] `AbstractClaudeTool` and `AbstractClaudeHook` each implement `getClaudeSessionsPath()` and
  `getClaudeSessionPath(String)`, calling `getClaudeConfigPath()` (not `getClaudeConfigDir()`)
- [ ] Concrete `getClaudeConfigPath()` implementations exist in `MainClaudeTool`,
  `TestClaudeTool`, and `AbstractClaudeHook`
- [ ] No file touched by this issue contains the string `getClaudeConfigDir` or `claudeConfigDir`
- [ ] `encodeProjectPath(String)` remains in `AbstractJvmScope` as a shared static utility
