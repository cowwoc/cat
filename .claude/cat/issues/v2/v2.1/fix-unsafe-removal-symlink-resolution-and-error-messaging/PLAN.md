# Plan: fix-unsafe-removal-symlink-resolution-and-error-messaging

## Goal
Fix two bugs in BlockUnsafeRemoval: (1) symlink resolution mismatch that allows symlink-based path traversal to bypass
safety checks, and (2) missing current working directory in error messages that makes blocked deletions hard to
diagnose.

## Satisfies
None - bugfix for existing hook infrastructure

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Symlink protection bypass could allow accidental deletion of protected directories via symlink paths;
  unclear error messages frustrate users who don't understand why their deletion is blocked
- **Mitigation:** Add comprehensive tests for symlink cases; error message enhancement is additive

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` - Fix path resolution consistency
  and improve error message
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` - Add symlink and CWD error tests

## Bug 1: Symlink Resolution Mismatch

Protected paths are resolved with `toRealPath()` (symlinks resolved), but target paths use `ShellParser.resolvePath()`
which only calls `normalize()` (symlinks NOT resolved). This means deletion via a symlink path bypasses the safety
check because the paths don't match during comparison.

**Fix:** Attempt `toRealPath()` on the target path when it exists on disk; fall back to normalized path when it doesn't.

## Bug 2: Missing CWD in Error Message

When the hook blocks a deletion because the shell's current working directory is inside the target, the error message
shows the protected path and target but not the CWD. Users cannot tell whether the block is due to their CWD, a lock,
or the main worktree protection.

**Fix:** Add `CWD: <path>` to the error message so users can immediately see why the deletion is blocked and know to
change directories.

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. Fix symlink resolution in `checkProtectedPaths()`: attempt `toRealPath()` on target path when possible, fall back
   to normalized path if target doesn't exist
2. Update error message format in `checkProtectedPaths()` to include `CWD: <workingDirectory>`
3. Add test cases for symlink resolution and CWD error message content
4. Run `mvn -f client/pom.xml verify`

## Post-conditions
- [ ] Symlink paths correctly matched against protected paths (no bypass via symlink)
- [ ] Error message includes current working directory
- [ ] All new tests pass
- [ ] All existing tests pass (no regressions)
