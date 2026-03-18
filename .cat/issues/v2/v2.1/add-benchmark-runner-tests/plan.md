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

## Post-conditions
- [ ] All tests pass
- [ ] >= 80% code coverage for BenchmarkRunner.java
- [ ] E2E: Run full test suite and confirm all tests pass
