# Plan: remove-jvmscope-claudeenv-duplicates

## Current State

`AbstractClaudeTool` stores `private Path projectPath` and `private Path pluginRoot` as private fields
and implements `getProjectPath()` / `getPluginRoot()` locally.

`AbstractClaudeHook` stores `private Path projectPath`, `private Path pluginRoot`, and
`private Path claudeConfigPath` as private fields and implements `getProjectPath()` / `getPluginRoot()` /
`getClaudeConfigPath()` locally.

`MainJvmScope` also stores `private Path projectPath`, `private Path pluginRoot`, and
`private Path claudeConfigPath` as private fields and implements all three getters locally.

All three subclasses of `AbstractJvmScope` independently hold and serve the same three path values, which
violates the DRY principle. The common storage belongs in shared base classes.

## Target State

- `AbstractJvmScope` stores only JVM-level paths (`projectPath`, `pluginRoot`) and provides
  `getProjectPath()` and `getPluginRoot()`. It does NOT store Claude-specific values.
- `AbstractClaudeScope` (new class) extends `AbstractJvmScope`, adds `claudeConfigPath`, and provides
  `getClaudeConfigPath()`, `getClaudeSessionsPath()`, and `getClaudeSessionPath()`.
- `AbstractClaudeTool` extends `AbstractClaudeScope` (not `AbstractJvmScope`).
- `AbstractClaudeHook` extends `AbstractClaudeScope` (not `AbstractJvmScope`).
- `MainJvmScope` extends `AbstractClaudeScope` (not `AbstractJvmScope`).
- `mvn -f client/pom.xml verify` exits 0 with no compilation errors, test failures, or linter violations.

## Parent Requirements

None — this is a tech-debt refactor.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None for external callers; internal hierarchy change only.
- **Mitigation:** The build (`mvn -f client/pom.xml verify`) provides exhaustive compile-time coverage.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`
  — Remove `claudeConfigPath` field, constructor parameter, and getter.
  — Remove `getClaudeSessionsPath()` and `getClaudeSessionPath()` implementations.
  — Constructor takes only `(Path projectPath, Path pluginRoot)`.

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeScope.java` (NEW)
  — Extends `AbstractJvmScope`.
  — Constructor takes `(Path projectPath, Path pluginRoot, Path claudeConfigPath)`.
  — Stores `claudeConfigPath` and provides `getClaudeConfigPath()`.
  — Implements `getClaudeSessionsPath()` and `getClaudeSessionPath()`.

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`
  — Change `extends AbstractJvmScope` to `extends AbstractClaudeScope`.

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`
  — Change `extends AbstractJvmScope` to `extends AbstractClaudeScope`.

- `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`
  — Change `extends AbstractJvmScope` to `extends AbstractClaudeScope`.

## Implementation Steps

### Step 1: Update AbstractJvmScope

Remove `claudeConfigPath` field, its constructor parameter, and `getClaudeConfigPath()`. Remove
`getClaudeSessionsPath()` and `getClaudeSessionPath()` implementations. Constructor now takes only
`(Path projectPath, Path pluginRoot)`.

### Step 2: Create AbstractClaudeScope

New class extending `AbstractJvmScope`. Constructor takes `(Path projectPath, Path pluginRoot,
Path claudeConfigPath)`, calls `super(projectPath, pluginRoot)`, validates and stores
`claudeConfigPath`. Provides `getClaudeConfigPath()`, `getClaudeSessionsPath()`, and
`getClaudeSessionPath()`.

### Step 3: Update AbstractClaudeTool

Change `extends AbstractJvmScope` to `extends AbstractClaudeScope`. Constructor already calls
`super(projectPath, pluginRoot, claudeConfigPath)` which now routes to `AbstractClaudeScope`.

### Step 4: Update AbstractClaudeHook

Change `extends AbstractJvmScope` to `extends AbstractClaudeScope`. Constructor already calls
`super(projectPath, pluginRoot, claudeConfigPath)` which now routes to `AbstractClaudeScope`.

### Step 5: Update MainJvmScope

Change `extends AbstractJvmScope` to `extends AbstractClaudeScope`. Constructor already calls
`super(...)` with three path arguments which now routes to `AbstractClaudeScope`.

### Step 6: Run full verify

```bash
mvn -f client/pom.xml verify -e
```

Fix any compilation errors, test failures, or linter violations before committing.

## Post-conditions

- [ ] `AbstractJvmScope` stores only `projectPath` and `pluginRoot`. No Claude-specific values.
- [ ] `AbstractClaudeScope` exists, extends `AbstractJvmScope`, stores `claudeConfigPath`.
- [ ] `AbstractClaudeTool` extends `AbstractClaudeScope`.
- [ ] `AbstractClaudeHook` extends `AbstractClaudeScope`.
- [ ] `MainJvmScope` extends `AbstractClaudeScope`.
- [ ] `mvn -f client/pom.xml verify` exits 0 with no errors or failures.
