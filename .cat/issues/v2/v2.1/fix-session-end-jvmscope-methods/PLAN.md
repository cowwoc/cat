# Plan: fix-session-end-jvmscope-methods

## Problem

Two compilation errors were introduced by the `feature: implement SessionEndHandler for stale session work file
cleanup` commit. `SessionEndHook.java:79` calls `scope.getClaudeProjectDir()` and `SessionEndHandler.java:80`
calls `scope.getEncodedProjectDir()` — neither method exists on the `JvmScope` interface.

## Parent Requirements

None

## Reproduction Code

```
mvn -f client/pom.xml compile
# Output:
# [ERROR] SessionEndHook.java:[79,50] cannot find symbol
#   symbol:   method getClaudeProjectDir()
# [ERROR] SessionEndHandler.java:[80,37] cannot find symbol
#   symbol:   method getEncodedProjectDir()
```

## Expected vs Actual

- **Expected:** `mvn -f client/pom.xml compile` succeeds with exit code 0
- **Actual:** Compilation fails with two "cannot find symbol" errors referencing non-existent JvmScope methods

## Root Cause

The `SessionEndHandler` implementation was written against method names that were never added to the `JvmScope`
interface. `JvmScope` provides `getProjectPath()` (not `getClaudeProjectDir()`) and has no `getEncodedProjectDir()`
method. The correct way to derive the `{claudeConfigDir}/projects/{encodedProjectDir}/` path is via
`scope.getClaudeSessionPath().getParent()` — `getClaudeSessionPath()` returns
`{claudeConfigDir}/projects/{encodedProjectRoot}/{sessionId}/`, so its parent is the per-project directory.

## Approach Analysis

### A: Add missing methods to JvmScope (REJECTED)
- **Risk:** MEDIUM
- **Scope:** 3+ files (JvmScope interface, AbstractJvmScope, MainJvmScope, TestJvmScope)
- **Description:** Add `getClaudeProjectDir()` as an alias for `getProjectPath()` and add `getEncodedProjectDir()`
  to the interface. Rejected because it adds API surface for methods that already have equivalents, creating
  confusion about which to use.

### B: Replace call sites with existing JvmScope methods (CHOSEN)
- **Risk:** LOW
- **Scope:** 2 files
- **Description:** Use `scope.getProjectPath()` for `getClaudeProjectDir()` and
  `scope.getClaudeSessionPath().getParent()` for the encoded project path construction. The existing
  `getClaudeSessionPath()` already returns `{claudeConfigDir}/projects/{encodedProjectRoot}/{sessionId}/`, so
  its parent gives the exact same directory that was being manually constructed.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — these files were just added and broke the build; no production code currently calls them
- **Mitigation:** Run `mvn -f client/pom.xml compile` to verify fix

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java` — line 79: replace `scope.getClaudeProjectDir()` with `scope.getProjectPath()`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java` — lines 80-81: replace two-line `encodedProjectDir` + manual path construction with `scope.getClaudeSessionPath().getParent()`

## Test Cases
- [ ] `mvn -f client/pom.xml compile` succeeds
- [ ] Existing tests pass: `mvn -f client/pom.xml test`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java`, change line 79 from:
  `return runWithProjectDir(input, output, scope.getClaudeProjectDir());`
  to:
  `return runWithProjectDir(input, output, scope.getProjectPath());`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`, replace lines 80-81:
  ```java
  String encodedProjectDir = scope.getEncodedProjectDir();
  Path claudeProjectsDir = scope.getClaudeConfigDir().resolve("projects").resolve(encodedProjectDir);
  ```
  with:
  ```java
  Path claudeProjectsDir = scope.getClaudeSessionPath().getParent();
  ```
- Run `mvn -f client/pom.xml test` to verify compilation and tests pass
- Update `.cat/issues/v2/v2.1/fix-session-end-jvmscope-methods/STATE.md`: set Status to `closed`, Progress to `100%`

## Post-conditions
- [ ] `mvn -f client/pom.xml compile` exits 0 with no errors
- [ ] `mvn -f client/pom.xml test` exits 0 with all tests passing
- [ ] E2E: Run `mvn -f client/pom.xml verify` and confirm the build succeeds
