# Plan: fix-merge-working-tree-sync

## Problem

`MergeAndCleanup.fastForwardMerge()` uses `git push . HEAD:baseBranch` which advances the branch pointer in the main
repo without updating its working tree. After the worktree is removed, files in the main workspace are stale (don't
match the new HEAD). This caused STATE.md to show "open" after a merge that committed "closed", leading to a bad commit
that reverted bugfix changes.

## Satisfies

None (infrastructure bugfix, recurrence of M369/M386)

## Reproduction Code

```java
// In MergeAndCleanup.execute():
fastForwardMerge(worktreePath, baseBranch);
// After this call, main workspace working tree files don't match HEAD
// git status in /workspace shows modified files (stale working tree)
```

## Expected vs Actual

- **Expected:** After merge, main workspace working tree matches HEAD (git status --porcelain returns empty)
- **Actual:** Working tree retains pre-merge file contents; reading files from disk returns stale data

## Root Cause

`git push . HEAD:baseBranch` is a pointer-only operation — it moves the branch ref but does NOT update the working tree.
This is equivalent to `git update-ref` or `git branch -f`. The working tree must be explicitly synced afterward.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** `git reset --hard HEAD` in the wrong directory could discard work
- **Mitigation:** Only run in projectDir (main workspace), verify with git status after

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` - Add syncMainWorkingTree() call after
  fastForwardMerge()
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java` - Add test verifying working tree is
  clean after execute()

## Test Cases

- [ ] After execute(), main workspace working tree matches HEAD (git status --porcelain returns empty)
- [ ] syncMainWorkingTree() runs successfully in projectDir
- [ ] Existing MergeAndCleanup tests still pass

## Execution Steps

1. **Add syncMainWorkingTree method to MergeAndCleanup**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Add a private method `syncMainWorkingTree(Path projectDir)` that runs `git -C projectDir reset --hard HEAD`
   - Call it in `execute()` immediately after `fastForwardMerge(worktreePath, baseBranch)`
2. **Add test for working tree sync**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java`
   - Add test that verifies `git status --porcelain` returns empty after execute()
3. **Run tests**
   - `mvn -f client/pom.xml test`

## Success Criteria

- [ ] All existing tests pass
- [ ] New test verifies working tree is clean after merge
- [ ] No regressions in related functionality
