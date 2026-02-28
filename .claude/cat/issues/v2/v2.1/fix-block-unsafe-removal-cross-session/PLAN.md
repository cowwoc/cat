# Plan: fix-block-unsafe-removal-cross-session

## Problem
`BlockUnsafeRemoval` produces a **misleading error message** when `git worktree remove` is blocked
due to a lock from another session. The error says:
```
Problem:   A protected path is inside the deletion target
WHY THIS IS BLOCKED:
- Deleting a directory containing your current location corrupts the shell session
```
This implies a CWD-containment problem, but the actual reason is that the worktree is locked by
another session. The user wastes time trying to fix CWD when the real fix is releasing the lock.

### Current Logic (Correct — Approach A)
The `getProtectedPaths()` lock logic implements the ownership model correctly:

| Lock Owner | Protected? | Rationale |
|---|---|---|
| Same session | No (skip) | Owner can clean up their own worktrees |
| Different session | Yes (block) | Protects another session's active work |
| No lock | No | Unowned worktrees can be freely removed |
| Stale lock (>4h) | No (skip) | Dead session, safe to clean up |

This applies identically to both `rm -rf` and `git worktree remove`, which is the correct behavior
for Approach A (must hold the lock or no lock to delete).

### Sibling Agent Limitation
Within a single session, all agents (main + subagents) share the same `session_id` in both lock
files and PreToolUse hook input. This means sibling agents can delete each other's worktrees — they
all look like "the same session."

`2.1-drop-cat-agent-id` introduced per-agent `agentId` values (main: `{sessionId}`, subagent:
`{sessionId}/subagents/{agent_id}`), but these are only available in:
- **Agent conversation context** (injected at SessionStart / SubagentStart)
- **SkillLoader** (passed as `$0` argument to `load-skill`)

They are **NOT** available in:
- **PreToolUse hook input** (only has `session_id`)
- **Lock files** (only store `session_id`)

Per-agent sibling protection would require propagating `agentId` to both lock files and PreToolUse
hooks — a separate infrastructure issue beyond this bugfix scope.

### Actual Bug
The error message in `checkProtectedPaths()` is generic — it always blames CWD/shell corruption
regardless of whether the block is caused by:
1. CWD inside target (shell corruption risk)
2. Lock from another session (ownership protection)
3. Main worktree root protection

The "WHAT TO DO" section always suggests `cd /workspace` which is useless when the block is
lock-based.

## Satisfies
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Error message changes must remain clear and actionable
- **Mitigation:** Add tests for each block reason; verify existing tests pass

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` - differentiate
  error messages based on WHY the path is protected (lock vs CWD vs main worktree)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` - add tests
  verifying correct error messages for each block reason

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Refactor `getProtectedPaths()` to return protection reasons:** Change the return type from
   `Set<Path>` to a structure that associates each protected path with a reason enum:
   - `CWD` — CWD is inside or equal to the target
   - `LOCKED_BY_OTHER_SESSION` — worktree locked by a different session
   - `MAIN_WORKTREE` — target is the main git worktree root

   One approach: return a `Map<Path, ProtectionReason>` or a `Set<ProtectedPath>` record.
   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`

2. **Update `checkProtectedPaths()` to emit reason-specific error messages:**

   **For CWD protection:**
   ```
   UNSAFE DIRECTORY REMOVAL BLOCKED

   Attempted: {command} {target}
   Problem:   Your shell's working directory is inside the deletion target
   CWD:       {cwd}
   Target:    {target}

   WHY THIS IS BLOCKED:
   - Deleting a directory containing your current location corrupts the shell session
   - All subsequent Bash commands will fail with "Exit code 1"

   WHAT TO DO:
   1. Change directory first: cd /workspace
   2. Then retry: {command} {target}
   ```

   **For lock-based protection:**
   ```
   UNSAFE DIRECTORY REMOVAL BLOCKED

   Attempted: {command} {target}
   Problem:   Worktree is locked by another session
   Lock:      {session_id}
   Target:    {target}

   WHY THIS IS BLOCKED:
   - Another Claude session may be actively using this worktree
   - Deleting it could corrupt that session's shell and lose uncommitted work

   WHAT TO DO:
   1. Release the lock first: issue-lock force-release {issue_id}
   2. Then retry: {command} {target}
   - Or use /cat:cleanup to release all stale locks
   ```

   **For main worktree protection:**
   ```
   UNSAFE DIRECTORY REMOVAL BLOCKED

   Attempted: {command} {target}
   Problem:   Target is the main git worktree
   Target:    {target}

   WHY THIS IS BLOCKED:
   - Deleting the main worktree would destroy the entire repository

   WHAT TO DO:
   - This operation is not allowed. Use a more specific target path.
   ```

   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`

3. **Write tests for each error message variant (TDD):** Add tests to `BlockUnsafeRemovalTest.java`:

   **Test A:** `worktreeRemoveBlockedByLockShowsLockMessage` — verify lock-based error message:
   - Create temp git repo with `.claude/cat/locks/task-123.lock` owned by `"other-session"`
   - Create worktree dir at `.claude/cat/worktrees/task-123`
   - Call `handler.check("git worktree remove <path>", mainRoot, ..., "my-session")`
   - Assert `result.blocked()` is true
   - Assert message contains "locked by another session"
   - Assert message contains "issue-lock force-release" (actionable guidance)
   - Assert message does NOT contain "current location" (CWD message)

   **Test B:** `worktreeRemoveBlockedByCwdShowsCwdMessage` — verify CWD-based error message:
   - Create temp git repo with worktree dir
   - Set CWD to inside the worktree
   - Call `handler.check("git worktree remove <path>", cwdInsideWorktree, ..., "session1")`
   - Assert `result.blocked()` is true
   - Assert message contains "working directory is inside"
   - Assert message does NOT contain "locked by another session"

   **Test C:** `worktreeRemoveAllowedWhenSameSessionLock` — verify owner can delete:
   - Create temp git repo with lock owned by `"my-session"`
   - Call with session `"my-session"`, CWD outside target
   - Assert `result.blocked()` is false

   Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`

4. **Run all tests:** `mvn -f client/pom.xml verify` — all tests must pass including checkstyle/PMD.

5. **E2E verification:** In the main workspace:
   - Create a lock owned by a fake session:
     `echo '{"session_id":"aaaaaaaa-0000-0000-0000-000000000000","timestamp":"2026-02-27T00:00:00Z"}' > /workspace/.claude/cat/locks/test-e2e-verify.lock`
   - Create a dummy worktree dir: `mkdir -p /workspace/.claude/cat/worktrees/test-e2e-verify`
   - Attempt `git worktree remove /workspace/.claude/cat/worktrees/test-e2e-verify 2>&1`
   - Confirm error message contains "locked by another session" and "issue-lock force-release"
   - Clean up: remove lock file and dummy dir

## Post-conditions
- [ ] Lock-based blocks show "locked by another session" with `issue-lock force-release` guidance
- [ ] CWD-based blocks show "working directory is inside" with `cd` guidance
- [ ] Main worktree blocks show "main git worktree" message
- [ ] `worktreeRemoveAllowedWhenSameSessionLock` test exists and passes (owner can delete)
- [ ] `worktreeRemoveBlockedByLockShowsLockMessage` test exists and passes
- [ ] `worktreeRemoveBlockedByCwdShowsCwdMessage` test exists and passes
- [ ] All existing tests pass — `rm -rf` behavior unchanged
- [ ] `mvn -f client/pom.xml verify` passes (no regressions)
- [ ] E2E: Lock-based block error message is actionable (shows exact command to release lock)

## Commit Type
bugfix
