# Plan: refactor-bash-handler-shared-utilities

## Goal
Extract shared utility code (`CD_PATTERN`, `COMMIT_PATTERN` constants and `extractCdDirectory`,
`isInsideCatWorktree` methods) duplicated across `WarnMainWorkspaceCommit`, `BlockWrongBranchCommit`,
and `VerifyStateInCommit` into a shared `BashHandlerUtils` class.

## Parent Requirements
None - internal code quality

## Origin
Deferred HIGH design concern from stakeholder review of `2.1-expand-worktree-commit-detection-hook`.
Design stakeholder flagged duplication of regex constants and utility methods across 3 BashHandler
implementations.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Refactoring shared code may break existing behavior if patterns differ subtly
- **Mitigation:** Run full test suite before and after; verify all 3 handlers pass existing tests

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnMainWorkspaceCommit.java` - remove duplicated code, use shared utils
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWrongBranchCommit.java` - remove duplicated code, use shared utils
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java` - remove duplicated code, use shared utils
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BashHandlerUtils.java` - new shared utility class

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Create `BashHandlerUtils` with shared constants and methods
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BashHandlerUtils.java`
- Update `WarnMainWorkspaceCommit`, `BlockWrongBranchCommit`, `VerifyStateInCommit` to use `BashHandlerUtils`
  - Files: all 3 handler files

## Post-conditions
- [ ] `BashHandlerUtils` contains `CD_PATTERN`, `COMMIT_PATTERN`, `extractCdDirectory`, `isInsideCatWorktree`
- [ ] No duplication of these constants/methods across handler files
- [ ] All existing handler tests pass
- [ ] E2E: `mvn -f client/pom.xml test` passes with zero failures
