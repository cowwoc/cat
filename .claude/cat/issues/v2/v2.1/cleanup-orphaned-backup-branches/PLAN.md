# Plan: cleanup-orphaned-backup-branches

## Problem
`git-squash-quick.sh` creates `backup-before-squash-*` branches as safety nets during squash operations. The script
cleans them up on success (line 151-153) and has an EXIT trap (line 20-26), but branches accumulate when subagents are
killed before cleanup runs or the trap doesn't fire in certain execution contexts. This was identified as M501 learning.

## Satisfies
None (infrastructure maintenance)

## Risk Assessment
- **Risk Level:** LOW
- **Scope:** 1 file, 4-line addition
- **Concerns:** None - cleanup is best-effort with `|| true`

## Files to Modify
- `plugin/scripts/git-squash-quick.sh` - Add proactive cleanup of orphaned backup branches at script start

## Acceptance Criteria
- [ ] Orphaned `backup-before-squash-*` branches are deleted at the start of each squash operation
- [ ] Cleanup is best-effort (failures ignored with `|| true`)
- [ ] Existing backup/cleanup logic unchanged

## Execution Steps
1. **Add orphaned backup cleanup:** Insert a loop after the argument parsing (line 17) and before the EXIT trap
   setup (line 19) that deletes any existing `backup-before-squash-*` branches:
   ```bash
   for orphan in $(git -C "$WORKTREE_PATH" branch --list 'backup-before-squash-*' --format='%(refname:short)' 2>/dev/null); do
     git -C "$WORKTREE_PATH" branch -D "$orphan" >/dev/null 2>&1 || true
   done
   ```
   - Files: `plugin/scripts/git-squash-quick.sh`

## Commit Type
bugfix
