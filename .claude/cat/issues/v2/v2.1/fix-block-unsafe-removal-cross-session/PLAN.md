# Plan: fix-block-unsafe-removal-cross-session

## Problem

Two bugs in `BlockUnsafeRemoval`:

**Bug 1: No sibling agent protection.** Within a session, all agents share `session_id`. The hook
skips locks from the same session (`isOwnedBySession` → true → not protected). A sibling agent can
delete another agent's worktree unchallenged.

**Bug 2: Misleading error messages.** When a block occurs, the error always blames CWD/shell
corruption regardless of the actual cause (lock ownership, CWD containment, or main worktree
protection). Users waste time fixing CWD when the real fix is releasing a lock.

### Current Lock Logic

```java
// In getProtectedPaths():
if (isOwnedBySession(lockFile, sessionId))  // same session → skip
    continue;
if (isStale(lockFile))                       // >4h old → skip
    continue;
// Otherwise → add worktree to protectedPaths
```

This provides session-level protection but not agent-level protection.

## Solution: Agent-Level Lock Ownership via `CAT_AGENT_ID` Env Prefix

### Design

Each agent has a unique `catAgentId` (from `2.1-per-agent-skill-markers`):
- Main agent: `{sessionId}`
- Subagent: `{sessionId}/subagents/{agent_id}`

**Lock files** store the owning `agent_id` alongside `session_id`:
```json
{
  "session_id": "370b23ac-...",
  "agent_id": "370b23ac-.../subagents/abc123",
  "created_at": 1740700000,
  "worktree": "/workspace/.claude/cat/worktrees/issue-name",
  "created_iso": "2026-02-28T00:00:00Z"
}
```

**Agents self-identify** to the hook by prefixing dangerous commands with a `CAT_AGENT_ID` env var:
```bash
CAT_AGENT_ID=370b23ac-.../subagents/abc123 git worktree remove /path/to/worktree
```

**The hook** extracts `CAT_AGENT_ID` from the command string and compares it against the lock
file's `agent_id` to determine ownership.

### Protection Rules

**For `git worktree remove` and `rm -rf`:**

| Condition | Action |
|---|---|
| No `CAT_AGENT_ID` in command + lock exists | **Block** (agent must self-identify) |
| `CAT_AGENT_ID` matches lock's `agent_id` | **Allow** (owner deleting own worktree) |
| `CAT_AGENT_ID` doesn't match lock's `agent_id` | **Block** (sibling/cross-session protection) |
| No lock exists | **Allow** (unowned worktree) |
| Stale lock (>4h) | **Allow** (dead session) |
| CWD inside target | **Block** (shell corruption, regardless of ownership) |
| Target is main worktree | **Block** (repository protection) |

**Fail-safe:** Missing `CAT_AGENT_ID` is treated as "unknown agent" and blocked when a lock exists.
This means legacy commands without the prefix are rejected, forcing agents to self-identify.

### Backward Compatibility

The `session_id` field remains in lock files for backward compatibility. Hooks that don't understand
`agent_id` still work at the session level. The new `agent_id` field is additive.

## Satisfies
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Agents must be instructed to include `CAT_AGENT_ID=` prefix on worktree removal
  and `rm -rf` commands. Forgetting causes a block (fail-safe, not fail-open).
- **Mitigation:** Inject instructions at SessionStart/SubagentStart; fail-safe on missing ID;
  comprehensive tests for all scenarios.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java` — add `agentId` parameter
  to `acquire()`, store in lock file as `agent_id`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — pass `agentId` to lock
  acquisition (received as CLI argument from agent context)
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` — extract
  `CAT_AGENT_ID` from command string; compare against lock's `agent_id`; emit reason-specific error
  messages
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` — add tests
  for agent-level protection and error messages
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockTest.java` — update tests for new
  `agent_id` field
- Agent instruction injection point (SessionStart/SubagentStart handler) — instruct agents to
  prefix dangerous commands with `CAT_AGENT_ID=`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Extend lock file format:** Add `agent_id` parameter to `IssueLock.acquire()`. Store it in the
   lock JSON alongside `session_id`. The `agent_id` is the full agentId string (e.g.,
   `"370b23ac-.../subagents/abc123"` for subagents, `"370b23ac-..."` for main agent).

   Update `isOwnedBySession()` → `isOwnedByAgent()` to compare `agent_id` instead of `session_id`.
   Keep `isOwnedBySession()` as a fallback for lock files that predate this change (no `agent_id`
   field → fall back to session-level comparison).

   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`

2. **Pass agentId through WorkPrepare:** Add `--agent-id` CLI argument to `work-prepare`. The agent
   passes its agentId (available in conversation context from SessionStart/SubagentStart injection).
   WorkPrepare forwards it to `IssueLock.acquire()`.

   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

3. **Extract CAT_AGENT_ID from commands in BlockUnsafeRemoval:** Add a method to parse the
   `CAT_AGENT_ID=<value>` env var prefix from the command string. Use a pattern like:
   ```
   CAT_AGENT_ID=(\S+)\s+(.*)
   ```
   If the prefix is present, extract the agentId and the actual command separately.

   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`

