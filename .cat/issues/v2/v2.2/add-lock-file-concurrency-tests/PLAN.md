# Plan: add-lock-file-concurrency-tests

## Goal

Add tests for concurrent lock file access in EnforceWorktreePathIsolation. The current tests only
cover the happy path with well-formed lock files created synchronously. Production may encounter
lock files deleted after directory scan, malformed JSON, or missing session_id fields.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Test-only change
- **Mitigation:** No production code changes

## Files to Modify

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add edge case tests for lock file scanning:
  - Lock file deleted between scan and usage
  - Malformed lock JSON
  - Lock file with missing session_id field
  - Lock file with missing worktrees field
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`

## Post-conditions

- [ ] Edge case tests added for concurrent/malformed lock file scenarios
- [ ] All tests pass
- [ ] E2E: Verify malformed lock files do not crash the isolation handler