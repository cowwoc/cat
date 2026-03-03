# Plan: fix-worktree-path-suggestion

## Problem
When an agent working in a worktree attempts to edit a path outside both the worktree AND the
main workspace (e.g., `/tmp/migration-test/dot-claude/cat/.gitignore`), the hook incorrectly
**blocks** the write and shows a nonsensical "corrected path" suggestion. The hook's purpose is to
redirect writes from the main workspace into the worktree — not to block writes to unrelated paths
like `/tmp/`.

## Satisfies
None

## Root Cause
`EnforceWorktreePathIsolation.checkAgainstContext()` blocks *everything* not inside the worktree.
It should only block writes targeting the **main workspace** (project directory), because those are
the writes that need redirection into the worktree. Writes to unrelated paths (`/tmp/`, `/home/`,
etc.) are legitimate operations (test fixtures, temp files) and should be allowed.

Additionally, `correctedPath()` is called unconditionally: it relativizes the target against the
project directory (producing `../../tmp/...`) then resolves that against the worktree, yielding a
nonsensical path.

Note: `absoluteFilePath` is already normalized via `.toAbsolutePath().normalize()` before the check,
so paths with `..` components are canonicalized before comparison.

## Expected vs Actual
- **Expected:** Path inside worktree → allow. Path inside main workspace → block with corrected
  path. Path outside both → allow.
- **Actual:** Path inside worktree → allow. Everything else → block with nonsensical corrected path.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Agents may attempt to write to unrelated paths that were previously blocked.
  This is the desired behavior — those writes were incorrectly blocked before.
- **Mitigation:** Test all three cases (worktree, workspace, outside-both)

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` — in
  `checkAgainstContext()`, allow paths that are outside both the worktree and the project directory;
  only block paths under the project directory (with corrected path suggestion)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java` — add
  new test cases; update existing test that checks outside-workspace paths

## Test Cases
- [ ] `fileOutsideWorkspaceIsAllowed` — target `/tmp/...` while worktree active, verify **allowed**
  (not blocked)
- [ ] `fileInsideWorkspaceShowsCorrectedPathSuggestion` — target project dir path, verify blocked AND
  error CONTAINS "Use the corrected worktree path" with a valid worktree-relative path
- [ ] `pathWithDotsNormalizedOutsideWorkspaceIsAllowed` — target `projectDir + "/../tmp/file"`,
  verify normalized path is treated as outside workspace and **allowed**

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Write failing tests for the new test cases
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- Run tests to confirm they fail: `mvn -f client/pom.xml test -Dtest=EnforceWorktreePathIsolationTest`

### Wave 2
- Fix `checkAgainstContext()`: after the worktree-containment check, add a second check —
  `absoluteFilePath.startsWith(context.absoluteProjectDirectory())`. If false, return `allow()`
  (path is outside both worktree and workspace). If true, block with the corrected path suggestion.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- Run full test suite: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] Bug fixed: writes to paths outside the workspace (e.g., `/tmp/...`) are **allowed** when a
  worktree is active
- [ ] Regression test added: `fileOutsideWorkspaceIsAllowed` passes
- [ ] Error message format verified: when target IS under the main workspace, the error still
  includes the corrected path suggestion with a valid worktree-relative path
  (`fileInsideWorkspaceShowsCorrectedPathSuggestion` passes)
- [ ] Canonical path resolution verified: `pathWithDotsNormalizedOutsideWorkspaceIsAllowed` passes
- [ ] All existing tests pass (no regressions)
- [ ] E2E: Reproduce original bug scenario: attempt Write to `/tmp/...` while in a worktree and
  confirm the write is allowed
