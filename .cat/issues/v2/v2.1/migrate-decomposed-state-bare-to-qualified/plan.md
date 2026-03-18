# Plan: migrate-decomposed-state-bare-to-qualified

## Problem
Existing decomposed parent issues have "Decomposed Into" sections using bare sub-issue names (e.g., `rename-config-java-core`) created before the fix to `fix-decompose-issue-bare-names`. These bare names cause `allSubissuesClosed()` to silently skip them, resulting in incorrect parent issue discovery.

## Expected vs Actual
- **Expected:** All "Decomposed Into" sections use fully-qualified names (e.g., `2.1-rename-config-java-core`)
- **Actual:** Some use bare names, which bypass the sub-issue closed check

## Root Cause
The decompose-issue-agent skill previously created bare names before the fix in `fix-decompose-issue-bare-names` was applied.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — purely data migration
- **Mitigation:** Script validates format before and after transformation

## Files to Modify
- `plugin/migrations/2.1.sh` — Add phase to update all STATE.md files with bare names in "Decomposed Into" sections
- All `.cat/issues/v*/v*.*/**/STATE.md` files with bare sub-issue names (auto-updated by migration)

## Test Cases
- [ ] Migration idempotently converts all bare names to qualified names
- [ ] No STATE.md files are corrupted or lose other content
- [ ] Migration skips STATE.md files that already use qualified names
- [ ] Edge case: STATE.md without "Decomposed Into" section is unchanged

## Pre-conditions
- [ ] `fix-decompose-issue-bare-names` is closed (prevents new bare names from being created)

## Sub-Agent Waves

### Wave 1
- Add migration phase to `plugin/migrations/2.1.sh` to transform bare names to qualified names
  - Files: `plugin/migrations/2.1.sh`
- Create Bats tests verifying the migration behavior
  - Files: `tests/hooks/migration-2.1.bats`

### Wave 2
- Install bats via `npm install -g bats` and run `bats tests/hooks/migration-2.1.bats` to execute all
  Phase 17 tests; all 6 tests must pass with exit code 0
  - Files: `tests/hooks/migration-2.1.bats` (read-only verification; no source changes unless a test
    fails, in which case fix the failing test or the migration script)
- Add a TestNG test to `IssueDiscoveryTest.java` that: (1) creates a temp project with a parent
  STATE.md whose "Decomposed Into" section lists bare sub-issue names (e.g., `sub-task`), (2) runs
  the Phase 17 migration on that temp project by invoking `plugin/migrations/2.1.sh` as a subprocess
  or by replicating its awk transformation on the STATE.md file, (3) asserts that the STATE.md now
  contains fully-qualified names (e.g., `2.1-sub-task`), and (4) calls `IssueDiscovery.findNextIssue()`
  with the parent and asserts the result reflects correct `allSubissuesClosed()` behavior (parent is
  `Decomposed` when sub-issues are open, `Found` when all sub-issues are closed)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java`

## Post-conditions
- [ ] All existing STATE.md "Decomposed Into" sections use fully-qualified names
- [ ] Migration script is idempotent (running multiple times produces same result)
- [ ] Bats tests pass (phase behavior validated)
- [ ] E2E: Run migration on a test repo with bare names, verify all are converted and `allSubissuesClosed()` behaves correctly
