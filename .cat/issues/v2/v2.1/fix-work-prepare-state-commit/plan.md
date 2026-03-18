# Plan: fix-work-prepare-state-commit

## Problem
`WorkPrepare.updateStateMd()` modifies STATE.md in the worktree (sets status to `in-progress`, adds `Target Branch` field) but does not commit the changes. This leaves STATE.md dirty, causing the implement phase's pre-spawn check to fail with "BLOCKED: dirty planning file detected."

## Parent Requirements
None

## Reproduction Code
```
WorkPrepare.java, line 532:
  updateStateMd(worktreePath, issuePath, projectDir, targetBranch);
```

After this call, STATE.md has uncommitted changes visible in `git diff`, which blocks implement phase from spawning subagents.

## Expected vs Actual
- **Expected:** `updateStateMd()` updates STATE.md AND commits the changes, leaving the worktree clean
- **Actual:** `updateStateMd()` updates STATE.md but does NOT commit, leaving the worktree dirty; implement phase blocks with "BLOCKED: dirty planning file detected"

## Root Cause
The `updateStateMd()` method (line 1529) writes STATE.md to disk via `Files.writeString()` but has no subsequent `git add` + `git commit` call. A similar method `createStateFileAndCommit()` (line 1618) handles the "file doesn't exist" case and *does* commit. The "file exists" case was missing the commit step.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — this adds a missing operation that should have always occurred
- **Mitigation:** Add regression test that verifies STATE.md is committed after `updateStateMd()` is called

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Add git commit to `updateStateMd()` method

## Test Cases
- [ ] `updateStateMd()` commits STATE.md when status changes to `in-progress`
- [ ] `updateStateMd()` commits STATE.md when target branch is added
- [ ] Working tree is clean after `updateStateMd()` returns (no dirty STATE.md)

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Waves

## Sub-Agent Waves

### Wave 1
- Modify `updateStateMd()` to commit STATE.md after writing
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - Pattern: After `Files.writeString(statePath, ...)` at line ~1540, add `git add` and `git commit` similar to the `createStateFileAndCommit()` pattern
  - Ensure the commit message follows CAT conventions (e.g., "planning: update STATE.md status to in-progress")
- Add regression test to verify STATE.md is committed
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
  - Add test case: `updateStateMd_commitsState()` to verify git status is clean after the method completes

## Post-conditions
- [ ] `updateStateMd()` commits STATE.md to the worktree branch after updating
- [ ] Regression test verifies STATE.md is committed (no dirty files after call)
- [ ] No new test failures; existing tests still pass
- [ ] E2E: Run `/cat:work 2.1-migrate-decomposed-state-bare-to-qualified` and verify implement phase no longer blocks with "dirty planning file detected"
