# Plan: add-migration-phase13-tests

## Goal
Add test coverage for migration script Phase 13 (rename ## Satisfies → ## Parent Requirements in issue
PLAN.md files) to verify correct files are selected, closed issues are skipped, idempotency works, and
missing STATE.md files are handled correctly.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (tests only)
- **Mitigation:** Tests run in isolated temp directories, no impact on production data

## Files to Modify
- `plugin/tests/migrations/test-2.1-phase-13.bats` — new test suite for Phase 13

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create `plugin/tests/migrations/test-2.1-phase-13.bats` with test cases:
  - Open issue with `## Satisfies` is renamed to `## Parent Requirements`
  - Closed issue (STATE.md status: closed) is NOT modified
  - Idempotency: running migration twice produces same result (0 changes on second run)
  - Missing STATE.md: migration proceeds with warning log message
  - Files: `plugin/tests/migrations/test-2.1-phase-13.bats`
- Run the tests to confirm they pass
  - Files: `plugin/tests/migrations/test-2.1-phase-13.bats`

## Post-conditions
- [ ] Test suite exists at `plugin/tests/migrations/test-2.1-phase-13.bats`
- [ ] All 4 test cases pass: open/closed handling, idempotency, missing STATE.md
- [ ] Tests run in isolated temp directories with no side effects on real data
