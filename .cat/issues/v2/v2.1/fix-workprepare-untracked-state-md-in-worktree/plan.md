# Plan: fix-workprepare-untracked-state-md-in-worktree

## Problem
When a new issue directory exists as an untracked directory in the main workspace (STATE.md exists
on disk but is not committed to git), `IssueDiscovery` sets `createStateMd=false` because
`Files.isRegularFile(statePath)` returns true. When `work-prepare` creates a worktree from the
branch, the untracked directory is not present in the worktree. `WorkPrepare.java` then calls
`updateStateMd()` which throws `IOException` ("STATE.md not found in worktree"), causing
`work-prepare` to return ERROR.

The correct behavior is to fall back to `createStateMd()` when STATE.md is absent from the
worktree, since the issue is genuinely open. The untracked directory means there is no committed
STATE.md to preserve — creating a fresh one is correct.

## Satisfies
None — internal tooling bugfix

## Root Cause
`WorkPrepare.java` `executeWithLock()` calls `updateStateMd()` when `createStateMd=false` (set by
`IssueDiscovery.java` because `STATE.md` exists on disk in main workspace). For untracked issue
directories, STATE.md exists in the main workspace but is not committed to git — so it is absent
from the worktree. `updateStateMd()` throws `IOException` with no fallback, causing `work-prepare`
to return ERROR.

The previous fix (`fix-work-prepare-missing-state-md`) addressed the case where STATE.md does not
exist at all, but did not address the case where STATE.md exists as an untracked file (on disk but
not in git). IssueDiscovery still returns `createStateMd=false` for this case.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Only affects the specific scenario of untracked issue directories
- **Mitigation:** Existing `createStateMd()` path is already tested; we are just extending it to
  handle an additional case

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — In `executeWithLock()`,
  when `updateStateMd()` throws `IOException` indicating STATE.md is absent from the worktree, fall
  back to calling `createStateMd()` instead of propagating the error as ERROR status.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — Add regression
  test for the untracked issue directory scenario.

## Pre-conditions
- All dependent issues are closed

## Sub-Agent Waves

### Wave 1
1. **Step 1:** In `WorkPrepare.java` `executeWithLock()` around line 513, locate the
   `updateStateMd()` call. When `updateStateMd()` throws `IOException` with a message containing
   "STATE.md not found in worktree", catch the exception and call `createStateMd()` as the
   fallback. The issue is open and valid — it just needs a fresh STATE.md created in the worktree.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
2. **Step 2:** Add a regression test to `WorkPrepareTest.java` that sets up an issue with a
   STATE.md in the main workspace but the file untracked (not committed to git). Verify that
   `work-prepare` returns READY and that STATE.md is created in the worktree with status `open`.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
3. **Step 3:** Run tests: `mvn -f client/pom.xml test`
   Update STATE.md: status: closed, progress: 100%

## Post-conditions
- [ ] When `work-prepare` is invoked for an issue whose directory is untracked in git (STATE.md
  exists on disk but not committed), `work-prepare` returns READY instead of ERROR
- [ ] The worktree contains a fresh STATE.md with status `open`
- [ ] `WorkPrepareTest` includes a regression test for the untracked issue directory scenario
  verifying READY is returned
- [ ] No regression: issues with committed STATE.md still use `updateStateMd()` preserving
  existing fields
