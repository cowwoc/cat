# Plan: add-benchmark-runner-tests

## Goal
Add comprehensive test coverage for the benchmark-runner tool (Java implementation after migration).

## Parent Requirements
None

## Current State
Zero automated tests for benchmark-runner functionality.

## Target State
TestNG test suite covering all 10 commands with edge cases, achieving >= 80% coverage.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (test-only)
- **Mitigation:** Tests use isolated temp directories and mock git repos

## Files to Modify
- `client/src/test/java/io/github/cowwoc/cat/hooks/skills/BenchmarkRunnerTest.java` (NEW)

## Pre-conditions
- [ ] 2.1-migrate-benchmark-runner-to-java is closed

## Sub-Agent Waves

### Wave 1
- Create BenchmarkRunnerTest.java with TestNG tests for all 10 commands
- Cover edge cases: empty inputs, missing fields, nested JSON, boundary conditions
- `initSprt()` carry-forward branch — when a test case exists in prior data but is NOT in `rerunIds`, its prior state
  (log_ratio, passes, fails, runs, decision, smoke_runs_done) must be carried forward with `carried_forward: true`.
  Add a test for `initSprt()` that provides prior data with a test case not in `rerunIds`.
- `updateSprt()` smoke counter increment — tests currently use `smoke_runs_done=3` (already at SMOKE_RUNS=3 limit),
  so the increment path when `currentSmoke < SMOKE_RUNS` is never exercised. Add a test that starts with
  `smoke_runs_done=0` or `smoke_runs_done=1` and verifies the counter increments.
- `mergeResults()` INCONCLUSIVE overall decision — tests cover all-ACCEPT and any-REJECT outcomes but not the mixed
  ACCEPT+INCONCLUSIVE case (no REJECT, not all ACCEPT). Add a test for this path.

## Post-conditions
- [ ] All tests pass
- [ ] >= 80% code coverage for BenchmarkRunner.java
- [ ] E2E: Run full test suite and confirm all tests pass
