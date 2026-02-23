# Plan: replace-update-ref-with-merge

## Current State

`MergeAndCleanup.java` uses `git update-ref` in two places (`syncBaseBranchWithOrigin` and `fastForwardMerge`) to advance
the base branch pointer. This only moves the ref without updating the working tree of the main worktree where the base
branch is checked out, leaving files stale after merges.

## Target State

Both methods use `git merge --ff-only` executed in the main worktree (`projectDir`), which atomically updates the ref,
index, and working tree. A shared `mergeWithRetry` method handles index.lock contention from concurrent agents.

## Satisfies

None (infrastructure refactor)

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None ��� merge produces identical git history (same commits, same refs)
- **Mitigation:** Existing tests verify merge behavior; update test that expected stale working tree

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` ��� replace update-ref with merge --ff-only,
  add mergeWithRetry method
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java` ��� update test to expect working tree
  sync

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Add `mergeWithRetry` private method to MergeAndCleanup**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Runs `git -C <projectDir> merge --ff-only <ref>`
   - index.lock contention: retry up to 3 times with 1-second delay
   - Uncommitted changes would be overwritten: fail-fast with clear error
   - Not fast-forwardable / other errors: throw with caller-provided message
   - Add a Logger field to the class for retry debug logging

2. **Rewrite `syncBaseBranchWithOrigin`**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Change signature from `(String worktreePath, String baseBranch)` to `(String projectDir, String baseBranch)`
   - Keep `git fetch origin <base>` (run from `projectDir`)
   - Replace `update-ref` with `mergeWithRetry(projectDir, "origin/" + baseBranch, ...)`
   - Update call site in `execute()`: pass `projectDir` instead of `worktreePath`

3. **Rewrite `fastForwardMerge`**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Change signature from `(String worktreePath, String baseBranch, String baseSha)` to
     `(String projectDir, String issueBranch)`
   - Replace `update-ref` with `mergeWithRetry(projectDir, issueBranch, ...)`
   - Update call site in `execute()`: pass `projectDir, taskBranch`
   - Remove `baseSha` lookup (no longer needed)

4. **Update test `executeUpdatesRefWithoutSyncingWorkingTree`**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java`
   - Rename to `executeSyncsMainWorkingTree`
   - Change assertion: the merged file SHOULD now exist in the main working tree
   - Update Javadoc

5. **Update Javadoc**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Class-level: remove "no checkout required" wording
   - `fastForwardMerge`: document `git merge --ff-only` and retry mechanism
   - `syncBaseBranchWithOrigin`: document new approach

6. **Run tests**
   - `mvn -f client/pom.xml test`

## Post-conditions

- [ ] User-visible behavior unchanged (merges produce same git history)
- [ ] All tests passing
- [ ] Code quality improved (no stale working tree, simpler mechanism)
- [ ] Retry logic handles index.lock contention with up to 3 attempts and 1-second delay
- [ ] Fail-fast when uncommitted changes would be overwritten by merge
- [ ] Test updated to expect working tree sync instead of stale state
- [ ] E2E: After merge, `git -C <projectDir> status --porcelain` returns empty (working tree matches HEAD)