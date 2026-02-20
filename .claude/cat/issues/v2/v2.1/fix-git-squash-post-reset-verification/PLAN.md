# Plan: fix-git-squash-post-reset-verification

## Problem
`GitSquash.java` Step 9 uses `git reset --hard newCommit` to move the branch pointer and update the working tree after
`commit-tree`. In worktree contexts, this can silently fail — the reset completes without error but HEAD doesn't point
to the new commit, leaving the worktree out of sync. The existing Step 10 verification (`git diff backupBranch`) may not
catch this because both HEAD and the backup could end up pointing at the same (wrong) commit.

## Root Cause
After `commit-tree` creates the new squashed commit and `reset --hard` runs, there is no explicit verification that HEAD
actually moved to `newCommit`. If the reset silently fails (e.g., worktree HEAD detachment, directory context issues),
the subsequent `git diff backupBranch` check compares the wrong state.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` — add post-reset HEAD verification
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java` — add test for HEAD verification

## Acceptance Criteria
- [ ] After `reset --hard`, verify `HEAD == newCommit` explicitly
- [ ] If HEAD != newCommit, throw IOException with diagnostic info (expected vs actual)
- [ ] Existing tests still pass

## Execution Steps
1. **Add HEAD verification after Step 9 in GitSquash.java**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java`
   - After line 149 (`reset --hard`), add: resolve HEAD, compare to newCommit, throw if mismatch
2. **Add test for HEAD verification**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java`
   - Test that squash result has HEAD matching the returned commit hash
3. **Run tests**
   - `mvn -f client/pom.xml test`
