# Plan: enforce-lock-before-subagent-spawn

## Goal
Add `EnforceLockBeforeSubagentSpawn` handler to `PreIssueHook` so that the main agent cannot spawn
implementation subagents (e.g., `cat:work-execute`) without first holding an issue-worktree lock. This closes
the enforcement gap where the main agent bypasses `cat:work-implement-agent` and spawns subagents directly.

## Parent Requirements
- enforce-jvmscope-env-access

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** `WorktreeLock.findIssueIdForSession()` may not exist or may need to be added; IO errors during
  lock lookup must be handled gracefully.
- **Mitigation:** Block on IO error (fail-safe); write tests for all three cases before implementing.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceLockBeforeSubagentSpawn.java` - new handler
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreIssueHook.java` - register new handler before
  `EnforceCommitBeforeSubagentSpawn`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceLockBeforeSubagentSpawnTest.java` - new tests

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create `EnforceLockBeforeSubagentSpawnTest.java` with failing tests for:
  - No lock held → spawn blocked with actionable guidance
  - Valid lock held by current session → spawn allowed
  - IO error reading lock → spawn blocked with error message
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceLockBeforeSubagentSpawnTest.java`

### Wave 2
- Implement `EnforceLockBeforeSubagentSpawn.java` in `client/src/main/java/io/github/cowwoc/cat/hooks/task/`:
  - Call `WorktreeLock.findIssueIdForSession(sessionId)` (or equivalent lock-lookup API)
  - Block if no lock is found or on IO error; allow if lock belongs to current session
  - Block message must include actionable guidance: how to acquire a lock (use `/cat:work`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceLockBeforeSubagentSpawn.java`
- Register handler in `PreIssueHook` immediately before `EnforceCommitBeforeSubagentSpawn`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PreIssueHook.java`

### Wave 3
- Run `mvn -f client/pom.xml test` and fix any failures

## Post-conditions
- [ ] `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceLockBeforeSubagentSpawn.java` exists
- [ ] Handler is registered in `PreIssueHook` immediately before `EnforceCommitBeforeSubagentSpawn`
- [ ] Handler blocks spawn when no issue lock is held by the current session
- [ ] Block message includes actionable guidance explaining how to acquire a lock
- [ ] Tests cover no-lock, valid-lock, and IO-error cases
- [ ] `mvn -f client/pom.xml test` exits 0 (all tests pass)
