# Plan: fix-block-unsafe-removal-cross-session

## Problem
`BlockUnsafeRemoval.getProtectedPaths()` treats a worktree as protected when its lock file was created
by a **different session**. When session A creates a worktree and session B runs cleanup (e.g., after a
crash or via `/cat:cleanup`), `isOwnedBySession()` returns false → the worktree is added to
`protectedPaths`. Then `checkWorktreeRemove()` calls `checkProtectedPaths()` (which checks lock-based
protections), causing a false positive block even when CWD is not inside the target worktree.

### Reproduction
```bash
# Session A creates a worktree and acquires a lock
# Session A crashes (or is a different Claude instance)
# Session B tries to remove the worktree:
git worktree remove /workspace/.claude/cat/worktrees/2.1-some-issue
# → UNSAFE DIRECTORY REMOVAL BLOCKED
# Error shows Protected: /workspace/.claude/cat/worktrees/2.1-some-issue
# Even though CWD is /workspace (outside the target)
```

### Root Cause
`checkWorktreeRemove()` delegates to `checkProtectedPaths()` which uses the full `getProtectedPaths()`
list including lock-based protection. Lock-based protection is appropriate for `rm -rf` (which may
sweep up many directories unexpectedly), but NOT for explicit `git worktree remove <path>` where:
- The user/agent explicitly named the target
- Only CWD safety matters (shell corruption risk)
- Cross-session cleanup is a legitimate and expected workflow

**Key insight:** `git worktree remove <path>` should be blocked only when CWD is inside `<path>`.
Lock ownership is irrelevant for this command.

## Satisfies
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must preserve the CWD protection for `git worktree remove`
- **Mitigation:** Add targeted test for the cross-session scenario; run all existing tests

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` - modify
  `checkWorktreeRemove()` to skip lock-based protection; only check CWD vs target containment
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` - add
  regression test for cross-session worktree removal (TDD: write failing test first)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Write a failing regression test (TDD):** Add test `worktreeRemoveAllowsWhenDifferentSessionLock`
   to `BlockUnsafeRemovalTest.java`. The test should:
   - Create a temp git repo with `.claude/cat/locks/task-123.lock` (owned by `other-session`)
   - Create a worktree directory at `.claude/cat/worktrees/task-123`
   - Call `handler.check("git worktree remove /path/to/worktree", workingDirectory, ...)` with CWD
     set to the main worktree root (outside the target)
   - Assert `result.blocked()` is false
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`

2. **Verify test fails:** Run `mvn -f client/pom.xml test -Dtest=BlockUnsafeRemovalTest` to confirm
   the new test fails, proving the bug exists.

3. **Fix `checkWorktreeRemove()`:** Modify the method to bypass `checkProtectedPaths()` entirely and
   instead directly check only whether CWD is inside or equal to the target path. The logic should be:
   - Resolve the target path
   - Resolve CWD
   - If CWD is inside or equal to target → block with the existing error message
   - Otherwise → allow
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`

4. **Run all tests:** `mvn -f client/pom.xml test` — all tests must pass.

5. **E2E verification:** In the main workspace, create a fresh lock file for a non-existent issue
   using a fake session ID:
   ```bash
   echo '{"session_id": "aaaaaaaa-0000-0000-0000-000000000000", "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > /workspace/.claude/cat/locks/test-e2e-verify.lock
   ```
   Then run `git worktree remove /workspace/.claude/cat/worktrees/nonexistent 2>&1 || true` and
   confirm the error is from git (not from the hook). Then clean up the test lock.

## Post-conditions
- [ ] `worktreeRemoveAllowsWhenDifferentSessionLock` test exists and passes
- [ ] `git worktree remove <path>` is allowed when CWD is outside `<path>`, even if a lock from
  another session exists for that worktree
- [ ] `git worktree remove <path>` is still blocked when CWD is inside `<path>` (existing behavior preserved)
- [ ] `rm -rf <path>` is still blocked when `<path>` is locked by another session (existing behavior preserved)
- [ ] `mvn -f client/pom.xml test` passes (no regressions)
- [ ] E2E: Running `git worktree remove` targeting a worktree locked by another session succeeds when
  CWD is outside the target

## Commit Type
bugfix

