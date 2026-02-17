# Plan: Move Squash Backup Cleanup to merge-and-cleanup.sh

## Goal
Move backup branch cleanup from git-squash-quick.sh to merge-and-cleanup.sh. After a successful merge, delete all
`backup-before-squash-*` branches that are ancestors of the just-merged commit (safe for parallel execution).
Additionally, fix dead code in git-squash-quick.sh where `set -euo pipefail` makes the rebase conflict handling
(lines 57-93) unreachable.

## Satisfies
None (infrastructure fix from M348/M349)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Parallel Claude Code instances may have in-progress squash backups
- **Mitigation:** Only delete backups that are ancestors of the merged commit — another instance's active backup won't
  be an ancestor

## Files to Modify
- `plugin/scripts/git-squash-quick.sh` - Remove backup deletion on success (lines 151-153) and EXIT trap cleanup
  (lines 19-26). Keep backup creation (line 97-98) for safety during squash.
- `plugin/scripts/merge-and-cleanup.sh` - Add new step between branch deletion and lock release: find all
  `backup-before-squash-*` branches, delete those that are ancestors of the merged commit

## Acceptance Criteria
- [ ] git-squash-quick.sh no longer deletes its own backup branch
- [ ] git-squash-quick.sh EXIT trap no longer deletes backup (backup preserved for investigation on any failure)
- [ ] git-squash-quick.sh dead code removed: rebase conflict handling (lines 57-93) replaced with proper `set -e`
  compatible error handling (capture exit code via `if ! REBASE_OUTPUT=$(...)` or `|| true` pattern)
- [ ] merge-and-cleanup.sh deletes only backup-before-squash-* branches that are ancestors of the merged commit
- [ ] Cleanup step is non-fatal (logs warning on failure, continues)
- [ ] After merge with multiple approval gate retries, no orphaned backups remain
- [ ] Parallel instance's in-progress backup is not deleted

## Execution Steps
1. **Fix dead code in git-squash-quick.sh:** Replace the unreachable rebase conflict handling (lines 54-93) with
   `set -e` compatible error capture. Use `if ! REBASE_OUTPUT=$(git ... rebase ... 2>&1); then` pattern to capture
   both output and exit code without triggering `set -e`. Remove unreachable `REBASE_EXIT=$?` and associated dead
   code blocks.
2. **Update git-squash-quick.sh cleanup:** Remove lines 19-26 (cleanup_backup function + trap), remove lines 151-153
   (explicit backup deletion on success), remove `BACKUP=""` after deletion. Keep backup creation at line 97-98.
3. **Update merge-and-cleanup.sh:** Add STEP 5 (renumber existing step 5 to 6) that iterates
   `backup-before-squash-*` branches, checks `git merge-base --is-ancestor $backup HEAD`, deletes if true
4. **Test:** Verify merge succeeds and backups are cleaned up
