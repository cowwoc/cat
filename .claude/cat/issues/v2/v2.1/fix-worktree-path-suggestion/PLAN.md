# Plan: fix-worktree-path-suggestion

## Problem
When an agent working in a worktree attempts to edit a path outside both the worktree AND the
main workspace (e.g., `/tmp/migration-test/dot-claude/cat/.gitignore`), the hook's error message
includes a nonsensical "corrected path" suggestion. `WorktreeContext.correctedPath()` is called
unconditionally: it relativizes the target against the project directory (producing `../../tmp/...`)
then resolves that relative path against the worktree, yielding:
`/worktree-path/../tmp/migration-test/dot-clone/cat/.gitignore`

## Satisfies
None

## Root Cause
`EnforceWorktreePathIsolation.checkAgainstContext()` calls `context.correctedPath(absoluteFilePath)`
without first checking whether `absoluteFilePath` is under `context.absoluteProjectDirectory()`. When
the target is outside the project directory, `relativize()` produces a `../../...` path that, when
resolved against the worktree, points outside it — giving a nonsensical suggestion.

Note: `absoluteFilePath` is already normalized via `.toAbsolutePath().normalize()` before the check,
so paths with `..` components are canonicalized before comparison. No additional `realpath` call is
needed.

## Expected vs Actual
- **Expected:** When target is outside the workspace (e.g., `/tmp/...`), the error shows only the
  blocking reason without a "corrected path" suggestion.
- **Expected:** When target is inside the workspace (e.g., `/workspace/plugin/file.py`), the error
  still shows the corrected worktree path suggestion.
- **Actual:** Corrected path suggestion always appears, even with `/tmp/...` targets, producing
  nonsensical paths containing `../`.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Agents may rely on the corrected path suggestion to fix their writes. Tests
  verify the suggestion still appears for workspace paths.
- **Mitigation:** Test both cases (inside-workspace and outside-workspace targets)

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` — add
  `isUnderProjectDirectory` guard before calling `correctedPath()` in `checkAgainstContext()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java` — add
  three new test cases

## Test Cases
- [ ] `fileOutsideWorkspaceOmitsCorrectedPathSuggestion` — target `/tmp/...`, verify blocked AND
  error does NOT contain "Use the corrected worktree path"
- [ ] `fileInsideWorkspaceShowsCorrectedPathSuggestion` — target project dir path, verify blocked AND
  error CONTAINS "Use the corrected worktree path" with a valid worktree-relative path
- [ ] `pathWithDotsIsNormalizedBeforeCheck` — target `projectDir + "/../tmp/file"`, verify normalized
  path is treated as outside workspace (no corrected suggestion)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Write failing tests for all three new test cases
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- Run tests to confirm they fail: `mvn -f client/pom.xml test -Dtest=EnforceWorktreePathIsolationTest`

### Wave 2
- Fix `checkAgainstContext()`: add `absoluteFilePath.startsWith(context.absoluteProjectDirectory())`
  guard; show corrected path only when true; omit the suggestion line when false
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- Run full test suite: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] Bug fixed: error message for targets outside the workspace (e.g., `/tmp/...`) does NOT contain
  the "Use the corrected worktree path instead" line
- [ ] Regression test added: `fileOutsideWorkspaceOmitsCorrectedPathSuggestion` passes
- [ ] Error message format verified: when target IS under the main workspace, the error still
  includes the corrected path suggestion with a valid worktree-relative path
  (`fileInsideWorkspaceShowsCorrectedPathSuggestion` passes)
- [ ] Canonical path resolution verified: `pathWithDotsIsNormalizedBeforeCheck` passes
- [ ] All existing tests pass (no regressions)
- [ ] E2E: Reproduce original bug scenario: attempt Write to `/tmp/...` while in a worktree and
  confirm the error message no longer contains `../` in a corrected-path suggestion
