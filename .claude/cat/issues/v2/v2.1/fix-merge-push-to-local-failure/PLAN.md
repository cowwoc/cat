# Plan: fix-merge-push-to-local-failure

## Problem

`MergeAndCleanup.java` uses `git push . HEAD:<baseBranch>` to fast-forward the base branch pointer from within a linked
worktree. This fails when the base branch is checked out in the main worktree because git's default
`receive.denyCurrentBranch = refuse` blocks pushes to checked-out branches.

The tests only passed because they explicitly set `receive.denyCurrentBranch = ignore`.

Two `push .` calls were affected:
1. `syncBaseBranchWithOrigin()`: `push . origin/<base>:<base>`
2. `fastForwardMerge()`: `push . HEAD:<base>`

## Satisfies

None (infrastructure bugfix, supersedes fix-merge-working-tree-sync)

## Root Cause

`git push .` triggers `receive.denyCurrentBranch` checks. The default value `refuse` rejects pushes to branches that
are currently checked out in any worktree. Tests masked this by setting the config to `ignore`.

## Fix

Replace `git push .` with `git update-ref` using compare-and-swap (CAS). Remove `syncMainWorkingTree()` entirely — the
main worktree's files don't need to be in sync because git commands resolve branches through refs, not disk files.

## Files Modified

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java`

## Pre-conditions

- [x] Tests use `receive.denyCurrentBranch = ignore` to work around the bug

## Post-conditions

- [x] No `receive.denyCurrentBranch` config in tests
- [x] Both `push .` calls replaced with `update-ref` using CAS
- [x] `syncMainWorkingTree()` removed
- [x] All tests pass without workarounds
