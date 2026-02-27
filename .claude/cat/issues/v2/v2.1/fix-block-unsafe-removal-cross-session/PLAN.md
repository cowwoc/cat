# Plan: fix-block-unsafe-removal-cross-session

## Problem
`BlockUnsafeRemoval.getProtectedPaths()` has **inverted logic** for `git worktree remove`:

| Lock Owner | Current Behavior | Correct Behavior |
|---|---|---|
| Same session | NOT protected (skip) | PROTECTED (sibling agent may be using it) |
| Different session | PROTECTED (block) | NOT protected (cross-session cleanup is legitimate) |

When session A creates a worktree and session B runs cleanup (e.g., after a crash or via
`/cat:cleanup`), `isOwnedBySession()` returns false → the worktree is added to `protectedPaths`,
causing a false positive block even when CWD is not inside the target worktree.

Conversely, within the **same session**, the main agent and its subagents share a session ID. If
subagent A is working inside worktree X and subagent B tries to `git worktree remove` worktree X,
the current code skips the lock (same session) and only checks CWD. Since subagent B's CWD is
different from subagent A's worktree, the removal is **allowed** — potentially corrupting
subagent A's shell.

### Reproduction
```bash
# Bug 1: Cross-session cleanup blocked (false positive)
# Session A creates a worktree and acquires a lock, then crashes
# Session B tries to clean up:
git worktree remove /workspace/.claude/cat/worktrees/2.1-some-issue
# → UNSAFE DIRECTORY REMOVAL BLOCKED (should be allowed)

# Bug 2: Same-session sibling deletion allowed (false negative)
# Main agent spawns subagent A in worktree X (lock owned by same session)
# Main agent or subagent B tries to:
git worktree remove /workspace/.claude/cat/worktrees/X
# → ALLOWED (should be blocked — subagent A is using it)
```

### Root Cause
In `getProtectedPaths()` (line ~336):
```java
// Skip worktrees owned by the current session
if (isOwnedBySession(lockFile, sessionId))
    continue;
```
This logic is correct for `rm -rf` (protect other sessions' work from accidental sweeps, allow
cleaning your own) but **inverted** for `git worktree remove` (should protect same-session sibling
worktrees, allow cross-session cleanup).

**Key insight:** For `git worktree remove <path>`, protection should cover:
1. CWD inside target (shell corruption risk) — **both commands need this**
2. Same-session locked worktrees (sibling agent protection) — **only `git worktree remove`**
3. Main worktree root — **both commands need this**

## Satisfies
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must preserve `rm -rf` protection for other sessions' worktrees; must preserve
  CWD protection for both commands
- **Mitigation:** Add targeted tests for both cross-session and same-session scenarios; run all
  existing tests

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` - refactor
  `checkWorktreeRemove()` to use worktree-specific protection logic (invert lock ownership check)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` - add
  regression tests for both cross-session and same-session scenarios (TDD: write failing tests first)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Write failing regression tests (TDD):** Add two tests to `BlockUnsafeRemovalTest.java`:

   **Test A:** `worktreeRemoveAllowsWhenDifferentSessionLock` — cross-session cleanup should succeed:
   - Create a temp git repo with `.claude/cat/locks/task-123.lock` containing
     `{"session_id": "other-session", "timestamp": "..."}` (recent, not stale)
   - Create worktree directory at `.claude/cat/worktrees/task-123`
   - Call `handler.check("git worktree remove <worktreePath>", mainWorktreeRoot, ..., "my-session")`
   - Assert `result.blocked()` is false

   **Test B:** `worktreeRemoveBlocksWhenSameSessionLock` — sibling agent protection should block:
   - Create same setup but with lock containing `{"session_id": "my-session", "timestamp": "..."}`
   - Call `handler.check("git worktree remove <worktreePath>", mainWorktreeRoot, ..., "my-session")`
   - Assert `result.blocked()` is true
   - Assert error message contains "UNSAFE"

   Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`

2. **Verify tests fail:** Run `mvn -f client/pom.xml verify -Dtest=BlockUnsafeRemovalTest` to confirm
   both new tests fail, proving both bugs exist.

3. **Refactor `checkWorktreeRemove()`:** Instead of delegating to `checkProtectedPaths()` (which uses
   the rm-oriented `getProtectedPaths()`), implement worktree-specific protection directly:

   Create a new private method `getWorktreeRemoveProtectedPaths(workingDirectory, sessionId)` that
   returns protected paths with **inverted** lock logic:
   - Include main worktree root (same as current)
   - Include CWD (same as current)
   - Include worktrees locked by the **same** session (inverted from current)
   - Exclude worktrees locked by **different** sessions (inverted from current)
   - Still skip stale locks (same as current)

   Update `checkWorktreeRemove()` to call this new method instead of `checkProtectedPaths()`.

   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`

4. **Verify `rm -rf` behavior unchanged:** Confirm existing `rmBlocksWhenDeletingLockedWorktree`
   test still passes (different-session lock protection preserved for `rm -rf`).

5. **Run all tests:** `mvn -f client/pom.xml verify` — all tests must pass including checkstyle/PMD.

6. **E2E verification:** In the main workspace:
   - Create a fresh lock owned by a fake session:
     `echo '{"session_id":"aaaaaaaa-0000-0000-0000-000000000000","timestamp":"2026-02-27T00:00:00Z"}' > /workspace/.claude/cat/locks/test-e2e-verify.lock`
   - Attempt `git worktree remove /workspace/.claude/cat/worktrees/test-e2e-verify 2>&1 || true`
   - Confirm the error is from git ("not a valid directory" or similar), NOT from the hook
   - Clean up the test lock file

## Post-conditions
- [ ] `worktreeRemoveAllowsWhenDifferentSessionLock` test exists and passes
- [ ] `worktreeRemoveBlocksWhenSameSessionLock` test exists and passes
- [ ] `git worktree remove <path>` is allowed when CWD is outside `<path>` and lock is from another
  session (cross-session cleanup works)
- [ ] `git worktree remove <path>` is blocked when lock is from the same session (sibling agent
  protection)
- [ ] `git worktree remove <path>` is still blocked when CWD is inside `<path>` (existing behavior)
- [ ] `rm -rf <path>` is still blocked when `<path>` is locked by another session (existing behavior)
- [ ] `mvn -f client/pom.xml verify` passes (no regressions)
- [ ] E2E: Running `git worktree remove` targeting a worktree locked by another session succeeds when
  CWD is outside the target

## Commit Type
bugfix
