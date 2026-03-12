# Plan: Clean orphaned files after rebase onto rewritten upstream

## Goal
Update `GitRebaseSafe` to capture `git status --porcelain` before and after the rebase, then delete any untracked
files that appeared as a result of the rebase (i.e., files present in the old upstream tip but removed by a subsequent
rewrite of that upstream branch).

## Satisfies
None - correctness improvement to rebase tooling

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Deleting files that were pre-existing untracked work rather than rebase orphans
- **Mitigation:** Only delete files whose `??` status was NOT present before the rebase; pre-existing untracked files
  are preserved

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java` - capture pre/post status, delete orphans
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseSafeTest.java` - add tests for orphan cleanup

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In `GitRebaseSafe.java`, before running the rebase, capture the set of untracked file paths from
  `git status --porcelain` (lines starting with `??`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
- After the rebase completes successfully, capture the set of untracked file paths again
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
- Compute the difference: files that are `??` after the rebase but were NOT `??` before
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
- Delete the newly-appeared untracked files (rebase orphans) from disk
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
- Add the list of deleted orphan paths to the JSON output under a `deleted_orphans` field (empty array if none)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
- Add tests covering: no orphans (empty deleted_orphans), orphans present and deleted, pre-existing untracked
  files preserved
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseSafeTest.java`

## Post-conditions
- [ ] Pre-existing untracked files are not deleted after rebase
- [ ] Files that become untracked due to the rebase (orphans from rewritten upstream) are deleted
- [ ] JSON output includes `deleted_orphans` field listing paths of any deleted files
- [ ] All existing tests pass
- [ ] E2E: Rebase onto a rewritten upstream that dropped a planning commit leaves no orphan directories in the
  worktree after `git-rebase-safe` completes