4. **Refactor `getProtectedPaths()` to return protection reasons:** Change from `Set<Path>` to a
   structure that associates each path with a reason:
   - `CWD` — CWD is inside or equal to the target
   - `LOCKED_BY_OTHER_AGENT` — worktree locked by a different agent (same or different session)
   - `LOCKED_UNKNOWN_AGENT` — lock exists but command has no `CAT_AGENT_ID` (fail-safe)
   - `MAIN_WORKTREE` — target is the main git worktree root

   The lock comparison logic becomes:
   ```
   if (lock has agent_id field):
     if (CAT_AGENT_ID from command matches lock's agent_id) → skip (owner)
     else → protect (different agent)
   else (legacy lock without agent_id):
     if (session_id matches) → skip (owner, backward compat)
     else → protect (different session)
   if (no CAT_AGENT_ID in command and lock exists) → protect (unknown agent, fail-safe)
   ```

   Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`

5. **Emit reason-specific error messages in `checkProtectedPaths()`:**

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

   **For lock-based protection (different agent or missing CAT_AGENT_ID):**
   ```
   UNSAFE DIRECTORY REMOVAL BLOCKED

   Attempted: {command} {target}
   Problem:   Worktree is locked by another agent
   Lock owner: {agent_id or session_id}
   Your ID:    {CAT_AGENT_ID or "(not provided)"}
   Target:     {target}

   WHY THIS IS BLOCKED:
   - Another agent may be actively using this worktree
   - Deleting it could corrupt that agent's shell and lose uncommitted work

   WHAT TO DO:
   1. If you own this worktree, prefix your command:
      CAT_AGENT_ID=<your-agent-id> {command} {target}
   2. If another agent owns it, release the lock first:
      issue-lock force-release {issue_id}
   3. Or use /cat:cleanup to release all stale locks
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

6. **Write TDD tests for all scenarios:** Add tests to `BlockUnsafeRemovalTest.java`:

   **Test A:** `worktreeRemoveBlockedWhenNoAgentId` — lock exists, no `CAT_AGENT_ID` in command →
   blocked (fail-safe)

   **Test B:** `worktreeRemoveAllowedWhenAgentIdMatchesLock` — lock has `agent_id`, command has
   matching `CAT_AGENT_ID` → allowed

   **Test C:** `worktreeRemoveBlockedWhenAgentIdMismatch` — lock has `agent_id`, command has
   different `CAT_AGENT_ID` → blocked (sibling protection)

   **Test D:** `worktreeRemoveAllowedWhenNoLock` — no lock file, no `CAT_AGENT_ID` → allowed

   **Test E:** `worktreeRemoveBlockedByCwdShowsCwdMessage` — CWD inside target → blocked with
   CWD-specific message, even if `CAT_AGENT_ID` matches

   **Test F:** `worktreeRemoveFallsBackToSessionIdForLegacyLock` — lock without `agent_id` field,
   same `session_id` → allowed (backward compat)

   **Test G:** `lockBlockedShowsLockMessage` — verify lock-based error mentions "locked by another
   agent" and shows actionable guidance

   Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`

7. **Update IssueLock tests:** Add tests verifying `agent_id` is stored in lock files.

   Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockTest.java`

8. **Inject CAT_AGENT_ID instructions into agent context:** Update SessionStart and SubagentStart
   handlers to instruct agents: "When running `git worktree remove` or `rm -rf` on worktree
   directories, prefix your command with `CAT_AGENT_ID=<your-agent-id>`."

   Files: SessionStart handler, SubagentStartHook handler (or the skills/instructions that
   orchestrate worktree removal — e.g., `safe-rm`, `cleanup`, `work-merge` skills)

9. **Run all tests:** `mvn -f client/pom.xml verify` — all tests must pass including checkstyle/PMD.

10. **E2E verification:** In the main workspace:
    - Create a lock with `agent_id`:
      ```bash
      echo '{"session_id":"aaaa","agent_id":"aaaa/subagents/xyz","created_at":1740700000,"worktree":"/workspace/.claude/cat/worktrees/test-e2e","created_iso":"2026-02-28T00:00:00Z"}' > /workspace/.claude/cat/locks/test-e2e.lock
      ```
    - Create dummy worktree: `mkdir -p /workspace/.claude/cat/worktrees/test-e2e`
    - Test 1: `git worktree remove /workspace/.claude/cat/worktrees/test-e2e` → blocked (no
      `CAT_AGENT_ID`)
    - Test 2: `CAT_AGENT_ID=wrong git worktree remove ...` → blocked (mismatch)
    - Test 3: `CAT_AGENT_ID=aaaa/subagents/xyz git worktree remove ...` → allowed (owner)
    - Clean up lock file and dummy dir

## Post-conditions
- [ ] Lock files contain `agent_id` field when acquired with the new code
- [ ] `CAT_AGENT_ID` env prefix in commands is parsed by `BlockUnsafeRemoval`
- [ ] Matching `CAT_AGENT_ID` allows worktree deletion (owner protection)
- [ ] Mismatched or missing `CAT_AGENT_ID` blocks deletion when lock exists (sibling protection)
- [ ] Legacy locks without `agent_id` fall back to `session_id` comparison
- [ ] CWD-based blocks show "working directory is inside" with `cd` guidance
- [ ] Lock-based blocks show "locked by another agent" with actionable guidance
- [ ] Main worktree blocks show "main git worktree" message
- [ ] All existing tests pass — no regressions
- [ ] `mvn -f client/pom.xml verify` passes
- [ ] E2E: Agent-level protection works end-to-end with `CAT_AGENT_ID` prefix

## Commit Type
bugfix
