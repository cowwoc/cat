---
issue: 2.1-jvmenv-w2-interface
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 2 of 5
---

# Plan: jvmenv-w2-interface

## Objective

Remove `getClaudeConfigDir()`, `getClaudeSessionsPath()`, and `getClaudeSessionPath()` from the
`JvmScope` interface and from `AbstractJvmScope`. Add these methods to both `ClaudeTool` and
`ClaudeHook` interfaces. Move the implementations from `AbstractJvmScope` to `AbstractClaudeTool`
and `AbstractClaudeHook`.

## Background

Waves 1 and 5 (creating `ClaudeHook`, `ClaudeTool`, `AbstractClaudeHook`, `AbstractClaudeTool`,
`MainClaudeTool`, `TestClaudeTool`) were implemented as part of `2.1-jvmenv-w1-claudeenv`. The
remaining claude-specific methods in `JvmScope` are the three path methods listed above.

## Dependencies

- `2.1-jvmenv-w1-claudeenv` is already closed and merged into v2.1

## Scope

### JvmScope interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Remove declarations of `getClaudeConfigDir()`, `getClaudeSessionsPath()`, and
  `getClaudeSessionPath(String sessionId)`.

### AbstractJvmScope

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Remove the concrete `getClaudeConfigDir()`, `getClaudeSessionsPath()`, and
  `getClaudeSessionPath(String sessionId)` implementations. These move to `AbstractClaudeTool`
  and `AbstractClaudeHook`.

### ClaudeTool interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeTool.java`

- Add declarations:
  - `Path getClaudeConfigDir()`
  - `Path getClaudeSessionsPath()`
  - `Path getClaudeSessionPath(String sessionId)`

### AbstractClaudeTool

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`

- Add concrete implementations of `getClaudeConfigDir()`, `getClaudeSessionsPath()`, and
  `getClaudeSessionPath(String sessionId)` (moved from `AbstractJvmScope`).

### ClaudeHook interface

File: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`

- Add declarations:
  - `Path getClaudeConfigDir()`
  - `Path getClaudeSessionsPath()`
  - `Path getClaudeSessionPath(String sessionId)`

### AbstractClaudeHook

File: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`

- `getClaudeConfigDir()` is already implemented here (line 206) — no change needed.
- Add concrete implementations of `getClaudeSessionsPath()` and `getClaudeSessionPath(String sessionId)`
  (moved from `AbstractJvmScope`).

## Post-conditions

- [ ] `JvmScope` declares no method named `getClaudeConfigDir`, `getClaudeSessionsPath`, or
  `getClaudeSessionPath`
- [ ] `AbstractJvmScope` contains no implementation of these three methods
- [ ] `ClaudeTool` and `ClaudeHook` declare all three methods
- [ ] `AbstractClaudeTool` and `AbstractClaudeHook` implement all three methods
- [ ] Code compiles (call sites are updated in Wave 3/4)
