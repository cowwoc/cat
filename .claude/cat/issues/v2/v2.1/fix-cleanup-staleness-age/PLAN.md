# Plan: fix-cleanup-staleness-age

## Problem
Cleanup-agent derives lock staleness age from branch commit timestamps via `git log`, but this is incorrect when a
session recently acquired a lock for a branch with old commits. A worktree can be misclassified as stale even though
it was locked recently.

## Parent Requirements
None

## Reproduction Code
```
// A worktree with a branch whose last commit was 2 days ago,
// but whose lock file was created 5 minutes ago.
// cleanup-agent incorrectly classifies it as stale because
// it only checks the commit timestamp, not the lock file mtime.
computeAge(branchLastCommitTime)  // returns 2 days → stale (wrong)
```

## Expected vs Actual
- **Expected:** staleness age = now - max(branch_last_commit_time, lock_file_mtime), so a recently-locked worktree
  with old commits is classified as "recent"
- **Actual:** staleness age = now - branch_last_commit_time, causing recently-locked worktrees with old commits to be
  incorrectly classified as stale

## Root Cause
`GetCleanupOutput.java` computes age solely from the branch's last commit timestamp, ignoring the lock file's
modification time. The lock file mtime reflects the most recent session activity and must be factored in.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Cleanup-agent may retain artifacts longer than before in edge cases where lock files are newer
  than commits — this is the correct behavior.
- **Mitigation:** Regression test covering the edge case; run full test suite before merge.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/client/GetCleanupOutput.java` - update age computation to use
  `max(branch_last_commit_time, lock_file_mtime)`
- `client/src/test/java/io/github/cowwoc/cat/client/GetCleanupOutputTest.java` - add regression test for
  old-commit + recent-lock scenario

## Test Cases
- [ ] Old commits + recent lock file → classified as "recent"
- [ ] Old commits + old lock file → classified as "stale"
- [ ] Recent commits + no lock file → classified as "recent"

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `GetCleanupOutput.java` to compute staleness age as `now - max(branch_last_commit_time, lock_file_mtime)`
  - Files: `client/src/main/java/io/github/cowwoc/cat/client/GetCleanupOutput.java`
- Add regression test in `GetCleanupOutputTest.java` covering the scenario: old branch commits but recently-created
  lock file → artifact classified as "recent" not "stale"
  - Files: `client/src/test/java/io/github/cowwoc/cat/client/GetCleanupOutputTest.java`
- Run tests: `mvn -f client/pom.xml test`
- Update STATE.md to closed with 100% progress

## Post-conditions
- [ ] Bug fixed: staleness age uses `max(branch_commit_time, lock_mtime)`
- [ ] Regression test added and passing
- [ ] No new issues introduced
- [ ] E2E: run cleanup-agent on a worktree with old branch commits but a recently-created lock file, confirm it is
  classified as "recent" not "stale"
