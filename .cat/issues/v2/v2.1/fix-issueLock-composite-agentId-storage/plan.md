# Plan: fix-issueLock-composite-agentId-storage

## Goal
Fix the lock ownership comparison for subagents: `IssueLock.writeLockToTempFile()` stores plain
`sessionId` in the worktrees map, but `BlockUnsafeRemoval.determineLockReason()` compares against the
composite `agentId` (`sessionId/subagents/{nativeId}` for subagents). This causes subagents to be
incorrectly blocked with `LOCKED_BY_OTHER_AGENT` when attempting to remove their own worktrees.

## Parent Requirements
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Changing the lock file format requires updating both readers and writers consistently.
  Existing lock files written before this fix store plain sessionId — need to handle migration or
  fall back gracefully.
- **Mitigation:** Add a secondary check in `determineLockReason()` that extracts the sessionId
  portion from the composite agentId when the full composite match fails, preserving backward
  compatibility with existing lock files.

## Approaches

### A: Update IssueLock to store composite agentId
- **Risk:** MEDIUM
- **Scope:** 3-4 files (IssueLock, IssueLock callers, tests)
- **Description:** Change `IssueLock.acquire()` and `update()` to accept composite agentId and store
  it in the worktrees map instead of plain sessionId. Requires updating all callers.

### B: Update BlockUnsafeRemoval to compare by sessionId prefix
- **Risk:** LOW
- **Scope:** 1-2 files (BlockUnsafeRemoval, tests)
- **Description:** In `determineLockReason()`, when full agentId comparison fails, extract the
  sessionId prefix from the composite agentId (before `/subagents/`) and compare against the stored
  value. Backward-compatible with existing lock files.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` — update
  `determineLockReason()` to handle composite agentId comparison (Approach B)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` — add tests
  verifying subagent lock ownership works with plain sessionId in lock file

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `determineLockReason()` in `BlockUnsafeRemoval` to extract sessionId from composite agentId
  when full match fails, allowing subagents to match their own session's locks
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
- Add integration test: subagent with composite agentId can remove worktree locked by main agent
  with matching sessionId prefix
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`
- Run all tests: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] Subagent with composite agentId (`sessionId/subagents/{nativeId}`) can remove a worktree
  whose lock was acquired by the main agent (stores plain `sessionId` in worktrees map)
- [ ] Main agent lock removal still works correctly
- [ ] Cross-session blocking still works correctly (different sessionId is still blocked)
- [ ] All tests pass

## Commit Type
bugfix

## Deferred From
Issue `2.1-remove-cat-agent-id-command-prefix` — architecture stakeholder review concern.
