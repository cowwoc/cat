# Plan: inject-env-via-jvmscope

## Current State
Three session start handlers (`CheckRetrospectiveDue`, `CheckUpgrade`, `CheckUpdateAvailable`) call
`System.getenv("CLAUDE_PROJECT_DIR")` and `System.getenv("CLAUDE_PLUGIN_ROOT")` directly. Their tests fail in the
Maven build environment because these env vars are not set. `DefaultJvmScope` only provides `DisplayUtils`.

## Target State
`JvmScope` provides `getClaudeProjectDir()` and `getClaudePluginRoot()`. `MainJvmScope` (renamed from
`DefaultJvmScope`) reads env vars lazily via `ConcurrentLazyReference`. `TestJvmScope` accepts paths as constructor
parameters. All 559 tests pass.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** `DefaultJvmScope` renamed to `MainJvmScope` (all references updated)
- **Mitigation:** Mechanical rename, compilation verifies completeness

## Files to Modify
- `hooks/src/main/java/.../JvmScope.java` - Add `getClaudeProjectDir()` and `getClaudePluginRoot()`
- `hooks/src/main/java/.../DefaultJvmScope.java` - Rename to `MainJvmScope`, add lazy env var reading
- `hooks/src/main/java/.../session/CheckRetrospectiveDue.java` - Accept `JvmScope` in constructor
- `hooks/src/main/java/.../session/CheckUpgrade.java` - Accept `JvmScope` in constructor
- `hooks/src/main/java/.../session/CheckUpdateAvailable.java` - Accept `JvmScope` in constructor
- `hooks/src/main/java/.../GetSessionStartOutput.java` - Accept `JvmScope`, pass to handlers
- `hooks/src/test/java/.../test/GetSessionStartOutputTest.java` - Use `TestJvmScope` with temp dirs
- 16 files referencing `DefaultJvmScope` - Bulk rename to `MainJvmScope`

## Files to Create
- `hooks/src/main/java/.../MainJvmScope.java` - Production impl with lazy env var reading
- `hooks/src/test/java/.../test/TestJvmScope.java` - Test impl with injectable paths

## Execution Steps
1. Add `getClaudeProjectDir()` and `getClaudePluginRoot()` to `JvmScope` interface
2. Rename `DefaultJvmScope` to `MainJvmScope`, add `ConcurrentLazyReference` fields for env vars
3. Create `TestJvmScope` in test module with constructor-injected paths
4. Update 3 handlers to accept `JvmScope` and use scope methods instead of `System.getenv()`
5. Update `GetSessionStartOutput` to accept and pass `JvmScope`
6. Bulk rename all `DefaultJvmScope` references to `MainJvmScope`
7. Update 3 failing tests to create temp directories and use `TestJvmScope`

## Success Criteria
- [x] `JvmScope` declares `getClaudeProjectDir()` and `getClaudePluginRoot()`
- [x] `MainJvmScope` reads env vars lazily; `TestJvmScope` accepts constructor params
- [x] 3 handlers use `JvmScope` instead of `System.getenv()`
- [x] All 559 tests pass (`mvn -f hooks/pom.xml test`)
- [x] No `DefaultJvmScope` references remain
