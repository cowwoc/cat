# Plan: add-lock-schema-validation-tests

## Goal

Add comprehensive lock file schema validation tests across all callers of WorktreeContext.forSession():
BlockMainRebase, WarnBaseBranchEdit, RecordLearning, GetDiffOutput, and EnforceWorktreePathIsolation.
Currently each caller assumes a specific JSON structure but no test validates behavior when lock file
schema changes or fields are missing.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Test-only change
- **Mitigation:** No production code changes

## Files to Modify

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorktreeContextTest.java` (new)

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Add parametrized test validating lock file schema requirements:
  - Missing session_id field
  - Missing worktrees field
  - Malformed JSON
  - Unexpected field types
  - Empty lock file
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorktreeContextTest.java`

## Post-conditions

- [ ] Parametrized test covers all lock file schema edge cases
- [ ] All tests pass
- [ ] E2E: Verify WorktreeContext.forSession() handles malformed lock files gracefully