# Plan: fix-stale-lock-protection

## Problem

`BlockUnsafeRemoval` hook blocks `git worktree remove` based on lock files from dead sessions, preventing cleanup of stale artifacts. Example: Attempting to remove a worktree fails with "A protected path is inside the deletion target" because the `.lock` file still exists from a session that crashed hours ago.

**Current behavior:**
1. Session A creates worktree, creates lock file
2. Session A crashes (without cleanup)
3. Worktree is manually deleted by admin
4. Session B tries to work: hook sees stale lock, blocks all worktree operations
5. Workaround: Use `cd /tmp && git worktree remove ...` (bypasses main worktree detection)

**Root cause:** Hook adds all locked worktrees as "protected" without checking if the owning session is alive.

## Satisfies

None (infrastructure improvement)

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
  - Modify `getProtectedPaths()` to skip stale locks (> 4 hours old or session ID validation)
  - Consider adding configurable TTL for lock staleness

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`
  - Add tests for stale lock handling

## Acceptance Criteria

- [ ] Stale locks (> 4 hours old) are not treated as protection
- [ ] Current session's locks are still protected
- [ ] Other active sessions' locks are still protected
- [ ] `git worktree remove` works without `cd /tmp` workaround
- [ ] Tests pass

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** May need to determine "staleness" threshold and session liveness
- **Mitigation:** Conservative TTL (4+ hours); verify session is truly inactive before skipping protection
